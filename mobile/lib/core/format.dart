import 'package:intl/intl.dart';

import 'models.dart';

/// Date/time and label helpers, matching what the Kotlin screens rendered.

DateTime? parseIso(String? iso) {
  if (iso == null || iso.trim().isEmpty) return null;
  final parsed = DateTime.tryParse(iso.trim());
  return parsed?.toLocal();
}

/// `09:30 AM`
String fmtTime(String? iso) {
  final date = parseIso(iso);
  if (date == null) return '—';
  return DateFormat('hh:mm a').format(date);
}

/// `2026-11-11 09:30`
String fmtStamp(String? iso) {
  final date = parseIso(iso);
  if (date == null) return '—';
  return DateFormat('yyyy-MM-dd HH:mm').format(date);
}

/// `2026 Nov 11, 09:30 AM`
String fmtDateTime(String? iso) {
  final date = parseIso(iso);
  if (date == null) return '—';
  return DateFormat('yyyy MMM dd, hh:mm a').format(date);
}

/// `yyyy-MM-dd` used by the activity export and by dashboard headers.
String fmtDate(DateTime date) => DateFormat('yyyy-MM-dd').format(date);

const Map<String, String> _streamLabels = <String, String>{
  'REGULAR': 'Regular',
  'WEEKLY': 'Weekly',
  'EVENING': 'Evening',
};

String streamLabel(String? stream) {
  if (stream == null || stream.isEmpty) return '—';
  return _streamLabels[stream] ?? stream;
}

bool isPresent(String status) => status != 'ABSENT';

/// `09:30 AM – 11:00 AM` (end omitted while the class is running).
String sessionTimeRange(StudentSession session) {
  final start = fmtTime(session.startedAt ?? session.openedAt);
  final end = session.endedAt;
  return end == null ? start : '$start – ${fmtTime(end)}';
}

String initialsOf(String name) {
  final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  if (parts.isEmpty) return '?';
  if (parts.length == 1) return parts.first.substring(0, 1).toUpperCase();
  final first = parts[0].substring(0, 1);
  final second = parts[1].substring(0, 1);
  return '$first$second'.toUpperCase();
}
