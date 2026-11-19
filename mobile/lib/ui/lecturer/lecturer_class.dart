import 'package:flutter/material.dart';

import '../../ble/ble_controller.dart';
import '../../ble/ble_verify_panel.dart';
import '../../core/api_client.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/polling.dart';
import '../theme.dart';
import '../widgets.dart';
import 'lecturer_common.dart';

class Finished {
  Finished(this.session, this.present, this.absent, this.total);

  final CurrentSession session;
  final int present;
  final int absent;
  final int total;
}

/// The Class tab: choose course + room, verify presence by Bluetooth, run the
/// live attendance list, then close the class and release the room.
class LecturerClassScreen extends StatefulWidget {
  const LecturerClassScreen({super.key, required this.api, required this.scanner});

  final ApiClient api;
  final BleController scanner;

  @override
  State<LecturerClassScreen> createState() => _LecturerClassScreenState();
}

class _LecturerClassScreenState extends State<LecturerClassScreen> {
  CurrentSession? _current;
  bool _loaded = false;
  String? _error;
  Finished? _finished;
  bool _stopped = false;

  @override
  void initState() {
    super.initState();
    _loop();
  }

  @override
  void dispose() {
    _stopped = true;
    super.dispose();
  }

  Future<void> _loop() async {
    while (!_stopped) {
      await _loadCurrent();
      if (_stopped) return;
      await Future<void>.delayed(const Duration(seconds: 5));
    }
  }

  Future<void> _loadCurrent() async {
    try {
      final res = await widget.api.get('/api/lecturer/current');
      final raw = res['session'];
      if (_stopped || !mounted) return;
      setState(() {
        _current = raw is Map ? CurrentSession.fromJson(Map<String, dynamic>.from(raw)) : null;
        _error = null;
        _loaded = true;
      });
    } catch (e) {
      if (_stopped || !mounted) return;
      setState(() {
        if (!_loaded) _error = errorText(e);
        _loaded = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final finished = _finished;
    if (finished != null) {
      return ScreenBody(
        children: <Widget>[
          const ScreenTitle('Class finished'),
          AppCard(
            title: '${finished.session.courseCode} · ${finished.session.courseName}',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  '${finished.session.classroomName} · room released',
                  style: const TextStyle(color: AppColors.muted, fontSize: 13),
                ),
                const SizedBox(height: 8),
                CountsHead(
                  total: finished.total,
                  present: finished.present,
                  absent: finished.absent,
                ),
              ],
            ),
          ),
          EditWindow(
            locked: finished.session.locked,
            editableUntil: finished.session.editableUntil,
          ),
          Text(
            'Open the class from Home to add students manually until '
            '${fmtStamp(finished.session.editableUntil)}.',
            style: const TextStyle(color: AppColors.muted, fontSize: 13),
          ),
          PrimaryButton(
            label: 'Done',
            onPressed: () => setState(() => _finished = null),
          ),
        ],
      );
    }

    if (!_loaded) {
      return const ScreenBody(children: <Widget>[ScreenTitle('Class'), LoadingRow()]);
    }
    if (_error != null && _current == null) {
      return ScreenBody(
        children: <Widget>[
          const ScreenTitle('Class'),
          ErrorBox(_error!, onRetry: _loadCurrent),
        ],
      );
    }

    final session = _current;
    if (session == null) {
      return ScreenBody(
        children: <Widget>[
          const ScreenTitle('Class'),
          _ClassSelect(api: widget.api, onCreated: _loadCurrent),
        ],
      );
    }
    if (session.status == 'SCHEDULED') {
      return ScreenBody(
        children: <Widget>[
          const ScreenTitle('Class'),
          _ClassVerify(
            api: widget.api,
            session: session,
            scanner: widget.scanner,
            onReload: _loadCurrent,
          ),
        ],
      );
    }
    return ScreenBody(
      children: <Widget>[
        const ScreenTitle('Class'),
        _ClassLive(
          api: widget.api,
          session: session,
          scanner: widget.scanner,
          onEnded: (Finished finished) => setState(() => _finished = finished),
        ),
      ],
    );
  }
}

