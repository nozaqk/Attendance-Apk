import 'package:flutter/material.dart';

import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/polling.dart';
import '../theme.dart';
import '../widgets.dart';

/// The notifications feed, opened from Home or from a class alert.
class StudentNotificationsScreen extends StatefulWidget {
  const StudentNotificationsScreen({super.key, required this.api, required this.onBack});

  final ApiClient api;
  final VoidCallback onBack;

  @override
  State<StudentNotificationsScreen> createState() => _StudentNotificationsScreenState();
}

class _StudentNotificationsScreenState extends State<StudentNotificationsScreen> {
  final GlobalKey<PollerState<NotificationsPage>> _key =
      GlobalKey<PollerState<NotificationsPage>>();

  @override
  Widget build(BuildContext context) {
    return ScreenBody(
      children: <Widget>[
        Align(
          alignment: Alignment.centerLeft,
          child: LinkText('← Back', onPressed: widget.onBack),
        ),
        AppCard(
          title: 'Notifications',
          child: Poller<NotificationsPage>(
            key: _key,
            interval: const Duration(seconds: 20),
            fetch: () async {
              final res = await widget.api.get('/api/student/notifications');
              return NotificationsPage.fromJson(res);
            },
            builder: (
              BuildContext context,
              NotificationsPage page,
              Future<void> Function() reload,
            ) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  if (page.unread > 0)
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton(
                        onPressed: () async {
                          await widget.api.post('/api/student/notifications/read');
                          await reload();
                        },
                        child: const Text(
                          'Mark all read',
                          style: TextStyle(color: AppColors.navy, fontWeight: FontWeight.w600),
                        ),
                      ),
                    ),
                  if (page.items.isEmpty)
                    const EmptyBox(
                      'No notifications',
                      hint: 'You will be told when a class starts or attendance is finalised.',
                    )
                  else
                    for (int i = 0; i < page.items.length; i++) ...<Widget>[
                      if (i > 0) const DividerLine(),
                      _NotifRow(item: page.items[i]),
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

class _NotifRow extends StatelessWidget {
  const _NotifRow({required this.item});

  final NotifItem item;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          if (!item.read)
            Container(
              margin: const EdgeInsets.only(top: 6, right: 8),
              width: 8,
              height: 8,
              decoration: const BoxDecoration(
                color: AppColors.navy,
                shape: BoxShape.circle,
              ),
            ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  item.title,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
                const SizedBox(height: 2),
                Text(item.body, style: const TextStyle(fontSize: 13, height: 1.35)),
                const SizedBox(height: 2),
                Text(
                  fmtDateTime(item.createdAt),
                  style: const TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
