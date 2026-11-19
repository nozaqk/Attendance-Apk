import 'package:flutter/material.dart';

import '../../ble/ble_controller.dart';
import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/polling.dart';
import '../theme.dart';
import '../widgets.dart';

/// Student home: today/this-week switch, live class banner, unread alerts,
/// overall totals and the class list.
class StudentHomeScreen extends StatefulWidget {
  const StudentHomeScreen({
    super.key,
    required this.api,
    required this.onOpenNotifications,
    required this.onOpenClass,
    this.liveScanner,
  });

  final ApiClient api;
  final VoidCallback onOpenNotifications;
  final VoidCallback onOpenClass;
  final BleController? liveScanner;

  @override
  State<StudentHomeScreen> createState() => _StudentHomeScreenState();
}

class _StudentHomeScreenState extends State<StudentHomeScreen> {
  String _period = 'day';

  @override
  Widget build(BuildContext context) {
    return ScreenBody(
      children: <Widget>[
        _PeriodSwitch(
          period: _period,
          onChanged: (String value) => setState(() => _period = value),
        ),
        KeyedSubtree(
          key: ValueKey<String>(_period),
          child: Poller<Dashboard>(
            interval: const Duration(seconds: 20),
            fetch: () async {
              final res = await widget.api.get('/api/student/dashboard?period=$_period');
              return Dashboard.fromJson(res);
            },
            builder: (BuildContext context, Dashboard data, Future<void> Function() reload) {
              final live = data.liveClass;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  if (live != null) ...<Widget>[
                    _LiveBanner(
                      session: live,
                      onTap: widget.onOpenClass,
                    ),
                    const SizedBox(height: 8),
                  ],
                  if (data.unread > 0)
                    Align(
                      alignment: Alignment.centerLeft,
                      child: LinkText(
                        '${data.unread} unread notification${data.unread > 1 ? 's' : ''}',
                        onPressed: widget.onOpenNotifications,
                      ),
                    ),
                  Row(
                    children: <Widget>[
                      _StatCell(data.stat('PRESENT'), 'Present', AppColors.green),
                      const SizedBox(width: 8),
                      _StatCell(data.stat('LATE'), 'Late', AppColors.yellow),
                      const SizedBox(width: 8),
                      _StatCell(data.stat('MANUAL'), 'Manual', AppColors.yellow),
                      const SizedBox(width: 8),
                      _StatCell(data.stat('ABSENT'), 'Absent', AppColors.red),
                    ],
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'Overall totals across all your classes.',
                    style: TextStyle(color: AppColors.muted, fontSize: 12),
                  ),
                  const SizedBox(height: 14),
                  AppCard(
                    title: _period == 'day'
                        ? "Today's classes"
                        : 'Classes ${data.from} to ${data.to}',
                    child: data.items.isEmpty
                        ? EmptyBox(
                            _period == 'day' ? 'No classes today yet' : 'No classes this week',
                            hint: 'Classes appear here once your lecturer starts them.',
                          )
                        : Column(
                            children: <Widget>[
                              for (int i = 0; i < data.items.length; i++) ...<Widget>[
                                if (i > 0) const DividerLine(),
                                _SessionRow(session: data.items[i], showDate: _period == 'week'),
                              ],
                            ],
                          ),
                  ),
                ],
              );
            },
          ),
        ),
      ],
    );
  }
}

class _PeriodSwitch extends StatelessWidget {
  const _PeriodSwitch({required this.period, required this.onChanged});

  final String period;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    Widget cell(String key, String label) {
      final selected = period == key;
      return Expanded(
        child: InkWell(
          onTap: () => onChanged(key),
          child: Container(
            padding: const EdgeInsets.symmetric(vertical: 10),
            color: selected ? AppColors.navy : AppColors.cardWhite,
            alignment: Alignment.center,
            child: Text(
              label,
              style: TextStyle(
                color: selected ? Colors.white : AppColors.muted,
                fontWeight: FontWeight.w600,
                fontSize: 14,
              ),
            ),
          ),
        ),
      );
    }

    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.line),
        color: AppColors.cardWhite,
      ),
      clipBehavior: Clip.antiAlias,
      child: Row(
        children: <Widget>[
          cell('day', 'Today'),
          cell('week', 'This week'),
        ],
      ),
    );
  }
}

class _LiveBanner extends StatelessWidget {
  const _LiveBanner({required this.session, required this.onTap});

  final StudentSession session;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.greenBg,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text(
              '${session.courseName} is in session',
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                color: AppColors.green,
                fontSize: 14,
              ),
            ),
            Text(
              '${session.classroomName} · tap Class to verify attendance',
              style: const TextStyle(color: AppColors.green, fontSize: 13),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatCell extends StatelessWidget {
  const _StatCell(this.value, this.label, this.color);

  final int value;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: AppColors.cardWhite,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppColors.line),
        ),
        child: Column(
          children: <Widget>[
            Text(
              '$value',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: color),
            ),
            Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 11)),
          ],
        ),
      ),
    );
  }
}

class _SessionRow extends StatelessWidget {
  const _SessionRow({required this.session, required this.showDate});

  final StudentSession session;
  final bool showDate;

  @override
  Widget build(BuildContext context) {
    final attendance = session.attendance;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          SizedBox(
            width: 62,
            child: Text(
              fmtTime(session.startedAt ?? session.openedAt),
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                color: AppColors.navy,
                fontSize: 13,
              ),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  session.courseName,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
                Text(
                  '${session.courseCode} · ${session.classroomName}'
                  '${showDate ? ' · ${session.sessionDate}' : ''}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 12),
                ),
                Text(
                  '${sessionTimeRange(session)}'
                  '${session.status == 'ACTIVE' ? ' · in session' : ''}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          if (attendance != null)
            Badge(
              attendance.byLecturer && attendance.status == 'PRESENT'
                  ? 'MANUAL'
                  : attendance.status,
            )
          else if (session.status == 'ACTIVE')
            const Badge('PENDING', tone: Tone.yellow)
          else
            const Badge('NOT RECORDED', tone: Tone.grey),
        ],
      ),
    );
  }
}
