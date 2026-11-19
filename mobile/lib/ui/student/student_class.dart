import 'package:flutter/material.dart';

import '../../ble/ble_controller.dart';
import '../../ble/ble_verify_panel.dart';
import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/polling.dart';
import '../../platform/native.dart';
import '../theme.dart';
import '../widgets.dart';

/// Live classes the student can check in to, plus the activity export.
class StudentClassScreen extends StatefulWidget {
  const StudentClassScreen({
    super.key,
    required this.api,
    required this.scanner,
    required this.user,
  });

  final ApiClient api;
  final BleController scanner;
  final User user;

  @override
  State<StudentClassScreen> createState() => _StudentClassScreenState();
}

class _StudentClassScreenState extends State<StudentClassScreen> {
  Future<void> _openExport() async {
    await showDialog<void>(
      context: context,
      builder: (BuildContext ctx) => _ExportDialog(api: widget.api, user: widget.user),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ScreenBody(
      children: <Widget>[
        SecondaryButton(label: 'Export my activity', onPressed: _openExport),
        Poller<List<StudentSession>>(
          interval: const Duration(seconds: 10),
          fetch: () async {
            final res = await widget.api.get('/api/student/classes/live');
            return StudentSession.listFrom(res['items']);
          },
          builder: (BuildContext context, List<StudentSession> items, Future<void> Function() reload) {
            if (items.isEmpty) {
              return AppCard(
                child: const EmptyBox(
                  'No class in session right now',
                  hint: 'When your lecturer starts a class for one of your courses it will '
                      'show up here and you can verify your attendance.',
                ),
              );
            }
            return Column(
              children: <Widget>[
                for (final StudentSession session in items)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 14),
                    child: _LiveCard(
                      api: widget.api,
                      session: session,
                      scanner: widget.scanner,
                    ),
                  ),
              ],
            );
          },
        ),
      ],
    );
  }
}

class _LiveCard extends StatefulWidget {
  const _LiveCard({required this.api, required this.session, required this.scanner});

  final ApiClient api;
  final StudentSession session;
  final BleController scanner;

  @override
  State<_LiveCard> createState() => _LiveCardState();
}

class _LiveCardState extends State<_LiveCard> {
  bool _verified = false;
  Attendance? _verdict;
  bool _listening = false;

  void _listen() {
    if (_listening) return;
    _listening = true;
    widget.scanner.addListener(_onScan);
  }

  void _onScan() {
    final result = widget.scanner.result;
    if (result == null || !mounted) return;
    setState(() {
      if (result.verified) _verified = true;
      if (result.attendance != null) _verdict = result.attendance;
    });
  }

  @override
  void initState() {
    super.initState();
    _listen();
  }

  @override
  void dispose() {
    if (_listening) widget.scanner.removeListener(_onScan);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final session = widget.session;
    final attendance = _verdict ?? session.attendance;
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Row(
            children: <Widget>[
              Expanded(
                child: Text(
                  '${session.courseName} is in session',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                ),
              ),
              const StatusPill('LIVE', tone: Tone.green),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            '${session.courseCode} · ${session.classroomName}'
            '${session.classroomBuilding == null ? '' : ', ${session.classroomBuilding}'}',
            style: const TextStyle(color: AppColors.muted, fontSize: 13),
          ),
          Text(
            'Started ${fmtTime(session.startedAt ?? session.openedAt)}'
            '${session.lecturerName == null ? '' : ' · ${session.lecturerName}'}',
            style: const TextStyle(color: AppColors.muted, fontSize: 13),
          ),
          const SizedBox(height: 10),
          BleVerifyPanel(
            scanner: widget.scanner,
            sessionId: session.id,
            minGapMs: 20000,
            idleLabel: 'Waiting',
            onVerified: () => setState(() => _verified = true),
          ),
          if (attendance != null) ...<Widget>[
            const SizedBox(height: 10),
            if (_verified) const BigStatus('ATTENDANCE VERIFIED ✓'),
            const SizedBox(height: 8),
            Row(
              children: <Widget>[
                StatusPill(attendance.status),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    attendance.byLecturer
                        ? 'Recorded by lecturer'
                        : 'Verified by Bluetooth',
                    style: const TextStyle(color: AppColors.muted, fontSize: 13),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              'Observations: ${attendance.observationCount}'
              ' · First detected ${fmtTime(attendance.firstDetectedAt)}'
              ' · Last detected ${fmtTime(attendance.lastDetectedAt)}',
              style: const TextStyle(color: AppColors.muted, fontSize: 12),
            ),
          ],
        ],
      ),
    );
  }
}

