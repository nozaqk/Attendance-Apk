/// JSON helpers shared by every model.
///
/// The backend is a TypeScript service, so numbers arrive as ints or doubles and
/// missing fields arrive as `null`. These helpers keep every parser tolerant of
/// that, exactly like the Kotlin client's `optString`/`optInt` did.
library;

Map<String, dynamic> asMap(dynamic value) {
  if (value is Map) return Map<String, dynamic>.from(value);
  return <String, dynamic>{};
}

List<Map<String, dynamic>> asMapList(dynamic value) {
  if (value is List) {
    return value.whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
  }
  return <Map<String, dynamic>>[];
}

String str(dynamic value) => value == null ? '' : '$value';

String? optStr(dynamic value) {
  if (value == null) return null;
  final s = '$value';
  return s.isEmpty ? null : s;
}

int asInt(dynamic value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse('$value') ?? 0;
}

int? optInt(dynamic value) {
  if (value == null) return null;
  if (value is num) return value.toInt();
  return int.tryParse('$value');
}

bool asBool(dynamic value) {
  if (value is bool) return value;
  return '$value' == 'true';
}
