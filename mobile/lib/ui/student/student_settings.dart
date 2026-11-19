import 'package:flutter/material.dart';

import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/polling.dart';
import '../theme.dart';
import '../widgets.dart';

/// Profile + academic details. First-time students get the form forced open so
/// Home/Class/Course can be unlocked (the web app redirects for the same reason).
class StudentSettingsScreen extends StatefulWidget {
  const StudentSettingsScreen({
    super.key,
    required this.api,
    required this.onSaved,
    required this.onSignOut,
    required this.firstTime,
  });

  final ApiClient api;
  final Future<void> Function() onSaved;
  final VoidCallback onSignOut;
  final bool firstTime;

  @override
  State<StudentSettingsScreen> createState() => _StudentSettingsScreenState();
}

class _StudentSettingsScreenState extends State<StudentSettingsScreen> {
  Profile? _profile;
  List<Department> _departments = <Department>[];
  bool _loading = true;
  bool _editing = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _editing = widget.firstTime;
    _load();
  }

  Future<void> _load() async {
    if (mounted) setState(() => _loading = true);
    try {
      final profile = Profile.fromJson(await widget.api.get('/api/student/profile'));
      final depts = Department.listFrom(
        (await widget.api.get('/api/student/departments'))['items'],
      );
      if (!mounted) return;
      setState(() {
        _profile = profile;
        _departments = depts;
        _error = null;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = errorText(e);
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const ScreenBody(children: <Widget>[LoadingRow()]);
    }
    final profile = _profile;
    if (profile == null) {
      return ScreenBody(
        children: <Widget>[ErrorBox(_error ?? 'Could not load your profile.', onRetry: _load)],
      );
    }
    final needsSetup = profile.needsSetup;

    if (_editing || needsSetup) {
      return _ProfileForm(
        api: widget.api,
        profile: profile,
        departments: _departments,
        firstTime: needsSetup,
        onSaved: () async {
          await _load();
          await widget.onSaved();
          if (mounted) setState(() => _editing = false);
        },
        onCancel: needsSetup ? null : () => setState(() => _editing = false),
      );
    }

    return ScreenBody(
      children: <Widget>[
        AppCard(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Container(
                width: 52,
                height: 52,
                decoration: const BoxDecoration(color: AppColors.navy, shape: BoxShape.circle),
                alignment: Alignment.center,
                child: const Text('👤', style: TextStyle(fontSize: 24)),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      profile.fullName,
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
                    ),
                    const Text(
                      'Student',
                      style: TextStyle(color: AppColors.muted, fontSize: 13),
                    ),
                    Text(
                      'Index number: ${profile.studentId}',
                      style: const TextStyle(color: AppColors.muted, fontSize: 13),
                    ),
                  ],
                ),
              ),
              LinkText('✎ Edit', onPressed: () => setState(() => _editing = true)),
            ],
          ),
        ),
        AppCard(
          title: 'Academic Details',
          child: Column(
            children: <Widget>[
              InfoRow('🏢', 'Department', profile.department?.name ?? 'Not chosen'),
              InfoRow(
                '📅',
                'Semester',
                profile.semester == null ? 'Not chosen' : 'Semester ${profile.semester}',
              ),
              InfoRow('🌳', 'Stream', streamLabel(profile.stream)),
            ],
          ),
        ),
        AppCard(
          title: 'Personal Information',
          child: Column(
            children: <Widget>[
              InfoRow('👤', 'Name', profile.fullName),
              InfoRow('🪪', 'Index number', profile.studentId),
              InfoRow('✉️', 'Email', profile.email),
            ],
          ),
        ),
        DangerButton(
          label: 'Log out',
          onPressed: () async {
            final ok = await confirmDialog(
              context,
              title: 'Log out',
              message: 'Sign out of this device?',
              confirmLabel: 'Log out',
            );
            if (ok) widget.onSignOut();
          },
        ),
      ],
    );
  }
}

class InfoRow extends StatelessWidget {
  const InfoRow(this.icon, this.label, this.value, {super.key});

  final String icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: <Widget>[
          Text(icon, style: const TextStyle(fontSize: 18)),
          const SizedBox(width: 10),
          Expanded(
            flex: 4,
            child: Text(
              label,
              style: const TextStyle(color: AppColors.muted, fontSize: 13),
            ),
          ),
          Expanded(
            flex: 6,
            child: Text(value, style: const TextStyle(fontSize: 13)),
          ),
        ],
      ),
    );
  }
}

class _ProfileForm extends StatefulWidget {
  const _ProfileForm({
    required this.api,
    required this.profile,
    required this.departments,
    required this.firstTime,
    required this.onSaved,
    this.onCancel,
  });

  final ApiClient api;
  final Profile profile;
  final List<Department> departments;
  final bool firstTime;
  final Future<void> Function() onSaved;
  final VoidCallback? onCancel;

