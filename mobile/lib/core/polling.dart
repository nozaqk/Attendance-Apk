import 'dart:async';

import 'package:flutter/material.dart';

import 'api_client.dart';
import '../ui/widgets.dart';

String errorText(Object? error) {
  if (error == null) return 'Unknown error';
  if (error is ApiException) return error.message;
  final text = '$error';
  return text.startsWith('Exception: ') ? text.substring(11) : text;
}

/// Loads data once and then silently refreshes it on an interval — the same
/// behaviour the Compose screens had (`while (true) { load(); delay(...) }`),
/// but with the loading/error/empty states handled in one place.
///
/// While a refresh fails the last good data stays on screen; only the very first
/// load failure shows the error box.
class Poller<T> extends StatefulWidget {
  const Poller({
    super.key,
    required this.fetch,
    required this.builder,
    this.interval,
    this.loadingLabel = 'Loading…',
  });

  final Future<T> Function() fetch;
  final Widget Function(BuildContext context, T data, Future<void> Function() reload) builder;

  /// `null` loads once; a duration keeps refreshing.
  final Duration? interval;
  final String loadingLabel;

  @override
  State<Poller<T>> createState() => PollerState<T>();
}

class PollerState<T> extends State<Poller<T>> {
  T? data;
  Object? error;
  bool loading = true;
  bool _stopped = false;

  @override
  void initState() {
    super.initState();
    _loop();
  }

  Future<void> _loop() async {
    while (!_stopped) {
      await reload();
      final interval = widget.interval;
      if (interval == null || _stopped) return;
      await Future<void>.delayed(interval);
    }
  }

  Future<void> reload() async {
    try {
      final result = await widget.fetch();
      if (_stopped || !mounted) return;
      setState(() {
        data = result;
        error = null;
        loading = false;
      });
    } catch (e) {
      if (_stopped || !mounted) return;
      setState(() {
        if (data == null) error = e;
        loading = false;
      });
    }
  }

  @override
  void dispose() {
    _stopped = true;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (loading && data == null) return LoadingRow(label: widget.loadingLabel);
    final current = data;
    if (current == null) {
      return ErrorBox(errorText(error), onRetry: reload);
    }
    return widget.builder(context, current, reload);
  }
}
