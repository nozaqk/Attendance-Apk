import 'package:flutter/material.dart';

import '../../ble/ble_controller.dart';
import '../../core/models.dart';
import '../../state/session.dart';
import '../theme.dart';
import '../widgets.dart';
import 'lecturer_class.dart';
import 'lecturer_courses.dart';
import 'lecturer_home.dart';
import 'lecturer_students.dart';

/// Lecturer portal: Home, Students, Courses, Class, Setting.
class LecturerShell extends StatefulWidget {
  const LecturerShell({
    super.key,
    required this.session,
    required this.onSignOut,
    required this.onRefreshUser,
  });

  final SessionController session;
  final VoidCallback onSignOut;
  final Future<void> Function() onRefreshUser;

  @override
  State<LecturerShell> createState() => _LecturerShellState();
}

class _LecturerShellState extends State<LecturerShell> {
  static const List<AppTab> _tabs = <AppTab>[
    AppTab('🏠', 'Home'),
    AppTab('👥', 'Students'),
    AppTab('📚', 'Courses'),
    AppTab('📡', 'Class'),
    AppTab('⚙️', 'Setting'),
  ];

  int _index = 0;
  String? _resultId;
  late final BleController _scanner = BleController(widget.session.api);

  @override
  void dispose() {
    _scanner.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final user = widget.session.user;
    return Scaffold(
      backgroundColor: AppColors.appBg,
      body: SafeArea(
        bottom: false,
        child: _resultId != null
            ? LecturerResultScreen(
                api: widget.session.api,
                sessionId: _resultId!,
                onBack: () => setState(() => _resultId = null),
              )
            : _body(user),
      ),
      bottomNavigationBar: AppBottomBar(
        items: _tabs,
        index: _index,
        onSelect: (int i) => setState(() {
          _index = i;
          _resultId = null;
        }),
      ),
    );
  }

  Widget _body(User user) {
    switch (_index) {
      case 0:
        return LecturerHomeScreen(
          api: widget.session.api,
          onOpen: (String id) => setState(() => _resultId = id),
        );
      case 1:
        return LecturerStudentsScreen(api: widget.session.api);
      case 2:
        return LecturerCoursesScreen(api: widget.session.api);
      case 3:
        return LecturerClassScreen(api: widget.session.api, scanner: _scanner);
      default:
        return LecturerSettingScreen(user: user, onSignOut: widget.onSignOut);
    }
  }
}
