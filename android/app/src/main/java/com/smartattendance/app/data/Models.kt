package com.smartattendance.app.data

import org.json.JSONObject

/** Shared user shape (mirrors src/shared/auth.tsx). */
data class User(
    val id: String,
    val email: String,
    val fullName: String,
    val role: String,
    val mustChangePassword: Boolean,
    val studentId: String? = null,
    val departmentId: String? = null,
    val semester: Int? = null,
    val stream: String? = null,
    val staffId: String? = null,
    val departmentName: String? = null
) {
    companion object {
        fun fromJson(o: JSONObject): User {
            val student = o.optJSONObject("student")
            val lecturer = o.optJSONObject("lecturer")
            val dept = o.optJSONObject("department")
            return User(
                id = o.optString("id"),
                email = o.optString("email"),
                fullName = o.optString("fullName"),
                role = o.optString("role"),
                mustChangePassword = o.optBoolean("mustChangePassword", false),
                studentId = student?.optString("studentId")?.takeIf { it.isNotEmpty() },
                departmentId = student?.optString("departmentId")?.takeIf { it.isNotEmpty() },
                semester = if (student != null && !student.isNull("semester")) student.optInt("semester") else null,
                stream = student?.optString("stream")?.takeIf { it.isNotEmpty() },
                staffId = lecturer?.optString("staffId")?.takeIf { it.isNotEmpty() },
                departmentName = dept?.optString("name")?.takeIf { it.isNotEmpty() }
            )
        }
    }
}

data class Department(val id: String, val code: String, val name: String, val semesterCount: Int) {
    companion object {
        fun fromJson(o: JSONObject) = Department(
            o.optString("id"), o.optString("code"), o.optString("name"),
            o.optInt("semesterCount", 8)
        )
    }
}

/** Lecturer session list item (Home). */
data class LecturerSession(
    val id: String, val courseCode: String, val courseName: String,
    val classroomName: String, val sessionDate: String,
    val startedAt: String?, val endedAt: String?, val editableUntil: String?,
    val locked: Boolean, val status: String, val presentCount: Int, val absentCount: Int
) {
    companion object {
        fun fromJson(o: JSONObject) = LecturerSession(
            o.optString("id"), o.optString("courseCode"), o.optString("courseName"),
            o.optJSONObject("classroom")?.optString("name") ?: "",
            o.optString("sessionDate"), o.optString("startedAt").takeIf { !o.isNull("startedAt") },
            o.optString("endedAt").takeIf { !o.isNull("endedAt") },
            o.optString("editableUntil").takeIf { !o.isNull("editableUntil") },
            o.optBoolean("locked", false), o.optString("status"),
            o.optInt("presentCount"), o.optInt("absentCount")
        )
    }
}

/** Roster row (Result + Live). */
data class RosterItem(
    val studentProfileId: String, val studentId: String, val fullName: String,
    val department: String?, val semester: Int?, val stream: String,
    val status: String, val method: String,
    val firstDetectedAt: String?, val lastDetectedAt: String?,
    val observationCount: Int, val lastRssi: Int?, val note: String?
) {
    companion object {
        fun fromJson(o: JSONObject) = RosterItem(
            o.optString("studentProfileId"), o.optString("studentId"), o.optString("fullName"),
            o.optString("department").takeIf { !o.isNull("department") },
            if (!o.isNull("semester")) o.optInt("semester") else null,
            o.optString("stream"), o.optString("status"), o.optString("method"),
            o.optString("firstDetectedAt").takeIf { !o.isNull("firstDetectedAt") },
            o.optString("lastDetectedAt").takeIf { !o.isNull("lastDetectedAt") },
            o.optInt("observationCount"),
            if (!o.isNull("lastRssi")) o.optInt("lastRssi") else null,
            o.optString("note").takeIf { !o.isNull("note") }
        )
    }
}

data class ResultData(
    val courseCode: String, val courseName: String, val classroomName: String,
    val sessionDate: String, val status: String, val editableUntil: String?,
    val locked: Boolean, val total: Int, val present: Int, val verified: Int,
    val late: Int, val manual: Int, val absent: Int, val items: List<RosterItem>
) {
    companion object {
        fun fromJson(o: JSONObject): ResultData {
            val s = o.optJSONObject("session") ?: JSONObject()
            val m = o.optJSONObject("summary") ?: JSONObject()
            val items = mutableListOf<RosterItem>()
            val arr = o.optJSONArray("items")
            if (arr != null) for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { items.add(RosterItem.fromJson(it)) }
            }
            return ResultData(
                s.optString("courseCode"), s.optString("courseName"),
                s.optJSONObject("classroom")?.optString("name") ?: "",
                s.optString("sessionDate"), s.optString("status"),
                s.optString("editableUntil").takeIf { !s.isNull("editableUntil") },
                s.optBoolean("locked", false),
                m.optInt("total"), m.optInt("present"), m.optInt("verified"),
                m.optInt("late"), m.optInt("manual"), m.optInt("absent"), items
            )
        }
    }
}

