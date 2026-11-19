import 'package:flutter/material.dart';

import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/polling.dart';
import '../theme.dart';
import '../widgets.dart';
import 'lecturer_common.dart';

/// Class results: every session this lecturer started, newest first.
class LecturerHomeScreen extends StatelessWidget {
  const LecturerHomeScreen({super.key, required this.api, required this.onOpen});

  final ApiClient api;
  final ValueChanged<String> onOpen;

  @override
  Widget build(BuildContext context) {
    return ScreenBody(
      children: <Widget>[
        const ScreenTitle('Class results'),
        AppCard(
          child: Poller<List<LecturerSession>>(
            interval: const Duration(seconds: 15),
            fetch: () async {
              final res = await api.get('/api/lecturer/sessions');
              return LecturerSession.listFrom(res['items']);
            },
            builder: (BuildContext context, List<LecturerSession> items, Future<void> Function() reload) {
              if (items.isEmpty) {
                return const EmptyBox(
                  'No classes yet',
                  hint: 'Classes you start from the Class tab will appear here with their results.',
                );
              }
              return Column(
                children: <Widget>[
                  for (int i = 0; i < items.length; i++) ...<Widget>[
                    if (i > 0) const DividerLine(),
                    _SessionRow(session: items[i], onTap: () => onOpen(items[i].id)),
                  ],
                ],
              );
            },
          ),
        ),
      ],
    );
  }
}

class _SessionRow extends StatelessWidget {
  const _SessionRow({required this.session, required this.onTap});

  final LecturerSession session;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final active = session.status == 'ACTIVE';
    final label = active
        ? 'IN PROGRESS'
        : session.locked
            ? 'LOCKED'
            : 'ENDED';
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                Expanded(
                  child: Text(
                    session.courseCode,
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                  ),
                ),
                StatusPill(label, tone: active ? Tone.green : Tone.grey),
              ],
            ),
            const SizedBox(height: 2),
            Text(session.courseName, style: const TextStyle(fontSize: 14)),
            Text(
              '${session.sessionDate} · ${session.classroomName}',
              style: const TextStyle(color: AppColors.muted, fontSize: 13),
            ),
            const SizedBox(height: 6),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: <Widget>[
                StatusPill('Present ${session.presentCount}', tone: Tone.green),
                StatusPill('Absent ${session.absentCount}', tone: Tone.red),
                if (!session.locked && session.editableUntil != null)
                  StatusPill('Edit until ${fmtStamp(session.editableUntil)}', tone: Tone.yellow),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// One session's result: summary, roster and the manual-correction window.
class LecturerResultScreen extends StatefulWidget {
  const LecturerResultScreen({
    super.key,
    required this.api,
    required this.sessionId,
    required this.onBack,
  });

  final ApiClient api;
  final String sessionId;
  final VoidCallback onBack;

  @override
  State<LecturerResultScreen> createState() => _LecturerResultScreenState();
}

class _LecturerResultScreenState extends State<LecturerResultScreen> {
  Future<void> _openManual(String sessionId, Future<void> Function() reload) async {
    await showDialog<void>(
      context: context,
      builder: (BuildContext ctx) =>
          ManualAddDialog(api: widget.api, sessionId: sessionId, onDone: reload),
    );
    await reload();
  }

  @override
  Widget build(BuildContext context) {
    return ScreenBody(
      children: <Widget>[
        Align(
          alignment: Alignment.centerLeft,
          child: LinkText('← Class results', onPressed: widget.onBack),
        ),
        Poller<ResultData>(
          interval: const Duration(seconds: 10),
          fetch: () async {
            final res = await widget.api.get('/api/lecturer/sessions/${widget.sessionId}/attendance');
            return ResultData.fromJson(res);
          },
          builder: (BuildContext context, ResultData data, Future<void> Function() reload) {
            final present = data.total - data.absent;
            final canAdd = (data.status == 'ENDED' && !data.locked) || data.status == 'ACTIVE';
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                AppCard(
                  title: '${data.courseCode} · ${data.courseName}',
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        '${data.sessionDate} · ${data.classroomName}',
                        style: const TextStyle(color: AppColors.muted, fontSize: 13),
                      ),
                      const SizedBox(height: 6),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: StatusPill(
                          data.status,
                          tone: data.status == 'ACTIVE' ? Tone.green : Tone.grey,
                        ),
                      ),
                      const SizedBox(height: 10),
                      CountsHead(total: data.total, present: present, absent: data.absent),
                      const SizedBox(height: 8),
                      Text(
                        'Bluetooth verified ${data.verified + data.present + data.late} · Manual ${data.manual}',
                        style: const TextStyle(color: AppColors.muted, fontSize: 13),
                      ),
                    ],
                  ),
                ),
                if (data.status == 'ENDED')
                  EditWindow(locked: data.locked, editableUntil: data.editableUntil),
                if (data.status == 'ACTIVE')
                  const Text(
                    'This class is still in progress. Go to the Class tab to manage it.',
                    style: TextStyle(color: AppColors.muted, fontSize: 13),
                  ),
                if (canAdd)
                  SecondaryButton(
                    label: '+ Add student manually',
                    onPressed: () => _openManual(widget.sessionId, reload),
                  ),
                AppCard(
                  title: 'Roster',
                  child: data.items.isEmpty
                      ? const Text(
                          'No students on this class.',
                          style: TextStyle(color: AppColors.muted, fontSize: 13),
                        )
                      : Column(
                          children: <Widget>[
                            for (final RosterItem item in data.items)
                              RosterRow(item: item, live: true),
                          ],
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
