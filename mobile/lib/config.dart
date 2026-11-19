/// Build-time configuration.
///
/// The backend origin is injected with
/// `flutter build apk --dart-define=API_BASE_URL=https://your-domain`
/// (the CI workflow sets it from the `API_BASE_URL` repository variable).
/// Nothing secret lives here: it is a public origin.
class AppConfig {
  const AppConfig._();

  static const String _override = String.fromEnvironment('API_BASE_URL');

  /// Render host used when no `--dart-define` is supplied.
  static const String _fallback = 'https://attendance-system-33pl.onrender.com';

  /// Backend origin without a trailing slash. ApiClient appends `/api/...`.
  static String get apiBase {
    final raw = _override.trim().isEmpty ? _fallback : _override.trim();
    return raw.endsWith('/') ? raw.substring(0, raw.length - 1) : raw;
  }

  /// True when the placeholder domain slipped through a build; the app logs it.
  static bool get placeholderBase => apiBase.contains('yourdomain.com');
}
