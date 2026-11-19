import 'json.dart';

/// Shapes returned by the backend, mirroring `src/shared/auth.ts` and the
/// Kotlin client's `Models.kt`.

class User {
  User({
    required this.id,
    required this.email,
    required this.fullName,
    required this.role,
    required this.mustChangePassword,
    this.studentId,
    this.departmentId,
    this.semester,
    this.stream,
    this.staffId,
    this.departmentName,
  });

  final String id;
  final String email;
  final String fullName;
  final String role;
  final bool mustChangePassword;
  final String? studentId;
  final String? departmentId;
  final int? semester;
  final String? stream;
  final String? staffId;
  final String? departmentName;

  /// A student can only use Home/Class/Course once the department and semester
  /// are known (the web app redirects to Settings for the same reason).
  bool get setupComplete => semester != null && departmentId != null;

  factory User.fromJson(Map<String, dynamic> json) {
    final student = asMap(json['student']);
    final lecturer = asMap(json['lecturer']);
    final department = asMap(json['department']);
    return User(
      id: str(json['id']),
      email: str(json['email']),
      fullName: str(json['fullName']),
      role: str(json['role']),
      mustChangePassword: asBool(json['mustChangePassword']),
      studentId: optStr(student['studentId']),
      departmentId: optStr(student['departmentId']),
      semester: optInt(student['semester']),
      stream: optStr(student['stream']),
      staffId: optStr(lecturer['staffId']),
      departmentName: optStr(department['name']),
    );
  }
}

class Department {
  Department({
    required this.id,
    required this.code,
    required this.name,
    required this.semesterCount,
  });

  final String id;
  final String code;
  final String name;
  final int semesterCount;

  factory Department.fromJson(Map<String, dynamic> json) => Department(
        id: str(json['id']),
        code: str(json['code']),
        name: str(json['name']),
        semesterCount: optInt(json['semesterCount']) ?? 8,
      );

  static List<Department> listFrom(dynamic value) =>
      asMapList(value).map(Department.fromJson).toList();
}

class LecturerSession {
  LecturerSession({
    required this.id,
    required this.courseCode,
    required this.courseName,
    required this.classroomName,
    required this.sessionDate,
    required this.startedAt,
    required this.endedAt,
    required this.editableUntil,
    required this.locked,
    required this.status,
    required this.presentCount,
    required this.absentCount,
  });

  final String id;
  final String courseCode;
  final String courseName;
  final String classroomName;
  final String sessionDate;
  final String? startedAt;
  final String? endedAt;
  final String? editableUntil;
  final bool locked;
  final String status;
  final int presentCount;
  final int absentCount;

  factory LecturerSession.fromJson(Map<String, dynamic> json) => LecturerSession(
        id: str(json['id']),
        courseCode: str(json['courseCode']),
        courseName: str(json['courseName']),
        classroomName: str(asMap(json['classroom'])['name']),
        sessionDate: str(json['sessionDate']),
        startedAt: optStr(json['startedAt']),
        endedAt: optStr(json['endedAt']),
        editableUntil: optStr(json['editableUntil']),
        locked: asBool(json['locked']),
        status: str(json['status']),
        presentCount: asInt(json['presentCount']),
        absentCount: asInt(json['absentCount']),
      );

  static List<LecturerSession> listFrom(dynamic value) =>
      asMapList(value).map(LecturerSession.fromJson).toList();
}

class RosterItem {
  RosterItem({
    required this.studentProfileId,
    required this.studentId,
    required this.fullName,
    required this.department,
    required this.semester,
    required this.stream,
    required this.status,
    required this.method,
    required this.firstDetectedAt,
    required this.lastDetectedAt,
    required this.observationCount,
    required this.lastRssi,
    required this.note,
  });

  final String studentProfileId;
  final String studentId;
  final String fullName;
  final String? department;
  final int? semester;
  final String stream;
  final String status;
  final String method;
  final String? firstDetectedAt;
  final String? lastDetectedAt;
  final int observationCount;
  final int? lastRssi;
  final String? note;

  bool get present => status != 'ABSENT';

  factory RosterItem.fromJson(Map<String, dynamic> json) => RosterItem(
        studentProfileId: str(json['studentProfileId']),
        studentId: str(json['studentId']),
        fullName: str(json['fullName']),
        department: optStr(json['department']),
        semester: optInt(json['semester']),
        stream: str(json['stream']),
        status: str(json['status']),
        method: str(json['method']),
        firstDetectedAt: optStr(json['firstDetectedAt']),
        lastDetectedAt: optStr(json['lastDetectedAt']),
        observationCount: asInt(json['observationCount']),
        lastRssi: optInt(json['lastRssi']),
        note: optStr(json['note']),
      );

