import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smartiq/core/api_client.dart';

void main() {
  test('returns decoded JSON on success', () async {
    final api = ApiClient(
      httpClient: MockClient((http.Request request) async {
        expect(request.method, 'GET');
        return http.Response('{"items":[]}', 200);
      }),
    );
    final res = await api.get('/api/lecturer/sessions');
    expect(res['items'], isA<List<dynamic>>());
  });

  test('sends the bearer token when one exists', () async {
    String? seen;
    final api = ApiClient(
      tokenProvider: () => 'tok-123',
      httpClient: MockClient((http.Request request) async {
        seen = request.headers['Authorization'];
        return http.Response('{}', 200);
      }),
    );
    await api.get('/api/auth/me');
    expect(seen, 'Bearer tok-123');
  });

  test('surfaces the server code and message', () async {
    final api = ApiClient(
      httpClient: MockClient(
        (http.Request request) async => http.Response(
          '{"error":{"code":"FORBIDDEN","message":"Attendance is locked"}}',
          403,
        ),
      ),
    );
    try {
      await api.post('/api/lecturer/attendance/manual');
      fail('expected an ApiException');
    } on ApiException catch (e) {
      expect(e.status, 403);
      expect(e.code, 'FORBIDDEN');
      expect(e.message, 'Attendance is locked');
    }
  });

  test('a 401 clears the session, invalid credentials do not', () async {
    var cleared = 0;
    final expired = ApiClient(
      tokenProvider: () => 'tok',
      onUnauthorized: () => cleared++,
      httpClient: MockClient(
        (http.Request request) async => http.Response(
          '{"error":{"code":"TOKEN_EXPIRED","message":"Expired"}}',
          401,
        ),
      ),
    );
    await expectLater(expired.get('/api/auth/me'), throwsA(isA<ApiException>()));
    expect(cleared, 1);

    final badLogin = ApiClient(
      tokenProvider: () => 'tok',
      onUnauthorized: () => cleared++,
      httpClient: MockClient(
        (http.Request request) async => http.Response(
          '{"error":{"code":"INVALID_CREDENTIALS","message":"Wrong password"}}',
          401,
        ),
      ),
    );
    await expectLater(badLogin.post('/api/auth/login'), throwsA(isA<ApiException>()));
    expect(cleared, 1);
  });

  test('empty bodies and 204 do not throw', () async {
    final api = ApiClient(
      httpClient: MockClient((http.Request request) async => http.Response('', 204)),
    );
    expect(await api.delete('/api/student/courses/extra/c1'), isEmpty);
  });
}
