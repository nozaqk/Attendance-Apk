import 'package:flutter/material.dart';

import '../../ble/ble_controller.dart';
import '../../core/models.dart';
import '../../state/alerts.dart';
import '../../state/session.dart';
import '../theme.dart';
import '../widgets.dart';
import 'student_class.dart';
import 'student_courses.dart';
import 'student_home.dart';
import 'student_notifications.dart';
import 'student_settings.dart';

/// Student portal: Home, Class, Course, Setting.
///
/// Also owns the two side effects that make class alerts work: registering the
/// restricted device token (closed-app poller) and the foreground fallback.
class StudentShell extends StatefulWidget {
  const StudentShell({
    super.key,
    required this.session,
    required this.user,
    required this.openFeed,
    required this.onSignOut,
    required this.onRefreshUser,
  });

  final SessionController session;
  final User user;
  final ValueNotifier<int> openFeed;
  final VoidCallback onSignOut;
  final Future<void> Function() onRefreshUser;

  @override
  State<StudentShell> createState() => _StudentShellState();
}

class _StudentShellState extends State<StudentShell> {
  static const List<AppTab> _tabs = <AppTab>[
    AppTab('🏠', 'Home'),
    AppTab('📡', 'Class'),
    AppTab('📚', 'Course'),
    AppTab('⚙️', 'Setting'),
  ];

  int _index = 0;
  bool _showNotifications = false;
  late final BleController _scanner = BleController(widget.session.api);
  late final ForegroundAlerter _alerter = ForegroundAlerter(widget.session.api);

  @override
  void initState() {
    super.initState();
    widget.openFeed.addListener(_onOpenFeed);
    if (widget.openFeed.value > 0) _showNotifications = true;

    // Foreground fallback (only alerts when the closed-app poller is not running).
    _alerter.start();

    // Register the restricted device token and start the closed-app poller.
    BackgroundAlerts.enable(widget.session.api).catchError((Object _) {});
  }

  void _onOpenFeed() {
    if (!mounted) return;
    setState(() => _showNotifications = true);
  }

  @override
  void dispose() {
    widget.openFeed.removeListener(_onOpenFeed);
    _alerter.stop();
    _scanner.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final user = widget.session.user ?? widget.user;

    // First-time setup: force the Settings form before anything else.
    if (!user.setupComplete) {
      return Scaffold(
        backgroundColor: AppColors.appBg,
        body: SafeArea(
          child: StudentSettingsScreen(
            api: widget.session.api,
            onSaved: widget.onRefreshUser,
            onSignOut: widget.onSignOut,
            firstTime: true,
          ),
        ),
      );
    }

    return Scaffold(
      backgroundColor: AppColors.appBg,
      body: SafeArea(
        bottom: false,
        child: _showNotifications
            ? StudentNotificationsScreen(
                api: widget.session.api,
                onBack: () => setState(() => _showNotifications = false),
              )
            : _body(),
      ),
      bottomNavigationBar: AppBottomBar(
        items: _tabs,
        index: _index,
        onSelect: (int i) => setState(() {
          _index = i;
          _showNotifications = false;
        }),
      ),
    );
  }

  Widget _body() {
    switch (_index) {
      case 0:
        return StudentHomeScreen(
          api: widget.session.api,
          liveScanner: _scanner,
          onOpenNotifications: () => setState(() => _showNotifications = true),
          onOpenClass: () => setState(() => _index = 1),
        );
      case 1:
        return StudentClassScreen(scanner: _scanner, api: widget.session.api, user: widget.user);
      case 2:
        return StudentCoursesScreen(api: widget.session.api);
      default:
        return StudentSettingsScreen(
          api: widget.session.api,
          onSaved: widget.onRefreshUser,
          onSignOut: widget.onSignOut,
          firstTime: false,
        );
    }
  }
}