class _ClassSelect extends StatefulWidget {
  const _ClassSelect({required this.api, required this.onCreated});

  final ApiClient api;
  final Future<void> Function() onCreated;

  @override
  State<_ClassSelect> createState() => _ClassSelectState();
}

class _ClassSelectState extends State<_ClassSelect> {
  String _courseId = '';
  String _roomId = '';
  bool _busy = false;
  String? _error;

  Future<void> _create() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.api.post('/api/lecturer/classes', <String, dynamic>{
        'courseId': _courseId,
        'classroomId': _roomId,
      });
      await widget.onCreated();
    } catch (e) {
      if (mounted) setState(() => _error = errorText(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        AppCard(
          title: '1. Choose your course',
          child: Poller<List<Course>>(
            fetch: () async {
              final res = await widget.api.get('/api/lecturer/courses');
              return Course.listFrom(res['items']);
            },
            builder: (BuildContext context, List<Course> courses, Future<void> Function() reload) {
              if (courses.isEmpty) {
                return const EmptyBox(
                  'No courses assigned',
                  hint: 'Ask the administrator to assign a course to you.',
                );
              }
              return Column(
                children: <Widget>[
                  for (final Course course in courses)
                    _RoomButton(
                      label: course.label,
                      selected: _courseId == course.id,
                      onTap: () => setState(() => _courseId = course.id),
                    ),
                ],
              );
            },
          ),
        ),
        const SizedBox(height: 14),
        AppCard(
          title: '2. Choose the classroom',
          child: Poller<List<Room>>(
            interval: const Duration(seconds: 10),
            fetch: () async {
              final res = await widget.api.get('/api/lecturer/classrooms');
              return Room.listFrom(res['items']);
            },
            builder: (BuildContext context, List<Room> rooms, Future<void> Function() reload) {
              if (rooms.isEmpty) return const EmptyBox('No classrooms registered');
              return Column(
                children: <Widget>[
                  for (final Room room in rooms)
                    _RoomTile(
                      room: room,
                      selected: _roomId == room.id,
                      onTap: () => setState(() => _roomId = room.id),
                    ),
                ],
              );
            },
          ),
        ),
        if (_error != null) ...<Widget>[
          const SizedBox(height: 14),
          NoticeBox(_error!, tone: Tone.red),
        ],
        const SizedBox(height: 14),
        PrimaryButton(
          label: 'Select course and room',
          busy: _busy,
          enabled: _courseId.isNotEmpty && _roomId.isNotEmpty,
          onPressed: _create,
        ),
      ],
    );
  }
}

class _RoomButton extends StatelessWidget {
  const _RoomButton({
    required this.label,
    required this.selected,
    required this.onTap,
    this.badge,
    this.sub,
    this.extra,
    this.extraColor = AppColors.red,
    this.enabled = true,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;
  final Badge? badge;
  final String? sub;
  final String? extra;
  final Color extraColor;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: InkWell(
        onTap: enabled ? onTap : null,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: selected ? AppColors.selectedTile : AppColors.cardWhite,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: selected ? AppColors.navy : AppColors.line),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Row(
                children: <Widget>[
                  Expanded(
                    child: Text(
                      label,
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                        color: enabled ? AppColors.textDark : AppColors.muted,
                      ),
                    ),
                  ),
                  if (badge != null) badge!,
                ],
              ),
              if (sub != null && sub!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    sub!,
                    style: const TextStyle(color: AppColors.muted, fontSize: 13),
                  ),
                ),
              if (extra != null)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    extra!,
                    style: TextStyle(color: extraColor, fontSize: 13),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RoomTile extends StatelessWidget {
  const _RoomTile({required this.room, required this.selected, required this.onTap});

  final Room room;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final taken = room.taken;
    final noNode = !room.hasNode;
    final Badge badge;
    if (taken) {
      badge = const Badge('TAKEN', tone: Tone.red);
    } else if (noNode) {
      badge = const Badge('NO NODE', tone: Tone.red);
    } else if (room.nodeOnline) {
      badge = const Badge('NODE ONLINE', tone: Tone.green);
    } else {
      badge = const Badge('NODE OFFLINE', tone: Tone.yellow);
    }
    final String? extra;
    if (taken) {
      extra = 'Selected by ${room.occupiedByLecturer ?? 'another lecturer'} '
          'lecturing ${room.occupiedByCourse}';
    } else if (noNode) {
      extra = 'No Bluetooth node: attendance cannot be verified here.';
    } else if (!room.nodeOnline) {
      extra = 'The node has not reported recently. Check it is powered.';
    } else {
      extra = null;
    }
    return _RoomButton(
      label: room.name,
      selected: selected,
      enabled: !taken && !noNode,
      badge: badge,
      sub: room.subline,
      extra: extra,
      extraColor: (taken || noNode) ? AppColors.red : AppColors.yellow,
      onTap: onTap,
    );
  }
}