data class Course(val id: String, val code: String, val name: String) {
    companion object {
        fun fromJson(o: JSONObject) = Course(o.optString("id"), o.optString("code"), o.optString("name"))
    }
}

data class LecturerCourse(
    val id: String, val code: String, val name: String, val description: String?,
    val eligibility: List<Pair<String, Int>>
) {
    companion object {
        fun fromJson(o: JSONObject): LecturerCourse {
            val elig = mutableListOf<Pair<String, Int>>()
            val arr = o.optJSONArray("eligibility")
            if (arr != null) for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                elig.add(e.optString("code") to e.optInt("semester"))
            }
            return LecturerCourse(
                o.optString("id"), o.optString("code"), o.optString("name"),
                o.optString("description").takeIf { !o.isNull("description") }, elig
            )
        }
    }
}

data class StudentRow(
    val id: String, val studentId: String, val fullName: String,
    val department: String?, val semester: Int?, val stream: String, val courses: List<String>
) {
    companion object {
        fun fromJson(o: JSONObject): StudentRow {
            val courses = mutableListOf<String>()
            val arr = o.optJSONArray("courses")
            if (arr != null) for (i in 0 until arr.length()) arr.optString(i).let { courses.add(it) }
            return StudentRow(
                o.optString("id"), o.optString("studentId"), o.optString("fullName"),
                o.optString("department").takeIf { !o.isNull("department") },
                if (!o.isNull("semester")) o.optInt("semester") else null,
                o.optString("stream"), courses
            )
        }
    }
}

data class CurrentSession(
    val id: String, val courseCode: String, val courseName: String,
    val classroomName: String, val nodeId: String?, val status: String,
    val lecturerVerified: Boolean, val editableUntil: String?, val locked: Boolean
) {
    companion object {
        fun fromJson(o: JSONObject) = CurrentSession(
            o.optString("id"), o.optString("courseCode"), o.optString("courseName"),
            o.optJSONObject("classroom")?.optString("name") ?: "",
            o.optString("nodeId").takeIf { !o.isNull("nodeId") },
            o.optString("status"), o.optBoolean("lecturerVerified", false),
            o.optString("editableUntil").takeIf { !o.isNull("editableUntil") },
            o.optBoolean("locked", false)
        )
    }
}

data class Room(
    val id: String, val name: String, val building: String?, val floor: String?,
    val zone: String?, val nodeId: String?, val nodeActive: Boolean, val nodeOnline: Boolean,
    val occupiedByLecturer: String?, val occupiedByCourse: String?
) {
    companion object {
        fun fromJson(o: JSONObject): Room {
            val occ = o.optJSONObject("occupiedBy")
            val floor = when {
                o.isNull("floor") -> null
                o.opt("floor") is Number -> (o.opt("floor") as Number).toInt().toString()
                else -> o.optString("floor").takeIf { it.isNotEmpty() }
            }
            return Room(
                o.optString("id"), o.optString("name"),
                o.optString("building").takeIf { !o.isNull("building") }, floor,
                o.optString("zone").takeIf { !o.isNull("zone") },
                o.optString("nodeId").takeIf { !o.isNull("nodeId") },
                o.optBoolean("nodeActive", false), o.optBoolean("nodeOnline", false),
                occ?.optString("lecturer")?.takeIf { !occ.isNull("lecturer") },
                occ?.optString("courseCode")?.takeIf { it.isNotEmpty() }
            )
        }
    }
}

data class Attendance(
    val status: String, val method: String, val observationCount: Int,
    val firstDetectedAt: String?, val lastDetectedAt: String?
) {
    companion object {
        fun fromJson(o: JSONObject) = Attendance(
            o.optString("status"), o.optString("method"), o.optInt("observationCount"),
            o.optString("firstDetectedAt").takeIf { !o.isNull("firstDetectedAt") },
            o.optString("lastDetectedAt").takeIf { !o.isNull("lastDetectedAt") }
        )
    }
}