class _ExportDialog extends StatefulWidget {
  const _ExportDialog({required this.api, required this.user});

  final ApiClient api;
  final User user;

  @override
  State<_ExportDialog> createState() => _ExportDialogState();
}

class _ExportDialogState extends State<_ExportDialog> {
  String? _busy;
  String? _error;
  bool _done = false;

  Future<void> _export(String period) async {
    setState(() {
      _busy = period;
      _error = null;
    });
    try {
      final res = await widget.api.get('/api/student/attendance');
      final all = AttendanceRecord.listFrom(res['items']);
      final now = DateTime.now();
      final to = fmtDate(now);
      final from = period == 'week'
          ? fmtDate(now.subtract(const Duration(days: 6)))
          : fmtDate(DateTime(now.year, now.month, 1));

      final rows = all
          .where((AttendanceRecord r) => r.date.compareTo(from) >= 0 && r.date.compareTo(to) <= 0)
          .toList()
        ..sort((AttendanceRecord a, AttendanceRecord b) => b.date.compareTo(a.date));

      final attended = rows.where((AttendanceRecord r) => r.status != 'ABSENT').length;
      final counts = <String, int>{};
      for (final AttendanceRecord row in rows) {
        counts[row.status] = (counts[row.status] ?? 0) + 1;
      }
      final keys = counts.keys.toList()..sort();
      final breakdown =
          keys.map((String k) => '$k: ${counts[k]}').join('   ');

      final summary = rows.isEmpty
          ? 'No class activity in this period.'
          : 'Classes: ${rows.length}   ·   Attended: $attended '
              '(${rows.isEmpty ? 0 : (attended * 100 / rows.length).floor()}%)'
              '${breakdown.isEmpty ? '' : '   ·   $breakdown'}';

      final fileName =
          'my-${period == 'week' ? 'weekly' : 'monthly'}-activity-$to.pdf';

      final saved = await NativeBridge.exportActivityPdf(<String, dynamic>{
        'period': period,
        'label': period == 'week' ? 'Weekly' : 'Monthly',
        'fileName': fileName,
        'studentName': widget.user.fullName,
        'studentId': widget.user.studentId ?? '',
        'from': from,
        'to': to,
        'generated': fmtStamp(now.toIso8601String()),
        'summary': summary,
        'rows': rows
            .map((AttendanceRecord r) => <String, String>{
                  'date': r.date,
                  'course': '${r.courseCode} ${r.courseName}',
                  'room': r.classroom,
                  'status': r.status,
                  'how': r.method == 'LECTURER_MANUAL' ? 'Lecturer' : 'Bluetooth',
                  'first': fmtTime(r.firstDetectedAt),
                  'last': fmtTime(r.lastDetectedAt),
                })
            .toList(),
      });
      if (!mounted) return;
      setState(() {
        _done = saved != null && saved.isNotEmpty;
        if (!_done) _error = 'The file could not be saved.';
      });
    } catch (e) {
      if (mounted) setState(() => _error = errorText(e));
    } finally {
      if (mounted) setState(() => _busy = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text(
        'Export my activity',
        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
      ),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            const Text(
              'Download your class attendance as a document.',
              style: TextStyle(color: AppColors.muted, fontSize: 13),
            ),
            const SizedBox(height: 12),
            PrimaryButton(
              label: 'Last 7 days (weekly)',
              busy: _busy == 'week',
              onPressed: () => _export('week'),
            ),
            const SizedBox(height: 10),
            SecondaryButton(
              label: 'This month (monthly)',
              busy: _busy == 'month',
              onPressed: () => _export('month'),
            ),
            if (_done) ...<Widget>[
              const SizedBox(height: 12),
              const NoticeBox('Saved to your Downloads folder.', tone: Tone.green),
            ],
            if (_error != null) ...<Widget>[
              const SizedBox(height: 12),
              ErrorBox(_error!),
            ],
          ],
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Close', style: TextStyle(color: AppColors.textDark)),
        ),
      ],
    );
  }
}
