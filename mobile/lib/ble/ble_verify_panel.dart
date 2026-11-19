import 'package:flutter/material.dart';

import '../ui/theme.dart';
import '../ui/widgets.dart';
import 'ble_controller.dart';

/// Flutter equivalent of the web `BleVerifyPanel`: permission/Bluetooth state,
/// the last node sighting and the server's verdict. Scanning runs while mounted.
class BleVerifyPanel extends StatefulWidget {
  const BleVerifyPanel({
    super.key,
    required this.scanner,
    required this.sessionId,
    required this.minGapMs,
    required this.idleLabel,
    this.onVerified,
  });

  final BleController scanner;
  final String? sessionId;
  final int minGapMs;
  final String idleLabel;
  final VoidCallback? onVerified;

  @override
  State<BleVerifyPanel> createState() => _BleVerifyPanelState();
}

class _BleVerifyPanelState extends State<BleVerifyPanel> {
  late BleController _scanner;

  @override
  void initState() {
    super.initState();
    _scanner = widget.scanner;
    _scanner.addListener(_onChanged);
    _scanner.start(sessionId: widget.sessionId, minGapMs: widget.minGapMs);
  }

  @override
  void didUpdateWidget(covariant BleVerifyPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.scanner != widget.scanner) {
      oldWidget.scanner.removeListener(_onChanged);
      _scanner = widget.scanner;
      _scanner.addListener(_onChanged);
    }
    if (oldWidget.sessionId != widget.sessionId ||
        oldWidget.minGapMs != widget.minGapMs) {
      _scanner.start(sessionId: widget.sessionId, minGapMs: widget.minGapMs);
    }
  }

  @override
  void dispose() {
    _scanner.removeListener(_onChanged);
    _scanner.stop();
    super.dispose();
  }

  bool _reportedVerified = false;

  void _onChanged() {
    if (!mounted) return;
    setState(() {});
    if (_scanner.result?.verified == true && !_reportedVerified) {
      _reportedVerified = true;
      widget.onVerified?.call();
    }
    if (_scanner.result?.verified != true) _reportedVerified = false;
  }

  @override
  Widget build(BuildContext context) {
    final status = _scanner;
    final last = status.last;
    final result = status.result;
    final active = status.active;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        if (!status.permissionsGranted) ...<Widget>[
          const NoticeBox('Bluetooth permission is needed.'),
          const SizedBox(height: 8),
          SecondaryButton(
            label: 'Grant permission',
            onPressed: status.requestPermissions,
          ),
        ] else if (!status.bluetoothOn) ...<Widget>[
          const NoticeBox(
            'Bluetooth is turned off. Turn it on to verify attendance.',
            tone: Tone.red,
          ),
        ],
        if (status.message != null)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Text(
              status.message!,
              style: const TextStyle(color: AppColors.muted, fontSize: 14, height: 1.35),
            ),
          ),
        Padding(
          padding: const EdgeInsets.only(top: 6),
          child: Text(
            active
                ? (status.scanning ? 'Scanning for the classroom node…' : 'Starting scan…')
                : widget.idleLabel,
            style: const TextStyle(color: AppColors.muted, fontSize: 14),
          ),
        ),
        if (last != null)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
              'Node ${last.nodeId} detected · signal ${last.rssi} dBm',
              style: const TextStyle(fontSize: 14, color: AppColors.textDark),
            ),
          ),
        if (status.error != null) ...<Widget>[
          const SizedBox(height: 8),
          NoticeBox(status.error!, tone: Tone.red),
        ],
        if (result != null) ...<Widget>[
          const SizedBox(height: 8),
          NoticeBox(
            result.verified ? '${result.message} ✓' : result.message,
            tone: result.verified ? Tone.green : Tone.yellow,
          ),
        ],
      ],
    );
  }
}
