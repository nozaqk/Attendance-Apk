import 'package:flutter/foundation.dart';

import '../core/api_client.dart';
import '../core/models.dart';
import '../core/tokens.dart';

const Map<String, String> roleLabels = <String, String>{
  'ADMIN': 'administrator',
  'LECTURER': 'lecturer',
  'STUDENT': 'student',
  'QA': 'quality assurance',
};

/// Signs in and rejects credentials that belong to the other portal.
Future<({String token, User user})> doLogin(
  ApiClient api,
  String email,
  String password,
  String expectedRole,
) async {
  final res = await api.post('/api/auth/login', <String, dynamic>{
    'email': email,
    'password': password,
  });
  final user = User.fromJson(res['user'] is Map ? Map<String, dynamic>.from(res['user'] as Map) : <String, dynamic>{});
  if (user.role != expectedRole) {
    throw Exception(
      'This is a ${expectedRole.toLowerCase()} portal. Your account is a '
      '${roleLabels[user.role] ?? user.role.toLowerCase()} account.',
    );
  }
  return (token: '${res['token'] ?? ''}', user: user);
}

/// Holds one portal's session: token, signed-in user and the API client bound to
/// that token. Lecturer and student sessions are completely independent.
class SessionController extends ChangeNotifier {
  SessionController({required this.role, required this.store})
      : api = ApiClient(tokenProvider: () => store.token);

  final String role;
  final TokenStore store;
  final ApiClient api;

  User? user;
  bool loading = false;
  bool _disposed = false;

  ApiClient get client => api;

  /// Restores a stored session on launch and verifies it against `/api/auth/me`.
  Future<void> bootstrap() async {
    if (store.token == null) {
      loading = false;
      _notify();
      return;
    }
    loading = true;
    _notify();
    await refreshUser();
    loading = false;
    _notify();
  }

  Future<void> refreshUser() async {
    if (store.token == null) {
      user = null;
      _notify();
      return;
    }
    try {
      final me = await api.get('/api/auth/me');
      final parsed = User.fromJson(me);
      if (parsed.role != role) {
        await store.save(null);
        user = null;
      } else {
        user = parsed;
      }
    } catch (_) {
      await store.save(null);
      user = null;
    }
    _notify();
  }

  Future<void> signIn(String email, String password) async {
    final result = await doLogin(api, email.trim(), password, role);
    await store.save(result.token);
    user = result.user;
    _notify();
  }

  Future<void> signOut({bool revokeDeviceToken = false}) async {
    try {
      await api.post('/api/auth/logout');
    } catch (_) {
      // Offline sign-out still clears the local session.
    }
    if (revokeDeviceToken && revokeAlerts != null) {
      try {
        await revokeAlerts!();
      } catch (_) {
        // best effort: the local session is cleared regardless
      }
    }
    await store.save(null);
    user = null;
    _notify();
  }

  /// Set by the student gate: stops the background poller and revokes the token.
  Future<void> Function()? revokeAlerts;

  void _notify() {
    if (_disposed) return;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