  @override
  State<_ProfileForm> createState() => _ProfileFormState();
}

class _ProfileFormState extends State<_ProfileForm> {
  late String _fullName = widget.profile.fullName;
  late String _departmentId = widget.profile.department?.id ?? '';
  late String _semester = widget.profile.semester?.toString() ?? '';
  late String _stream = widget.profile.stream;
  bool _busy = false;
  String? _error;

  int get _semesterCount {
    for (final Department d in widget.departments) {
      if (d.id == _departmentId) return d.semesterCount;
    }
    return widget.profile.semesterCount;
  }

  String get _departmentName {
    for (final Department d in widget.departments) {
      if (d.id == _departmentId) return d.name;
    }
    return widget.profile.department?.name ?? 'Select department…';
  }

  bool get _departmentChanged =>
      widget.profile.department != null &&
      _departmentId.isNotEmpty &&
      _departmentId != widget.profile.department!.id;

  Future<void> _save() async {
    if (_semester.isEmpty) {
      setState(() => _error = 'Choose your semester');
      return;
    }
    if (_departmentChanged) {
      final ok = await confirmDialog(
        context,
        title: 'Change department?',
        message: 'Moving to $_departmentName will remove all extra courses you added from '
            'other semesters. Your semester courses will be updated to the new department.',
        confirmLabel: 'Change department',
        danger: true,
      );
      if (!ok) return;
    }
    if (!mounted) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final body = <String, dynamic>{'fullName': _fullName, 'stream': _stream};
      if (_departmentId.isNotEmpty) body['departmentId'] = _departmentId;
      body['semester'] = int.tryParse(_semester) ?? _semester;
      await widget.api.put('/api/student/settings', body);
      await widget.onSaved();
    } catch (e) {
      if (mounted) setState(() => _error = errorText(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ScreenBody(
      children: <Widget>[
        if (widget.firstTime)
          const NoticeBox(
            'Welcome! Choose your department and semester, then save. '
            'This unlocks Home, Class and Course.',
          ),
        AppCard(
          title: 'Academic Details',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              PickerField(
                label: 'Department',
                value: _departmentName,
                onTap: () async {
                  final chosen = await showPicker<String>(
                    context: context,
                    title: 'Choose department',
                    options: widget.departments
                        .map((Department d) => MapEntry<String, String>(d.id, d.name))
                        .toList(),
                  );
                  if (chosen != null && mounted) {
                    setState(() {
                      _departmentId = chosen;
                      final count = _semesterCount;
                      final current = int.tryParse(_semester) ?? 0;
                      if (current > count) _semester = '';
                    });
                  }
                },
              ),
              if (_departmentChanged) ...<Widget>[
                const SizedBox(height: 10),
                const NoticeBox('Changing department removes any extra courses you added.'),
              ],
              const SizedBox(height: 14),
              PickerField(
                label: 'Semester',
                value: _semester.isEmpty ? 'Select semester…' : 'Semester $_semester',
                onTap: () async {
                  final options = <MapEntry<String, String>>[
                    for (int i = 1; i <= _semesterCount; i++)
                      MapEntry<String, String>('$i', 'Semester $i'),
                  ];
                  final chosen = await showPicker<String>(
                    context: context,
                    title: 'Choose semester',
                    options: options,
                  );
                  if (chosen != null && mounted) setState(() => _semester = chosen);
                },
              ),
              const SizedBox(height: 14),
              PickerField(
                label: 'Stream',
                value: streamLabel(_stream),
                onTap: () async {
                  final chosen = await showPicker<String>(
                    context: context,
                    title: 'Choose stream',
                    options: const <MapEntry<String, String>>[
                      MapEntry<String, String>('REGULAR', 'Regular'),
                      MapEntry<String, String>('WEEKLY', 'Weekly'),
                      MapEntry<String, String>('EVENING', 'Evening'),
                    ],
                  );
                  if (chosen != null && mounted) setState(() => _stream = chosen);
                },
              ),
            ],
          ),
        ),
        AppCard(
          title: 'Personal Information',
          child: Column(
            children: <Widget>[
              AuthField(
                value: _fullName,
                onChanged: (v) => _fullName = v,
                label: 'Name',
              ),
              const SizedBox(height: 14),
              AuthField(
                value: widget.profile.studentId,
                onChanged: (_) {},
                label: 'Index number',
                enabled: false,
              ),
              const SizedBox(height: 14),
              AuthField(
                value: widget.profile.email,
                onChanged: (_) {},
                label: 'Email',
                enabled: false,
              ),
            ],
          ),
        ),
        if (_error != null) ErrorBox(_error!),
        PrimaryButton(label: 'Save details', busy: _busy, onPressed: _save),
        if (widget.onCancel != null)
          SecondaryButton(label: 'Cancel', onPressed: widget.onCancel),
      ],
    );
  }
}
