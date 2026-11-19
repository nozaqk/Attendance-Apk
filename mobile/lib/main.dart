import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'config.dart';
import 'core/tokens.dart';
import 'platform/native.dart';
import 'state/session.dart';
import 'ui/auth/auth_screens.dart';
import 'ui/lecturer/lecturer_shell.dart';
import 'ui/role_picker.dart';
import 'ui/student/student_shell.dart';
import 'state/alerts.dart';
import 'ui/theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  await NativeBridge.ensureChannels();
  await NativeBridge.requestNotificationPermission();
  if (AppConfig.placeholderBase) {
    debugPrint(
      'API_BASE_URL still points at the placeholder domain (${AppConfig.apiBase}). '
      'Rebuild with --dart-define=API_BASE_URL=https://<your-real-domain>.',
    );
  }
  runApp(SmartIqApp(prefs: prefs));
}

class SmartIqApp extends StatelessWidget {
  const SmartIqApp({super.key, required this.prefs});

  final SharedPreferences prefs;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SmartIQ',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      home: RootScreen(prefs: prefs),
    );
  }
}

/// Role picker -> portal login -> portal shell. Lecturer and student keep
/// separate tokens and can both be signed in on the same phone.
class RootScreen extends StatefulWidget {
  const RootScreen({super.key, required this.prefs});

  final SharedPreferences prefs;

  @override
  State<RootScreen> createState() => _RootScreenState();
}

class _RootScreenState extends State<RootScreen> {
  Role? _role;

  /// Bumped when the app is opened from a class alert, so the student shell can
  /// jump to the notifications feed.
  final ValueNotifier<int> _openFeed = ValueNotifier<int>(0);

  @override
  void initState() {
    super.initState();
    NativeBridge.setForegroundTapHandler(() => _openFeed.value++);
    NativeBridge.consumeOpenNotifications().then((bool opened) {
      if (opened) _openFeed.value++;
    });
  }

  @override
  void dispose() {
    _openFeed.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    switch (_role) {
      case null:
        return RolePicker(onSelect: (Role role) => setState(() => _role = role));
      case Role.lecturer:
        return LecturerGate(
          store: TokenStore(widget.prefs, TokenStore.lecturerKey),
          onChooseRole: () => setState(() => _role = null),
        );
      case Role.student:
        return StudentGate(
          store: TokenStore(widget.prefs, TokenStore.studentKey),
          openFeed: _openFeed,
          onChooseRole: () => setState(() => _role = null),
        );
    }
  }
}

/// Shared gate: restores the session, shows the right login/forced-password
/// screen and finally the portal shell.
class LecturerGate extends StatefulWidget {
  const LecturerGate({super.key, required this.store, required this.onChooseRole});

  final TokenStore store;
  final VoidCallback onChooseRole;

  @override
  State<LecturerGate> createState() => _LecturerGateState();
}

class _LecturerGateState extends State<LecturerGate> {
  late final SessionController _session = SessionController(role: 'LECTURER', store: widget.store);
  bool _forgot = false;

  @override
  void initState() {
    super.initState();
    _session.addListener(_onChanged);
    _session.bootstrap();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _session.removeListener(_onChanged);
    _session.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_session.loading) return const _Splash();
    final user = _session.user;
    if (user == null) {
      if (_forgot) {
        return ForgotPasswordScreen(
          api: _session.api,
          onBackToSignIn: () => setState(() => _forgot = false),
        );
      }
      return LecturerLoginScreen(
        api: _session.api,
        onSignedIn: _session.signIn,
        onForgot: () => setState(() => _forgot = true),
        onBack: widget.onChooseRole,
      );
    }
    if (user.mustChangePassword) {
      return ForcePasswordChangeScreen(
        api: _session.api,
        user: user,
        onDone: _session.refreshUser,
        onSignOut: () => _session.signOut(),
      );
    }
    return LecturerShell(
      session: _session,
      onSignOut: () => _session.signOut(),
      onRefreshUser: _session.refreshUser,
    );
  }
}

class StudentGate extends StatefulWidget {
  const StudentGate({
    super.key,
    required this.store,
    required this.openFeed,
    required this.onChooseRole,
  });

  final TokenStore store;
  final ValueNotifier<int> openFeed;
  final VoidCallback onChooseRole;

  @override
  State<StudentGate> createState() => _StudentGateState();
}

class _StudentGateState extends State<StudentGate> {
  late final SessionController _session = SessionController(role: 'STUDENT', store: widget.store);
  bool _forgot = false;

  @override
  void initState() {
    super.initState();
    _session.revokeAlerts = BackgroundAlerts.disable;
    _session.addListener(_onChanged);
    _session.bootstrap();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _session.removeListener(_onChanged);
    _session.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_session.loading) return const _Splash();
    final user = _session.user;
    if (user == null) {
      if (_forgot) {
        return ForgotPasswordScreen(
          api: _session.api,
          onBackToSignIn: () => setState(() => _forgot = false),
        );
      }
      return StudentAuthScreen(
        api: _session.api,
        onSignedIn: _session.signIn,
        onForgot: () => setState(() => _forgot = true),
        onBack: widget.onChooseRole,
      );
    }
    if (user.mustChangePassword) {
      return ForcePasswordChangeScreen(
        api: _session.api,
        user: user,
        onDone: _session.refreshUser,
        onSignOut: () => _session.signOut(revokeDeviceToken: true),
      );
    }
    return StudentShell(
      session: _session,
      user: user,
      openFeed: widget.openFeed,
      onSignOut: () => _session.signOut(revokeDeviceToken: true),
      onRefreshUser: _session.refreshUser,
    );
  }
}

class _Splash extends StatelessWidget {
  const _Splash();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: AppColors.appBg,
      body: Center(
        child: SizedBox(
          width: 28,
          height: 28,
          child: CircularProgressIndicator(strokeWidth: 2.5, color: AppColors.navy),
        ),
      ),
    );
  }
}