data class StudentSession(
    val id: String, val courseCode: String, val courseName: String, val status: String,
    val classroomName: String, val classroomBuilding: String?,
    val lecturerName: String?, val startedAt: String?, val openedAt: String,
    val endedAt: String?, val sessionDate: String, val attendance: Attendance?
) {
    companion object {
        fun fromJson(o: JSONObject): StudentSession {
            val room = o.optJSONObject("classroom")
            val att = o.optJSONObject("attendance")
            return StudentSession(
                o.optString("id"), o.optString("courseCode"), o.optString("courseName"),
                o.optString("status"),
                room?.optString("name") ?: "",
                room?.optString("building")?.takeIf { room != null && !room.isNull("building") },
                o.optJSONObject("lecturer")?.optString("name")?.takeIf { !o.optJSONObject("lecturer").isNull("name") },
                o.optString("startedAt").takeIf { !o.isNull("startedAt") },
                o.optString("openedAt"), o.optString("endedAt").takeIf { !o.isNull("endedAt") },
                o.optString("sessionDate"),
                att?.let { Attendance.fromJson(it) }
            )
        }
    }
}

data class Dashboard(
    val from: String, val to: String, val items: List<StudentSession>,
    val liveClass: StudentSession?, val stats: Map<String, Int>, val unread: Int
) {
    companion object {
        fun fromJson(o: JSONObject): Dashboard {
            val items = mutableListOf<StudentSession>()
            val arr = o.optJSONArray("items")
            if (arr != null) for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { items.add(StudentSession.fromJson(it)) }
            }
            val stats = mutableMapOf<String, Int>()
            val s = o.optJSONObject("stats")
            if (s != null) {
                val keys = s.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    stats[k] = s.optInt(k)
                }
            }
            return Dashboard(
                o.optString("from"), o.optString("to"), items,
                o.optJSONObject("liveClass")?.let { StudentSession.fromJson(it) },
                stats, o.optInt("unreadNotifications")
            )
        }
    }
}

data class StudentCourse(
    val id: String, val code: String, val name: String, val lecturer: String?,
    val isExtra: Boolean, val semester: Int?
) {
    companion object {
        fun fromJson(o: JSONObject) = StudentCourse(
            o.optString("id"), o.optString("code"), o.optString("name"),
            o.optString("lecturer").takeIf { !o.isNull("lecturer") },
            o.optBoolean("isExtra", false),
            if (!o.isNull("semester")) o.optInt("semester") else null
        )
    }
}

data class Profile(
    val fullName: String, val email: String, val studentId: String,
    val department: Department?, val semester: Int?, val stream: String, val semesterCount: Int
) {
    companion object {
        fun fromJson(o: JSONObject): Profile {
            val dept = o.optJSONObject("department")
            return Profile(
                o.optString("fullName"), o.optString("email"), o.optString("studentId"),
                dept?.let { Department.fromJson(it) },
                if (!o.isNull("semester")) o.optInt("semester") else null,
                o.optString("stream"), o.optInt("semesterCount", 8)
            )
        }
    }
}

data class NotifItem(val id: String, val title: String, val body: String, val read: Boolean, val createdAt: String) {
    companion object {
        fun fromJson(o: JSONObject) = NotifItem(
            o.optString("id"), o.optString("title"), o.optString("body"),
            o.optBoolean("read", false), o.optString("createdAt")
        )
    }
}

data class VerifyResult(
    val verified: Boolean, val message: String, val attendance: Attendance?
) {
    companion object {
        fun fromJson(o: JSONObject) = VerifyResult(
            o.optBoolean("verified", false), o.optString("message"),
            o.optJSONObject("attendance")?.let { Attendance.fromJson(it) }
        )
    }
}

data class AttendanceRecord(
    val id: String, val date: String, val courseCode: String, val courseName: String,
    val classroom: String, val status: String, val method: String,
    val firstDetectedAt: String?, val lastDetectedAt: String?
) {
    companion object {
        fun fromJson(o: JSONObject) = AttendanceRecord(
            o.optString("id"), o.optString("date"), o.optString("courseCode"),
            o.optString("courseName"), o.optString("classroom"), o.optString("status"),
            o.optString("method"),
            o.optString("firstDetectedAt").takeIf { !o.isNull("firstDetectedAt") },
            o.optString("lastDetectedAt").takeIf { !o.isNull("lastDetectedAt") }
        )
    }
}