  static List<RosterItem> listFrom(dynamic value) =>
      asMapList(value).map(RosterItem.fromJson).toList();
}

class ResultData {
  ResultData({
    required this.courseCode,
    required this.courseName,
    required this.classroomName,
    required this.sessionDate,
    required this.status,
    required this.editableUntil,
    required this.locked,
    required this.total,
    required this.present,
    required this.verified,
    required this.late,
    required this.manual,
    required this.absent,
    required this.items,
  });

  final String courseCode;
  final String courseName;
  final String classroomName;
  final String sessionDate;
  final String status;
  final String? editableUntil;
  final bool locked;
  final int total;
  final int present;
  final int verified;
  final int late;
  final int manual;
  final int absent;
  final List<RosterItem> items;

  int get attended => total - absent;

  factory ResultData.fromJson(Map<String, dynamic> json) {
    final session = asMap(json['session']);
    final summary = asMap(json['summary']);
    return ResultData(
      courseCode: str(session['courseCode']),
      courseName: str(session['courseName']),
      classroomName: str(asMap(session['classroom'])['name']),
      sessionDate: str(session['sessionDate']),
      status: str(session['status']),
      editableUntil: optStr(session['editableUntil']),
      locked: asBool(session['locked']),
      total: asInt(summary['total']),
      present: asInt(summary['present']),
      verified: asInt(summary['verified']),
      late: asInt(summary['late']),
      manual: asInt(summary['manual']),
      absent: asInt(summary['absent']),
      items: RosterItem.listFrom(json['items']),
    );
  }
}

class Course {
  Course({required this.id, required this.code, required this.name});

  final String id;
  final String code;
  final String name;

  String get label => '$code · $name';

  factory Course.fromJson(Map<String, dynamic> json) => Course(
        id: str(json['id']),
        code: str(json['code']),
        name: str(json['name']),
      );

  static List<Course> listFrom(dynamic value) =>
      asMapList(value).map(Course.fromJson).toList();
}

class Eligibility {
  Eligibility(this.code, this.semester);

  final String code;
  final int semester;

  String get label => '$code · Sem $semester';
}

class LecturerCourse {
  LecturerCourse({
    required this.id,
    required this.code,
    required this.name,
    required this.description,
    required this.eligibility,
  });

  final String id;
  final String code;
  final String name;
  final String? description;
  final List<Eligibility> eligibility;

  factory LecturerCourse.fromJson(Map<String, dynamic> json) => LecturerCourse(
        id: str(json['id']),
        code: str(json['code']),
        name: str(json['name']),
        description: optStr(json['description']),
        eligibility: asMapList(json['eligibility'])
            .map((e) => Eligibility(str(e['code']), asInt(e['semester'])))
            .toList(),
      );

  static List<LecturerCourse> listFrom(dynamic value) =>
      asMapList(value).map(LecturerCourse.fromJson).toList();
}

class StudentRow {
  StudentRow({
    required this.id,
    required this.studentId,
    required this.fullName,
    required this.department,
    required this.semester,
    required this.stream,
    required this.courses,
  });

  final String id;
  final String studentId;
  final String fullName;
  final String? department;
  final int? semester;
  final String stream;
  final List<String> courses;

  factory StudentRow.fromJson(Map<String, dynamic> json) => StudentRow(
        id: str(json['id']),
        studentId: str(json['studentId']),
        fullName: str(json['fullName']),
        department: optStr(json['department']),
        semester: optInt(json['semester']),
        stream: str(json['stream']),
        courses: json['courses'] is List
            ? (json['courses'] as List).map((e) => str(e)).toList()
            : <String>[],
      );

  static List<StudentRow> listFrom(dynamic value) =>
      asMapList(value).map(StudentRow.fromJson).toList();
}

class CurrentSession {
  CurrentSession({
    required this.id,
    required this.courseCode,
    required this.courseName,
    required this.classroomName,
    required this.nodeId,
    required this.status,
    required this.lecturerVerified,
    required this.editableUntil,
    required this.locked,
  });

  final String id;
  final String courseCode;
  final String courseName;
  final String classroomName;
  final String? nodeId;
  final String status;
  final bool lecturerVerified;
  final String? editableUntil;
  final bool locked;

  factory CurrentSession.fromJson(Map<String, dynamic> json) => CurrentSession(
        id: str(json['id']),
        courseCode: str(json['courseCode']),
        courseName: str(json['courseName']),
        classroomName: str(asMap(json['classroom'])['name']),
        nodeId: optStr(json['nodeId']),
        status: str(json['status']),
        lecturerVerified: asBool(json['lecturerVerified']),
        editableUntil: optStr(json['editableUntil']),
        locked: asBool(json['locked']),
      );
}

