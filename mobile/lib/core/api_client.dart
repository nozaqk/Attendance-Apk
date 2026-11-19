import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../config.dart';

/// Error raised for every failed request, carrying the server's own code and
/// message so screens can show exactly what the backend said.
class ApiException implements Exception {
  ApiException(this.status, this.code, this.message);

  final int status;
  final String code;
  final String message;

  @override
  String toString() => message;
}

/// JSON client for the SmartIQ backend.
///
/// Mirrors the web client (`src/shared/api.ts`):
///  * sends `Authorization: Bearer <token>` whenever a token is available,
///  * surfaces `{ error: { code, message } }` as [ApiException],
///  * clears the session on 401 unless the credentials themselves were wrong.
class ApiClient {
  ApiClient({
    String? Function()? tokenProvider,
    void Function()? onUnauthorized,
    http.Client? httpClient,
  })  : _tokenProvider = tokenProvider ?? (() => null),
        _onUnauthorized = onUnauthorized ?? (() {}),
        _http = httpClient ?? http.Client();

  final String? Function() _tokenProvider;
  final void Function() _onUnauthorized;
  final http.Client _http;

  static const Duration _timeout = Duration(seconds: 20);

  static String get base => AppConfig.apiBase;

  Future<Map<String, dynamic>> get(String path) => _send('GET', path, null);

  Future<Map<String, dynamic>> post(String path, [Map<String, dynamic>? body]) =>
      _send('POST', path, body ?? <String, dynamic>{});

  Future<Map<String, dynamic>> put(String path, Map<String, dynamic> body) =>
      _send('PUT', path, body);

  Future<Map<String, dynamic>> delete(String path) => _send('DELETE', path, null);

  Future<Map<String, dynamic>> _send(
    String method,
    String path,
    Map<String, dynamic>? body,
  ) async {
    final token = _tokenProvider();
    final uri = Uri.parse(base + path);
    final headers = <String, String>{'Accept': 'application/json'};
    if (body != null) headers['Content-Type'] = 'application/json';
    if (token != null && token.isNotEmpty) {
      headers['Authorization'] = 'Bearer $token';
    }

    http.Response response;
    try {
      final request = http.Request(method, uri)..headers.addAll(headers);
      if (body != null) request.body = jsonEncode(body);
      final streamed = await _http.send(request).timeout(_timeout);
      response = await http.Response.fromStream(streamed);
    } on TimeoutException {
      throw ApiException(0, 'NETWORK', _offline);
    } on SocketException {
      throw ApiException(0, 'NETWORK', _offline);
    } on http.ClientException {
      throw ApiException(0, 'NETWORK', _offline);
    } catch (_) {
      throw ApiException(0, 'NETWORK', _offline);
    }

    final status = response.statusCode;
    final raw = response.body;
    final data = _decode(raw);

    if (status >= 200 && status < 300) return data;

    final error = data['error'];
    final code = (error is Map && error['code'] != null && '${error['code']}'.isNotEmpty)
        ? '${error['code']}'
        : 'ERROR';
    final message =
        (error is Map && error['message'] != null && '${error['message']}'.isNotEmpty)
            ? '${error['message']}'
            : 'Request failed ($status)';

    if (status == 401 && token != null && token.isNotEmpty && code != 'INVALID_CREDENTIALS') {
      _onUnauthorized();
    }
    throw ApiException(status, code, message);
  }

  static const String _offline =
      'Cannot reach the server. Check your internet connection and try again.';

  static Map<String, dynamic> _decode(String raw) {
    if (raw.trim().isEmpty) return <String, dynamic>{};
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map) return Map<String, dynamic>.from(decoded);
      return <String, dynamic>{};
    } catch (_) {
      return <String, dynamic>{};
    }
  }
}
