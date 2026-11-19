import 'dart:async';

import 'package:flutter/foundation.dart';

import '../core/api_client.dart';
import '../core/models.dart';
import '../platform/native.dart';

class BleSighting {
  BleSighting(this.nodeId, this.rssi, this.timestamp);

  final String nodeId;
  final int rssi;
  final int timestamp;
}

/// Bluetooth verification, driven from Dart.
///
/// The Android host reports real radio sightings (never simulated). This class
/// throttles them per node, posts `/api/attendance/ble-observation` and keeps the
/// server's verdict, exactly like the web panel and the previous native build.
class BleController extends ChangeNotifier {
  BleController(this._api);

  final ApiClient _api;

  StreamSubscription<Map<String, dynamic>>? _subscription;

  bool available = false;
  bool bluetoothOn = false;
  bool permissionsGranted = false;
  bool scanning = false;
  String? message;
  String? error;
  BleSighting? last;
  VerifyResult? result;

  String? _sessionId;
  int _minGapMs = 20000;
  bool _disposed = false;
  bool _active = false;

  /// One observation per node per window. Per-node (not global) matters in a room
  /// full of phones and earbuds: another device must never delay the classroom node.
  final Map<String, int> _lastSentByNode = <String, int>{};

  bool get active => _active;

  Future<void> refreshStatus() async {
    final status = await NativeBridge.bleStatus();
    if (status.isEmpty) return;
    _applyStatus(status);
  }

  void _applyStatus(Map<String, dynamic> status) {
    available = status['available'] == true;
    bluetoothOn = status['bluetoothOn'] == true;
    permissionsGranted = status['permissionsGranted'] == true;
    scanning = status['scanning'] == true;
    final incoming = status['message'];
    message = (incoming == null || '$incoming'.isEmpty) ? null : '$incoming';
    _notify();
  }

  Future<void> start({String? sessionId, int minGapMs = 20000}) async {
    if (_active && _sessionId == sessionId) return;
    if (_sessionId != sessionId) {
      // A different class starts with a clean verdict: a previous session's
      // "verified" must never leak into the new one.
      result = null;
      error = null;
      _lastSentByNode.clear();
    }
    _sessionId = sessionId;
    _minGapMs = minGapMs;
    _active = true;
    _listen();
    await NativeBridge.bleStart(sessionId: sessionId, minGapMs: minGapMs);
    await refreshStatus();
    _notify();
  }

  Future<void> stop() async {
    _active = false;
    await NativeBridge.bleStop();
    _notify();
  }

  Future<void> requestPermissions() async {
    await NativeBridge.requestBlePermissions();
    await refreshStatus();
  }

  void _listen() {
    _subscription ??= NativeBridge.bleStream.listen(_onEvent);
  }

  void _onEvent(Map<String, dynamic> event) {
    if (_disposed) return;
    final type = '${event['type'] ?? ''}';
    if (type == 'status') {
      _applyStatus(event);
      return;
    }
    if (type != 'sighting') return;

    final nodeId = '${event['nodeId'] ?? ''}';
    if (nodeId.isEmpty) return;
    final rssi = event['rssi'] is num ? (event['rssi'] as num).toInt() : null;
    final timestamp = event['timestamp'] is num
        ? (event['timestamp'] as num).toInt()
        : DateTime.now().millisecondsSinceEpoch;
    if (rssi == null) return;

    last = BleSighting(nodeId, rssi, timestamp);
    _notify();

    if (!_active) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    final previous = _lastSentByNode[nodeId] ?? 0;
    if (now - previous < _minGapMs) return;
    _lastSentByNode[nodeId] = now;
    _report(nodeId, rssi, now);
  }

  Future<void> _report(String nodeId, int rssi, int now) async {
    try {
      final body = <String, dynamic>{
        'nodeId': nodeId,
        'rssi': rssi,
        'timestamp': now,
      };
      if (_sessionId != null) body['sessionId'] = _sessionId;
      final res = await _api.post('/api/attendance/ble-observation', body);
      if (_disposed) return;
      result = VerifyResult.fromJson(res);
      error = null;
    } on ApiException catch (e) {
      if (_disposed) return;
      error = e.message;
      result = null;
      // Back off briefly, then allow the next attempt for this node.
      _lastSentByNode[nodeId] = now - _minGapMs + 5000;
    } catch (e) {
      if (_disposed) return;
      error = '$e';
      result = null;
    }
    _notify();
  }

  void _notify() {
    if (_disposed) return;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _subscription?.cancel();
    _subscription = null;
    NativeBridge.bleStop();
    super.dispose();
  }
}
