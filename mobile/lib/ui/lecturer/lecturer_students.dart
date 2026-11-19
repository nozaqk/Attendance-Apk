import 'package:flutter/material.dart';

import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/polling.dart';
import '../theme.dart';
import '../widgets.dart';

/// Every student who can be marked on this lecturer's courses, searchable and
/// filterable per course.
class LecturerStudentsScreen extends StatefulWidget {
  const LecturerStudentsScreen({super.key, required this.api});

  final ApiClient api;

  @override
  State<LecturerStudentsScreen> createState() => _LecturerStudentsScreenState();
}

class _LecturerStudentsScreenState extends State<LecturerStudentsScreen> {
  String _courseId = '';
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final needle = _query.trim().toLowerCase();
    return ScreenBody(
      children: <Widget>[
        const ScreenTitle('Students'),
        AuthField(
          value: _query,
          onChanged: (v) => setState(() => _query = v),
          label: 'Search name or index number',
        ),
        Poller<List<Course>>(
          fetch: () async {
            final res = await widget.api.get('/api/lecturer/courses');
            return Course.listFrom(res['items']);
          },
          builder: (BuildContext context, List<Course> courses, Future<void> Function() reload) {
            String label = 'All my courses';
            for (final Course c in courses) {
              if (c.id == _courseId) label = c.label;
            }
            return PickerField(
              label: 'Course',
              value: label,
              onTap: () async {
                final chosen = await showPicker<String>(
                  context: context,
                  title: 'Filter by course',
                  options: <MapEntry<String, String>>[
                    const MapEntry<String, String>('', 'All my courses'),
                    ...courses.map((c) => MapEntry<String, String>(c.id, c.label)),
                  ],
                );
                if (chosen != null && mounted) setState(() => _courseId = chosen);
              },
            );
          },
        ),
        KeyedSubtree(
          key: ValueKey<String>(_courseId),
          child: Poller<List<StudentRow>>(
            fetch: () async {
              final path = _courseId.isEmpty
                  ? '/api/lecturer/students'
                  : '/api/lecturer/students?courseId=${Uri.encodeQueryComponent(_courseId)}';
              final res = await widget.api.get(path);
              return StudentRow.listFrom(res['items']);
            },
            builder: (BuildContext context, List<StudentRow> rows, Future<void> Function() reload) {
              final filtered = rows.where((StudentRow s) {
                if (needle.isEmpty) return true;
                return s.fullName.toLowerCase().contains(needle) ||
                    s.studentId.toLowerCase().contains(needle);
              }).toList();
              return AppCard(
                title: '${filtered.length} student${filtered.length == 1 ? '' : 's'}',
                child: filtered.isEmpty
                    ? EmptyBox(
                        'No students found',
                        hint: _query.isNotEmpty
                            ? 'Try a different search.'
                            : 'Students appear here once they pick a department and semester matching your courses.',
                      )
                    : Column(
                        children: <Widget>[
                          for (int i = 0; i < filtered.length; i++) ...<Widget>[
                            if (i > 0) const DividerLine(),
                            _StudentRowView(student: filtered[i]),
                          ],
                        ],
                      ),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _StudentRowView extends StatelessWidget {
  const _StudentRowView({required this.student});

  final StudentRow student;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Expanded(
                child: Text(
                  student.fullName,
                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                ),
              ),
              Badge(streamLabel(student.stream), tone: Tone.blue),
            ],
          ),
          Text(
            student.studentId,
            style: const TextStyle(color: AppColors.muted, fontSize: 12),
          ),
          Text(
            '${student.department ?? 'No department'}'
            '${student.semester == null ? '' : ' · Semester ${student.semester}'}',
            style: const TextStyle(color: AppColors.muted, fontSize: 12),
          ),
          if (student.courses.isNotEmpty)
            Text(
              student.courses.join(', '),
              style: const TextStyle(color: AppColors.muted, fontSize: 12),
            ),
        ],
      ),
    );
  }
}