class _ClassVerify extends StatefulWidget {
  const _ClassVerify({
    required this.api,
    required this.session,
    required this.scanner,
    required this.onReload,
  });

  final ApiClient api;
  final CurrentSession session;
  final BleController scanner;
  final Future<void> Function() onReload;

  @override
  State<_ClassVerify> createState() => _ClassVerifyState();
}

class _ClassVerifyState extends State<_ClassVerify> {
  bool _verified = false;
  bool _busy = false;
  String? _error;

  bool get _ok => _verified || widget.session.lecturerVerified;

  Future<void> _start() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.api.post('/api/lecturer/sessions/${widget.session.id}/start');
      await widget.onReload();
    } catch (e) {
      if (mounted) setState(() => _error = errorText(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _cancel() async {
    final ok = await confirmDialog(
      context,
      title: 'Cancel selection?',
      message: 'This releases ${widget.session.classroomName} so another lecturer can use it.',
      confirmLabel: 'Cancel selection',
      danger: true,
    );
    if (!ok) return;
    try {
      await widget.api.post('/api/lecturer/sessions/${widget.session.id}/cancel');
      await widget.onReload();
    } catch (_) {
      // the next poll reconciles the state
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        AppCard(
          title: '${widget.session.courseCode} · ${widget.session.courseName}',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(
                '${widget.session.classroomName}'
                '${widget.session.nodeId == null ? '' : ' · node ${widget.session.nodeId}'}',
                style: const TextStyle(color: AppColors.muted, fontSize: 13),
              ),
              const SizedBox(height: 6),
              const NoticeBox(
                'This room is reserved for you. Others cannot use it until you press Class over.',
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        AppCard(
          title: 'Verify you are in the room',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              BleVerifyPanel(
                scanner: widget.scanner,
                sessionId: widget.session.id,
                minGapMs: 5000,
                idleLabel: 'Waiting…',
                onVerified: () => setState(() => _verified = true),
              ),
              if (_ok) ...<Widget>[
                const SizedBox(height: 10),
                const BigStatus('LECTURER VERIFIED ✓'),
              ],
            ],
          ),
        ),
        if (_error != null) ...<Widget>[
          const SizedBox(height: 14),
          NoticeBox(_error!, tone: Tone.red),
        ],
        const SizedBox(height: 14),
        PrimaryButton(label: 'Start class', enabled: _ok, busy: _busy, onPressed: _start),
        const SizedBox(height: 10),
        SecondaryButton(label: 'Cancel selection', onPressed: _cancel),
      ],
    );
  }
}

class _ClassLive extends StatefulWidget {
  const _ClassLive({
    required this.api,
    required this.session,
    required this.scanner,
    required this.onEnded,
  });

  final ApiClient api;
  final CurrentSession session;
  final BleController scanner;
  final ValueChanged<Finished> onEnded;

  @override
  State<_ClassLive> createState() => _ClassLiveState();
}

class _ClassLiveState extends State<_ClassLive> {
  List<RosterItem> _items = <RosterItem>[];
  bool _ended = false;
  bool _busy = false;
  String? _error;

  Future<void> _addManual() async {
    await showDialog<void>(
      context: context,
      builder: (BuildContext ctx) => ManualAddDialog(
        api: widget.api,
        sessionId: widget.session.id,
      ),
    );
  }

  Future<void> _end() async {
    final ok = await confirmDialog(
      context,
      title: 'Class over?',
      message: 'Everyone not detected will be marked ABSENT, the room is released, and you '
          'will have 6 hours to correct attendance manually. Continue?',
      confirmLabel: 'Class over',
      danger: true,
    );
    if (!ok) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final res = await widget.api.post('/api/lecturer/sessions/${widget.session.id}/end');
      final sessionRaw = res['session'];
      final ended = sessionRaw is Map
          ? CurrentSession.fromJson(Map<String, dynamic>.from(sessionRaw))
          : widget.session;
      var items = <RosterItem>[];
      try {
        final finalRes =
            await widget.api.get('/api/lecturer/sessions/${widget.session.id}/attendance');
        items = RosterItem.listFrom(finalRes['items']);
      } catch (_) {
        items = _items;
      }
      final present = items.where((RosterItem i) => i.present).length;
      widget.onEnded(Finished(ended, present, items.length - present, items.length));
    } catch (e) {
      if (mounted) setState(() => _error = errorText(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        AppCard(
          title: '${widget.session.courseCode} · ${widget.session.courseName}',
          actions: const Badge('IN PROGRESS', tone: Tone.green),
          child: Poller<List<RosterItem>>(
            interval: const Duration(seconds: 5),
            fetch: () async {
              final res = await widget.api
                  .get('/api/lecturer/sessions/${widget.session.id}/attendance');
              final items = RosterItem.listFrom(res['items']);
              if (!_ended && mounted) {
                // Keep the local copy for the "class over" summary.
                _items = items;
              }
              return items;
            },
            builder: (BuildContext context, List<RosterItem> items, Future<void> Function() reload) {
              final present = items.where((RosterItem i) => i.present).length;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    widget.session.classroomName,
                    style: const TextStyle(color: AppColors.muted, fontSize: 13),
                  ),
                  const SizedBox(height: 8),
                  CountsHead(total: items.length, present: present, absent: items.length - present),
                ],
              );
            },
          ),
        ),
        const SizedBox(height: 14),
        AppCard(
          title: 'Your presence',
          child: BleVerifyPanel(
            scanner: widget.scanner,
            sessionId: widget.session.id,
            minGapMs: 60000,
            idleLabel: 'Waiting…',
          ),
        ),
        const SizedBox(height: 14),
        AppCard(
          title: 'Live attendance',
          child: Poller<List<RosterItem>>(
            interval: const Duration(seconds: 5),
            fetch: () async {
              final res = await widget.api
                  .get('/api/lecturer/sessions/${widget.session.id}/attendance');
              return RosterItem.listFrom(res['items']);
            },
            builder: (BuildContext context, List<RosterItem> items, Future<void> Function() reload) {
              _items = items;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  if (items.isEmpty)
                    const EmptyBox('No students on this course yet')
                  else
                    for (final RosterItem item in items) RosterRow(item: item, live: true),
                  const Text(
                    'Updates every 5 seconds.',
                    style: TextStyle(color: AppColors.muted, fontSize: 12),
                  ),
                ],
              );
            },
          ),
        ),
        const SizedBox(height: 14),
        SecondaryButton(label: '+ Add student manually', onPressed: _addManual),
        const SizedBox(height: 14),
        const NoticeBox('The room stays reserved for you until you press Class over.'),
        if (_error != null) ...<Widget>[
          const SizedBox(height: 14),
          NoticeBox(_error!, tone: Tone.red),
        ],
        const SizedBox(height: 14),
        DangerButton(label: 'Class over', busy: _busy, onPressed: _end),
      ],
    );
  }
}
