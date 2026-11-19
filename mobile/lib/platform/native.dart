import 'package:flutter/services.dart';

/// Thin, typed bridge to the Android host.
///
/// Everything the user sees (screens, navigation, state, validation, API calls,
/// polling cadence, PDF content and layout) is Dart. Only three things cannot be
/// done from Dart alone, because they belong to the operating system:
///
///  1. Bluetooth LE scanning (`BleController` posts the observations),
///  2. the foreground service that keeps polling for class alerts while the app
///     is closed, and the notifications it posts,
///  3. writing a finished PDF into the public Downloads folder (MediaStore).
///
/// Each call degrades to a no-op when the host does not implement it (tests,
/// desktop), so nothing crashes off-device.
class NativeBridge {
  NativeBridge._();

  static const MethodChannel _methods = MethodChannel('com.smartiq.app/native');
  static const EventChannel _bleEvents = EventChannel('com.smartiq.app/ble');

  static Future<T?> _invoke<T>(String method, [Map<String, dynamic>? args]) async {
    try {
      return await _methods.invokeMethod<T>(method, args);
    } catch (_) {
      return null;
    }
  }

  /// Creates the notification channels and applies the launch notification
  /// permission request.
  static Future<void> ensureChannels() => _invoke<void>('ensureChannels');

  static Future<void> requestNotificationPermission() =>
      _invoke<void>('requestNotificationPermission');

  static Future<void> requestBlePermissions() => _invoke<void>('requestBlePermissions');

  // ---------------------------------------------------------------- background

  /// Stores the restricted device token and starts the closed-app poller.
  static Future<void> enableBackgroundAlerts({
    required String apiBase,
    required String deviceToken,
  }) =>
      _invoke<void>('enableBackgroundAlerts', <String, dynamic>{
        'apiBase': apiBase,
        'deviceToken': deviceToken,
      });

  /// Stops the poller, forgets the token and revokes it on the server.
  static Future<void> disableBackgroundAlerts() =>
      _invoke<void>('disableBackgroundAlerts');

  static Future<bool> backgroundAlertsRunning() async =>
      await _invoke<bool>('backgroundAlertsRunning') ?? false;

  /// Ids already alerted, shared with the native poller so nothing shows twice.
  static Future<Set<String>> seenIds() async {
    final raw = await _invoke<List<dynamic>>('seenIds');
    if (raw == null) return <String>{};
    return raw.map((e) => '$e').toSet();
  }

  static Future<void> markSeen(Iterable<String> ids) => _invoke<void>(
        'markSeen',
        <String, dynamic>{'ids': ids.toList()},
      );

  static Future<void> showNotification({
    required String id,
    required String title,
    required String body,
  }) =>
      _invoke<void>('showNotification', <String, dynamic>{
        'id': id,
        'title': title,
        'body': body,
      });

  /// True when the app was opened by tapping a class alert (and clears the flag).
  static Future<bool> consumeOpenNotifications() async =>
      await _invoke<bool>('consumeOpenNotifications') ?? false;

  /// Complementary stream for later taps: the host invokes `openNotifications`.
  static void setForegroundTapHandler(VoidCallback onOpenFeed) {
    _methods.setMethodCallHandler((MethodCall call) async {
      if (call.method == 'openNotifications') onOpenFeed();
      return null;
    });
  }

  // ---------------------------------------------------------------------- BLE

  static Stream<Map<String, dynamic>> get bleStream => _bleEvents
      .receiveBroadcastStream()
      .map((dynamic event) {
        if (event is Map) return Map<String, dynamic>.from(event);
        return <String, dynamic>{};
      });

  static Future<Map<String, dynamic>> bleStatus() async {
    final raw = await _invoke<Map<dynamic, dynamic>>('bleStatus');
    if (raw == null) return <String, dynamic>{};
    return Map<String, dynamic>.from(raw);
  }

  static Future<void> bleStart({String? sessionId, required int minGapMs}) =>
      _invoke<void>('bleStart', <String, dynamic>{
        'sessionId': sessionId,
        'minGapMs': minGapMs,
      });

  static Future<void> bleStop() => _invoke<void>('bleStop');

  // ---------------------------------------------------------------------- PDF

  /// Draws the activity report natively and saves it into Downloads.
  static Future<String?> exportActivityPdf(Map<String, dynamic> payload) =>
      _invoke<String>('exportActivityPdf', payload);
}
