import 'package:flutter/material.dart';

import '../../core/api_client.dart';
import '../../core/models.dart';
import '../../core/polling.dart';
import '../theme.dart';
import '../widgets.dart';

/// The courses assigned to the signed-in lecturer and who takes them.
class LecturerCoursesScreen extends StatelessWidget {
  const LecturerCoursesScreen({super.key, required this.api});

  final ApiClient api;

  @override
  Widget build(BuildContext context) {
    return ScreenBody(
      children: <Widget>[
        const ScreenTitle('My courses'),
        Poller<List<LecturerCourse>>(
          fetch: () async {
            final res = await api.get('/api/lecturer/courses');
            return LecturerCourse.listFrom(res['items']);
          },
          builder: (BuildContext context, List<LecturerCourse> items, Future<void> Function() reload) {
            if (items.isEmpty) {
              return const AppCard(
                child: EmptyBox(
                  'No courses assigned',
                  hint: 'The administrator assigns courses to you.',
                ),
              );
            }
            return Column(
              children: <Widget>[
                for (final LecturerCourse course in items)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 14),
                    child: AppCard(
                      title: '${course.code} · ${course.name}',
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          if (course.description != null)
                            Text(
                              course.description!,
                              style: const TextStyle(color: AppColors.muted, fontSize: 13),
                            ),
                          const Text(
                            'Taken by',
                            style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13),
                          ),
                          if (course.eligibility.isEmpty)
                            const Text(
                              'No department/semester set yet.',
                              style: TextStyle(color: AppColors.muted, fontSize: 13),
                            )
                          else
                            Wrap(
                              spacing: 6,
                              runSpacing: 6,
                              children: <Widget>[
                                for (final Eligibility e in course.eligibility)
                                  Badge(e.label, tone: Tone.blue),
                              ],
                            ),
                        ],
                      ),
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

/// Lecturer settings: profile summary and sign-out.
class LecturerSettingScreen extends StatelessWidget {
  const LecturerSettingScreen({
    super.key,
    required this.user,
    required this.onSignOut,
  });

  final User user;
  final VoidCallback onSignOut;

  @override
  Widget build(BuildContext context) {
    return ScreenBody(
      children: <Widget>[
        const ScreenTitle('Setting'),
        AppCard(
          title: 'Profile',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(
                user.fullName,
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              Text(
                user.email,
                style: const TextStyle(color: AppColors.muted, fontSize: 13),
              ),
              Text(
                'Staff ID: ${user.staffId ?? '—'}',
                style: const TextStyle(color: AppColors.muted, fontSize: 13),
              ),
              if (user.departmentName != null)
                Text(
                  user.departmentName!,
                  style: const TextStyle(color: AppColors.muted, fontSize: 13),
                ),
            ],
          ),
        ),
        DangerButton(
          label: 'Log out',
          onPressed: () async {
            final ok = await confirmDialog(
              context,
              title: 'Log out?',
              message: 'You will need to sign in again.',
              confirmLabel: 'Log out',
            );
            if (ok) onSignOut();
          },
        ),
      ],
    );
  }
}
