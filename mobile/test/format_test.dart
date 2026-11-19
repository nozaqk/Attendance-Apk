import 'package:flutter_test/flutter_test.dart';
import 'package:smartiq/core/format.dart';
import 'package:smartiq/core/models.dart';

void main() {
  test('formats local timestamps', () {
    expect(fmtTime('2026-11-11T09:30:00'), '09:30 AM');
    expect(fmtTime(null), '—');
    expect(fmtStamp('2026-11-11T09:30:00'), '2026-11-11 09:30');
    expect(fmtDateTime('2026-11-11T09:30:00'), '2026 Nov 11, 09:30 AM');
    expect(fmtTime('not-a-date'), '—');
  });

  test('stream labels and presence', () {
    expect(streamLabel('REGULAR'), 'Regular');
    expect(streamLabel('WEEKLY'), 'Weekly');
    expect(streamLabel('EVENING'), 'Evening');
    expect(streamLabel(null), '—');
    expect(streamLabel('CUSTOM'), 'CUSTOM');
    expect(isPresent('ABSENT'), isFalse);
    expect(isPresent('PRESENT'), isTrue);
    expect(isPresent('LECTURER_MANUAL'), isTrue);
  });

  test('session time range omits the end while running', () {
    final running = StudentSession.fromJson(<String, dynamic>{
      'openedAt': '2026-11-11T09:00:00',
      'startedAt': '2026-11-11T09:05:00',
      'status': 'ACTIVE',
    });
    expect(sessionTimeRange(running), '09:05 AM');

    final ended = StudentSession.fromJson(<String, dynamic>{
      'openedAt': '2026-11-11T09:00:00',
      'startedAt': '2026-11-11T09:05:00',
      'endedAt': '2026-11-11T10:30:00',
      'status': 'ENDED',
    });
    expect(sessionTimeRange(ended), '09:05 AM – 10:30 AM');
  });

  test('initials', () {
    expect(initialsOf('Ama Mensah'), 'AM');
    expect(initialsOf('Kofi'), 'K');
    expect(initialsOf('   '), '?');
  });
}
