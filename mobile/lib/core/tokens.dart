import 'package:shared_preferences/shared_preferences.dart';

/// Per-role token storage, so a lecturer session and a student session on the
/// same phone never overwrite each other (the web apps keep separate origins).
class TokenStore {
  TokenStore(this._prefs, this._key);

  final SharedPreferences _prefs;
  final String _key;

  static const String lecturerKey = 'lecturer_token';
  static const String studentKey = 'student_token';

  String? get token {
    final value = _prefs.getString(_key);
    return (value == null || value.isEmpty) ? null : value;
  }

  bool get hasToken => token != null;

  Future<void> save(String? value) async {
    if (value == null || value.isEmpty) {
      await _prefs.remove(_key);
    } else {
      await _prefs.setString(_key, value);
    }
  }
}
