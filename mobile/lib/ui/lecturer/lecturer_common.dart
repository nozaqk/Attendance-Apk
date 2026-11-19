import 'package:flutter/material.dart';

import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/polling.dart';
import '../theme.dart';
import '../widgets.dart';

/// One roster line: mark, name, index number, detection details and status badges.
class RosterRow extends StatelessWidget {
  const RosterRow({super.key, required this.item, this.live = false});

  final RosterItem item;
  final bool live;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          MarkCircle(present: item.present),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  item.fullName,
                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                ),
                Text(
                  '${item.studentId}${item.department == null ? '' : ' · ${item.department}'}',
                  style: const TextStyle(color: AppColors.muted, fontSize: 12),
                ),
                if (live && item.firstDetectedAt != null)
                  Text(
                    'First ${fmtTime(item.firstDetectedAt)} · Last ${fmtTime(item.lastDetectedAt)}'
                    ' · ${item.observationCount} obs'
                    '${item.lastRssi == null ? '' : ' · ${item.lastRssi} dBm'}',
                    style: const TextStyle(color: AppColors.muted, fontSize: 12),
                  ),
                if (item.note != null)
                  Text(
                    'Note: ${item.note}',
                    style: const TextStyle(color: AppColors.muted, fontSize: 12),
                  ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: <Widget>[
              StatusPill(item.status),
              if (item.method == 'BLE') ...<Widget>[
                const SizedBox(height: 4),
                const StatusPill('Bluetooth verified', tone: Tone.green),
              ],
              if (item.method == 'LECTURER_MANUAL') ...<Widget>[
                const SizedBox(height: 4),
                const StatusPill('LECTURER_MANUAL', tone: Tone.yellow),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

/// Yellow/red strip explaining whether attendance can still be corrected.
class EditWindow extends StatelessWidget {
  const EditWindow({super.key, required this.locked, required this.editableUntil});

  final bool locked;
  final String? editableUntil;

  @override
  Widget build(BuildContext context) {
    if (locked) return const NoticeBox('ATTENDANCE LOCKED', tone: Tone.red);
    if (editableUntil != null) {
      return NoticeBox(
        'Attendance editing closes: ${fmtStamp(editableUntil)}',
        tone: Tone.yellow,
      );
    }
    return const SizedBox.shrink();
  }
}

/// Manual attendance for a student without a phone or with a flat battery.
class ManualAddDialog extends StatefulWidget {
  const ManualAddDialog({super.key, required this.api, required this.sessionId, this.onDone});

  final ApiClient api;
  final String sessionId;
  final VoidCallback? onDone;

  @override
  State<ManualAddDialog> createState() => _ManualAddDialogState();
}

class _ManualAddDialogState extends State<ManualAddDialog> {
  String _studentId = '';
  String _note = '';
  bool _busy = false;
  String? _error;
  String? _ok;

  Future<void> _save() async {
    setState(() {
      _busy = true;
      _error = null;
      _ok = null;
    });
    try {
      final body = <String, dynamic>{
        'sessionId': widget.sessionId,
        'studentId': _studentId.trim(),
      };
      if (_note.trim().isNotEmpty) body['note'] = _note.trim();
      final res = await widget.api.post('/api/lecturer/attendance/manual', body);
      final record = res['record'] is Map
          ? Map<String, dynamic>.from(res['record'] as Map)
          : <String, dynamic>{};
      setState(() {
        _ok = '${record['fullName']} (${record['studentId']}) recorded as MANUAL present.';
        _studentId = '';
        _note = '';
      });
      widget.onDone?.call();
    } catch (e) {
      setState(() => _error = errorText(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text(
        'Manual attendance',
        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
      ),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            const Text(
              'For a student without a phone, with a flat battery or who forgot it. '
              'Recorded as LECTURER_MANUAL, separate from Bluetooth-verified attendance.',
              style: TextStyle(color: AppColors.muted, fontSize: 13, height: 1.4),
            ),
            const SizedBox(height: 12),
            AuthField(
              value: _studentId,
              onChanged: (v) => _studentId = v,
              label: 'Student index number',
            ),
            const SizedBox(height: 12),
            AuthField(
              value: _note,
              onChanged: (v) => _note = v,
              label: 'Note (optional)',
            ),
            if (_error != null) ...<Widget>[
              const SizedBox(height: 12),
              ErrorBox(_error!),
            ],
            if (_ok != null) ...<Widget>[
              const SizedBox(height: 12),
              NoticeBox(_ok!, tone: Tone.green),
            ],
          ],
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Close', style: TextStyle(color: AppColors.textDark)),
        ),
        TextButton(
          onPressed: _busy ? null : _save,
          child: const Text(
            'Mark present manually',
            style: TextStyle(color: AppColors.navy, fontWeight: FontWeight.bold),
          ),
        ),
      ],
    );
  }
}
