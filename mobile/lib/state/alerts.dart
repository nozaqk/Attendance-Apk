import 'dart:async';

import '../core/api_client.dart';
import '../core/json.dart';
import '../core/models.dart';
import '../platform/native.dart';

/// Class alerts for the student portal.
///
/// Two delivery paths, sharing one seen-id set so an alert is never shown twice:
///
///  * **closed app** – the Android host polls the restricted-token feed every
///    30 s from a foreground service and posts the notification itself;
///  * **foreground fallback** – while the app is open and the service could not
///    run (notification permission denied, OEM battery manager), this watcher
///    polls the normal student route and posts through the same channel.
class BackgroundAlerts {
  const BackgroundAlerts._();

  /// Issues a restricted device token and starts the closed-app poller.
  static Future<void> enable(ApiClient api) async {
    final res = await api.post('/api/student/notifications/device-token');
    final token = str(res['token']);
    if (token.isEmpty) return;
    await NativeBridge.enableBackgroundAlerts(
      apiBase: ApiClient.base,
      deviceToken: token,
    );
  }

  /// Sign-out: stop polling, forget the token and revoke it server-side.
  static Future<void> disable() => NativeBridge.disableBackgroundAlerts();
}

class ForegroundAlerter {
  ForegroundAlerter(this._api);

  final ApiClient _api;
  static const Duration _interval = Duration(seconds: 15);

  bool _stopped = false;

  void start() {
    _stopped = false;
    unawaited(_loop());
  }

  void stop() {
    _stopped = true;
  }

  Future<void> _loop() async {
    while (!_stopped) {
      await _tick();
      if (_stopped) return;
      await Future<void>.delayed(_interval);
    }
  }

  Future<void> _tick() async {
    // The native poller already covers this; never alert twice.
    if (await NativeBridge.backgroundAlertsRunning()) return;
    final res = await _api.get('/api/student/notifications');
    if (_stopped) return;
    final page = NotificationsPage.fromJson(res);
    if (page.items.isEmpty) return;
    final seen = await NativeBridge.seenIds();
    final fresh = <String>[];
    for (final item in page.items) {
      if (item.read || seen.contains(item.id)) continue;
      await NativeBridge.showNotification(
        id: item.id,
        title: item.title.isEmpty ? 'SmartIQ' : item.title,
        body: item.body,
      );
      fresh.add(item.id);
    }
    if (fresh.isNotEmpty) await NativeBridge.markSeen(fresh);
  }
}