class Room {
  Room({
    required this.id,
    required this.name,
    required this.building,
    required this.floor,
    required this.zone,
    required this.nodeId,
    required this.nodeActive,
    required this.nodeOnline,
    required this.occupiedByLecturer,
    required this.occupiedByCourse,
  });

  final String id;
  final String name;
  final String? building;
  final String? floor;
  final String? zone;
  final String? nodeId;
  final bool nodeActive;
  final bool nodeOnline;
  final String? occupiedByLecturer;
  final String? occupiedByCourse;

  bool get taken => occupiedByCourse != null;
  bool get hasNode => nodeId != null && nodeActive;

  String get subline => <String>[
        if (building != null) building!,
        if (floor != null) 'Floor $floor',
        if (zone != null) zone!,
      ].join(' · ');

  factory Room.fromJson(Map<String, dynamic> json) {
    final occupied = asMap(json['occupiedBy']);
    final floorRaw = json['floor'];
    return Room(
      id: str(json['id']),
      name: str(json['name']),
      building: optStr(json['building']),
      floor: floorRaw == null ? null : str(floorRaw),
      zone: optStr(json['zone']),
      nodeId: optStr(json['nodeId']),
      nodeActive: asBool(json['nodeActive']),
      nodeOnline: asBool(json['nodeOnline']),
      occupiedByLecturer: optStr(occupied['lecturer']),
      occupiedByCourse: optStr(occupied['courseCode']),
    );
  }

  static List<Room> listFrom(dynamic value) =>
      asMapList(value).map(Room.fromJson).toList();
}

class Attendance {
  Attendance({
    required this.status,
    required this.method,
    required this.observationCount,
    required this.firstDetectedAt,
    required this.lastDetectedAt,
  });

  final String status;
  final String method;
  final int observationCount;
  final String? firstDetectedAt;
  final String? lastDetectedAt;

  bool get byLecturer => method == 'LECTURER_MANUAL';

  factory Attendance.fromJson(Map<String, dynamic> json) => Attendance(
        status: str(json['status']),
        method: str(json['method']),
        observationCount: asInt(json['observationCount']),
        firstDetectedAt: optStr(json['firstDetectedAt']),
        lastDetectedAt: optStr(json['lastDetectedAt']),
      );
}

class StudentSession {
  StudentSession({
    required this.id,
    required this.courseCode,
    required this.courseName,
    required this.status,
    required this.classroomName,
    required this.classroomBuilding,
    required this.lecturerName,
    required this.startedAt,
    required this.openedAt,
    required this.endedAt,
    required this.sessionDate,
    required this.attendance,
  });

  final String id;
  final String courseCode;
  final String courseName;
  final String status;
  final String classroomName;
  final String? classroomBuilding;
  final String? lecturerName;
  final String? startedAt;
  final String openedAt;
  final String? endedAt;
  final String sessionDate;
  final Attendance? attendance;

  factory StudentSession.fromJson(Map<String, dynamic> json) {
    final room = asMap(json['classroom']);
    final attendanceRaw = json['attendance'];
    return StudentSession(
      id: str(json['id']),
      courseCode: str(json['courseCode']),
      courseName: str(json['courseName']),
      status: str(json['status']),
      classroomName: str(room['name']),
      classroomBuilding: optStr(room['building']),
      lecturerName: optStr(json['lecturerName']),
      startedAt: optStr(json['startedAt']),
      openedAt: str(json['openedAt']),
      endedAt: optStr(json['endedAt']),
      sessionDate: str(json['sessionDate']),
      attendance: attendanceRaw is Map ? Attendance.fromJson(asMap(attendanceRaw)) : null,
    );
  }

  static List<StudentSession> listFrom(dynamic value) =>
      asMapList(value).map(StudentSession.fromJson).toList();
}

class Dashboard {
  Dashboard({
    required this.from,
    required this.to,
    required this.items,
    required this.liveClass,
    required this.stats,
    required this.unread,
  });

  final String from;
  final String to;
  final List<StudentSession> items;
  final StudentSession? liveClass;
  final Map<String, int> stats;
  final int unread;

  int stat(String key) => stats[key] ?? 0;

  factory Dashboard.fromJson(Map<String, dynamic> json) {
    final rawStats = asMap(json['stats']);
    final live = json['liveClass'];
    return Dashboard(
      from: str(json['from']),
      to: str(json['to']),
      items: StudentSession.listFrom(json['items']),
      liveClass: live is Map ? StudentSession.fromJson(asMap(live)) : null,
      stats: rawStats.map((key, value) => MapEntry(key.toString(), asInt(value))),
      unread: asInt(json['unreadNotifications']),
    );
  }
}

