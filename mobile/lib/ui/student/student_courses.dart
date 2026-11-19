import 'package:flutter/material.dart';

import '../../core/api_client.dart';
import '../../core/json.dart';
import '../../core/models.dart';
import '../../core/polling.dart';
import '../theme.dart';
import '../widgets.dart';

class _CourseBundle {
  _CourseBundle(this.semester, this.mine, this.available);

  final int? semester;
  final List<StudentCourse> mine;
  final List<StudentCourse> available;
}

/// The student's semester courses plus carry-over courses from other semesters.
class StudentCoursesScreen extends StatefulWidget {
  const StudentCoursesScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<StudentCoursesScreen> createState() => _StudentCoursesScreenState();
}

class _StudentCoursesScreenState extends State<StudentCoursesScreen> {
  bool _showAdd = false;
  String? _busyId;
  String? _error;
  Future<void> Function()? _reload;

  Future<_CourseBundle> _fetch() async {
    final mineRaw = await widget.api.get('/api/student/courses');
    final availableRaw = await widget.api.get('/api/student/courses/available');
    return _CourseBundle(
      optInt(mineRaw['semester']),
      StudentCourse.listFrom(mineRaw['items']),
      StudentCourse.listFrom(availableRaw['items']),
    );
  }

  Future<void> _add(StudentCourse course) async {
    setState(() {
      _busyId = course.id;
      _error = null;
    });
    try {
      await widget.api.post('/api/student/courses/extra', <String, dynamic>{'courseId': course.id});
      await _reload?.call();
    } catch (e) {
      if (mounted) setState(() => _error = errorText(e));
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
  }

  Future<void> _remove(StudentCourse course) async {
    final ok = await confirmDialog(
      context,
      title: 'Remove course',
      message: 'Remove ${course.name} (${course.code}) from your courses? '
          'You will no longer be marked for its classes.',
      confirmLabel: 'Remove',
      danger: true,
    );
    if (!ok) return;
    try {
      await widget.api.delete('/api/student/courses/extra/${course.id}');
      await _reload?.call();
    } catch (_) {
      // the refreshed list shows the real state
    }
  }

  @override
  Widget build(BuildContext context) {
    return ScreenBody(
      children: <Widget>[
        Poller<_CourseBundle>(
          fetch: _fetch,
          builder: (BuildContext context, _CourseBundle bundle, Future<void> Function() reload) {
            _reload = reload;
            final groups = <int, List<StudentCourse>>{};
            for (final StudentCourse c in bundle.available) {
              groups.putIfAbsent(c.semester ?? 0, () => <StudentCourse>[]).add(c);
            }
            final semesters = groups.keys.toList()..sort();
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                AppCard(
                  title: bundle.semester == null
                      ? 'My courses'
                      : 'Semester ${bundle.semester} courses',
                  child: bundle.mine.isEmpty
                      ? const EmptyBox(
                          'No courses yet',
                          hint: 'Courses for your department and semester appear here once the '
                              'administrator sets them up.',
                        )
                      : Column(
                          children: <Widget>[
                            for (int i = 0; i < bundle.mine.length; i++) ...<Widget>[
                              if (i > 0) const DividerLine(),
                              _MyCourseRow(
                                course: bundle.mine[i],
                                onRemove: () => _remove(bundle.mine[i]),
                              ),
                            ],
                          ],
                        ),
                ),
                const SizedBox(height: 14),
                AppCard(
                  title: 'Add courses from other semesters',
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: <Widget>[
                      if (!_showAdd) ...<Widget>[
                        const Text(
                          'Taking a course from another semester (for example a carry-over)? '
                          'Add it here so you can be marked for its classes.',
                          style: TextStyle(color: AppColors.muted, fontSize: 13, height: 1.4),
                        ),
                        const SizedBox(height: 10),
                        SecondaryButton(
                          label: 'Browse',
                          onPressed: () => setState(() => _showAdd = true),
                        ),
                      ] else ...<Widget>[
                        SecondaryButton(
                          label: 'Hide',
                          onPressed: () => setState(() => _showAdd = false),
                        ),
                        if (_error != null) ...<Widget>[
                          const SizedBox(height: 10),
                          NoticeBox(_error!, tone: Tone.red),
                        ],
                        if (semesters.isEmpty)
                          const Padding(
                            padding: EdgeInsets.only(top: 10),
                            child: EmptyBox(
                              'Nothing to add',
                              hint: 'There are no other courses in your department.',
                            ),
                          )
                        else
                          for (final int semester in semesters) ...<Widget>[
                            Padding(
                              padding: const EdgeInsets.only(top: 14, bottom: 6),
                              child: Text(
                                'SEMESTER ${semester == 0 ? '?' : semester}',
                                style: const TextStyle(
                                  color: AppColors.muted,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 12,
                                  letterSpacing: 0.4,
                                ),
                              ),
                            ),
                            for (final StudentCourse course in groups[semester]!)
                              Padding(
                                padding: const EdgeInsets.only(bottom: 8),
                                child: Row(
                                  children: <Widget>[
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: <Widget>[
                                          Text(
                                            course.name,
                                            style: const TextStyle(
                                              fontWeight: FontWeight.bold,
                                              fontSize: 14,
                                            ),
                                          ),
                                          Text(
                                            '${course.code}'
                                            '${course.lecturer == null ? '' : ' · ${course.lecturer}'}',
                                            style: const TextStyle(
                                              color: AppColors.muted,
                                              fontSize: 12,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                    SizedBox(
                                      width: 96,
                                      child: SecondaryButton(
                                        label: 'Add',
                                        busy: _busyId == course.id,
                                        onPressed: () => _add(course),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                          ],
                      ],
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

class _MyCourseRow extends StatelessWidget {
  const _MyCourseRow({required this.course, required this.onRemove});

  final StudentCourse course;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  course.name,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                ),
                Text(
                  '${course.code}${course.lecturer == null ? ' · No lecturer assigned' : ' · ${course.lecturer}'}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              ],
            ),
          ),
          if (course.isExtra)
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: <Widget>[
                const Badge('Added', tone: Tone.blue),
                LinkText('Remove', onPressed: onRemove),
              ],
            ),
        ],
      ),
    );
  }
}
