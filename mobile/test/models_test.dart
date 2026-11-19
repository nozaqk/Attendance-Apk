import 'package:flutter_test/flutter_test.dart';
import 'package:smartiq/core/models.dart';

void main() {
  test('User tolerates missing nested objects and numeric semester', () {
    final user = User.fromJson(<String, dynamic>{
      'id': 'u1',
      'email': 'a@b.c',
      'fullName': 'Ama Mensah',
      'role': 'STUDENT',
      'mustChangePassword': false,
      'student': <String, dynamic>{'studentId': 'S123', 'departmentId': 'd1', 'semester': 3},
      'department': <String, dynamic>{'name': 'Computer Science'},
    });
    expect(user.studentId, 'S123');
    expect(user.semester, 3);
    expect(user.departmentName, 'Computer Science');
    expect(user.setupComplete, isTrue);
  });

  test('User without student block is not set up', () {
    final user = User.fromJson(<String, dynamic>{'role': 'STUDENT'});
    expect(user.setupComplete, isFalse);
    expect(user.stream, isNull);
  });

  test('Dashboard reads stats as a map of ints and the live class', () {
    final dashboard = Dashboard.fromJson(<String, dynamic>{
      'from': '2026-11-11',
      'to': '2026-11-11',
      'unreadNotifications': 2,
      'stats': <String, dynamic>{'PRESENT': 4, 'ABSENT': '1', 'LATE': 2.0},
      'liveClass': <String, dynamic>{
        'id': 's1',
        'courseName': 'Algorithms',
        'classroom': <String, dynamic>{'name': 'Room 4'},
        'status': 'ACTIVE',
        'openedAt': '2026-11-11T09:00:00.000Z',
      },
      'items': <dynamic>[
        <String, dynamic>{
          'id': 's1',
          'courseCode': 'CS301',
          'courseName': 'Algorithms',
          'status': 'ACTIVE',
          'openedAt': '2026-11-11T09:00:00.000Z',
          'attendance': <String, dynamic>{'status': 'PRESENT', 'method': 'BLE'},
        },
      ],
    });
    expect(dashboard.stat('PRESENT'), 4);
    expect(dashboard.stat('ABSENT'), 1);
    expect(dashboard.stat('LATE'), 2);
    expect(dashboard.liveClass?.classroomName, 'Room 4');
    expect(dashboard.items.single.attendance?.method, 'BLE');
    expect(dashboard.unread, 2);
  });

  test('ResultData summarises a roster', () {
    final result = ResultData.fromJson(<String, dynamic>{
      'session': <String, dynamic>{
        'courseCode': 'CS301',
        'courseName': 'Algorithms',
        'sessionDate': '2026-11-11',
        'status': 'ENDED',
        'classroom': <String, dynamic>{'name': 'Room 4'},
      },
      'summary': <String, dynamic>{'total': 10, 'present': 8, 'absent': 2, 'manual': 1},
      'items': <dynamic>[
        <String, dynamic>{'fullName': 'Ama', 'studentId': 'S1', 'status': 'PRESENT', 'method': 'BLE'},
        <String, dynamic>{'fullName': 'Kofi', 'studentId': 'S2', 'status': 'ABSENT', 'method': 'NONE'},
      ],
    });
    expect(result.total, 10);
    expect(result.attended, 8);
    expect(result.items.length, 2);
    expect(result.items.first.present, isTrue);
    expect(result.items.last.present, isFalse);
  });

  test('Room exposes occupancy and node availability', () {
    final room = Room.fromJson(<String, dynamic>{
      'id': 'r1',
      'name': 'Room 4',
      'floor': 2,
      'nodeId': 'node-4',
      'nodeActive': true,
      'nodeOnline': false,
      'occupiedBy': <String, dynamic>{'lecturer': 'Dr B', 'courseCode': 'CS301'},
    });
    expect(room.floor, '2');
    expect(room.taken, isTrue);
    expect(room.hasNode, isTrue);
    expect(room.subline, contains('Floor 2'));
  });
}