class StudentCourse {
  StudentCourse({
    required this.id,
    required this.code,
    required this.name,
    required this.lecturer,
    required this.isExtra,
    required this.semester,
  });

  final String id;
  final String code;
  final String name;
  final String? lecturer;
  final bool isExtra;
  final int? semester;

  factory StudentCourse.fromJson(Map<String, dynamic> json) => StudentCourse(
        id: str(json['id']),
        code: str(json['code']),
        name: str(json['name']),
        lecturer: optStr(json['lecturer']),
        isExtra: asBool(json['isExtra']),
        semester: optInt(json['semester']),
      );

  static List<StudentCourse> listFrom(dynamic value) =>
      asMapList(value).map(StudentCourse.fromJson).toList();
}

class Profile {
  Profile({
    required this.fullName,
    required this.email,
    required this.studentId,
    required this.department,
    required this.semester,
    required this.stream,
    required this.semesterCount,
  });

  final String fullName;
  final String email;
  final String studentId;
  final Department? department;
  final int? semester;
  final String stream;
  final int semesterCount;

  bool get needsSetup => semester == null || department == null;

  factory Profile.fromJson(Map<String, dynamic> json) {
    final department = json['department'];
    return Profile(
      fullName: str(json['fullName']),
      email: str(json['email']),
      studentId: str(json['studentId']),
      department: department is Map ? Department.fromJson(asMap(department)) : null,
      semester: optInt(json['semester']),
      stream: str(json['stream']),
      semesterCount: optInt(json['semesterCount']) ?? 8,
    );
  }
}

class NotifItem {
  NotifItem({
    required this.id,
    required this.title,
    required this.body,
    required this.read,
    required this.createdAt,
  });

  final String id;
  final String title;
  final String body;
  final bool read;
  final String createdAt;

  factory NotifItem.fromJson(Map<String, dynamic> json) => NotifItem(
        id: str(json['id']),
        title: str(json['title']),
        body: str(json['body']),
        read: asBool(json['read']),
        createdAt: str(json['createdAt']),
      );

  static List<NotifItem> listFrom(dynamic value) =>
      asMapList(value).map(NotifItem.fromJson).toList();
}

class NotificationsPage {
  NotificationsPage({required this.items, required this.unread});

  final List<NotifItem> items;
  final int unread;

  factory NotificationsPage.fromJson(Map<String, dynamic> json) => NotificationsPage(
        items: NotifItem.listFrom(json['items']),
        unread: asInt(json['unread']),
      );
}

class VerifyResult {
  VerifyResult({required this.verified, required this.message, required this.attendance});

  final bool verified;
  final String message;
  final Attendance? attendance;

  factory VerifyResult.fromJson(Map<String, dynamic> json) {
    final attendance = json['attendance'];
    return VerifyResult(
      verified: asBool(json['verified']),
      message: str(json['message']),
      attendance:
          attendance is Map ? Attendance.fromJson(asMap(attendance)) : null,
    );
  }
}

class AttendanceRecord {
  AttendanceRecord({
    required this.id,
    required this.date,
    required this.courseCode,
    required this.courseName,
    required this.classroom,
    required this.status,
    required this.method,
    required this.firstDetectedAt,
    required this.lastDetectedAt,
  });

  final String id;
  final String date;
  final String courseCode;
  final String courseName;
  final String classroom;
  final String status;
  final String method;
  final String? firstDetectedAt;
  final String? lastDetectedAt;

  Map<String, dynamic> toExportRow() => <String, dynamic>{
        'date': date,
        'course': '$courseCode $courseName',
        'courseCode': courseCode,
        'courseName': courseName,
        'classroom': classroom,
        'status': status,
        'method': method,
        'first': firstDetectedAt,
        'last': lastDetectedAt,
      };

  factory AttendanceRecord.fromJson(Map<String, dynamic> json) => AttendanceRecord(
        id: str(json['id']),
        date: str(json['date']),
        courseCode: str(json['courseCode']),
        courseName: str(json['courseName']),
        classroom: str(json['classroom']),
        status: str(json['status']),
        method: str(json['method']),
        firstDetectedAt: optStr(json['firstDetectedAt']),
        lastDetectedAt: optStr(json['lastDetectedAt']),
      );

  static List<AttendanceRecord> listFrom(dynamic value) =>
      asMapList(value).map(AttendanceRecord.fromJson).toList();
}
