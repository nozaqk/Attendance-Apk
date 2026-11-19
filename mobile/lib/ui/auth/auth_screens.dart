import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/api_client.dart';
import '../../core/json.dart';
import '../../core/models.dart';
import '../theme.dart';
import '../widgets.dart';

/// Frame shared by every auth screen: brand header, one white form card,
/// 20 gutters and a single vertical rhythm so nothing looks hand-placed.
class AuthShell extends StatelessWidget {
  const AuthShell({super.key, required this.title, this.subtitle, required this.child});

  final String title;
  final String? subtitle;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.appBg,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 36, 20, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              BrandHeader(title: title, subtitle: subtitle),
              const SizedBox(height: 22),
              AppCard(
                contentPadding: const EdgeInsets.all(20),
                spacing: 14,
                child: child,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class LecturerLoginScreen extends StatefulWidget {
  const LecturerLoginScreen({
    super.key,
    required this.api,
    required this.onSignedIn,
    required this.onForgot,
    required this.onBack,
  });

  final ApiClient api;
  final Future<void> Function(String email, String password) onSignedIn;
  final VoidCallback onForgot;
  final VoidCallback onBack;

  @override
  State<LecturerLoginScreen> createState() => _LecturerLoginScreenState();
}

class _LecturerLoginScreenState extends State<LecturerLoginScreen> {
  String _email = '';
  String _password = '';
  bool _busy = false;
  String? _error;

  Future<void> _submit() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.onSignedIn(_email.trim(), _password);
    } catch (e) {
      if (mounted) setState(() => _error = _message(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthShell(
      title: 'Lecturer portal',
      subtitle: 'Your account is created by the administrator',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          AuthField(
            value: _email,
            onChanged: (v) => _email = v,
            label: 'Email',
            keyboardType: TextInputType.emailAddress,
          ),
          const SizedBox(height: 14),
          AuthField(
            value: _password,
            onChanged: (v) => _password = v,
            label: 'Password',
            isPassword: true,
          ),
          if (_error != null) ...<Widget>[
            const SizedBox(height: 14),
            ErrorBox(_error!),
          ],
          const SizedBox(height: 14),
          PrimaryButton(label: 'Sign in', busy: _busy, onPressed: _submit),
          LinkText('Forgot password?', onPressed: widget.onForgot),
          const SizedBox(height: 4),
          const DividerLine(),
          const SizedBox(height: 14),
          SecondaryButton(label: 'Back', onPressed: widget.onBack),
        ],
      ),
    );
  }
}

class StudentAuthScreen extends StatefulWidget {
  const StudentAuthScreen({
    super.key,
    required this.api,
    required this.onSignedIn,
    required this.onForgot,
    required this.onBack,
  });

  final ApiClient api;
  final Future<void> Function(String email, String password) onSignedIn;
  final VoidCallback onForgot;
  final VoidCallback onBack;

  @override
  State<StudentAuthScreen> createState() => _StudentAuthScreenState();
}

class _StudentAuthScreenState extends State<StudentAuthScreen> {
  bool _signUp = false;
  String _email = '';
  String _password = '';
  String _password2 = '';
  String _firstName = '';
  String _secondName = '';
  String _studentId = '';
  String _departmentId = '';
  String _stream = 'REGULAR';
  List<Department> _departments = <Department>[];
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadDepartments();
  }

  Future<void> _loadDepartments() async {
    try {
      final res = await widget.api.get('/api/public/departments');
      final list = Department.listFrom(res['items']);
      if (mounted) setState(() => _departments = list);
    } catch (_) {
      // The form still works; the department hint explains the missing list.
    }
  }

  Future<void> _signIn() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.onSignedIn(_email, _password);
    } catch (e) {
      if (mounted) setState(() => _error = _message(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _register() async {
    if (_password != _password2) {
      setState(() => _error = 'The two passwords do not match');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final res = await widget.api.post('/api/auth/register/student', <String, dynamic>{
        'firstName': _firstName,
        'secondName': _secondName,
        'studentId': _studentId,
        'email': _email,
        'departmentId': _departmentId,
        'stream': _stream,
        'password': _password,
      });
      final token = str(res['token']);
      if (token.isEmpty) throw Exception('Registration did not return a session.');
      await widget.onSignedIn(_email, _password);
    } catch (e) {
      if (mounted) setState(() => _error = _message(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthShell(
      title: _signUp ? 'Create your student account' : 'Student portal',
      subtitle: _signUp
          ? 'Register once, then sign in with the same email'
          : 'Sign in to verify attendance and follow your classes',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: _signUp
            ? <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(
                      child: AuthField(
                        value: _firstName,
                        onChanged: (v) => _firstName = v,
                        label: 'First name',
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: AuthField(
                        value: _secondName,
                        onChanged: (v) => _secondName = v,
                        label: 'Second name',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                AuthField(
                  value: _studentId,
                  onChanged: (v) => _studentId = v,
                  label: 'Index number',
                ),
                const SizedBox(height: 14),
                AuthField(
                  value: _email,
                  onChanged: (v) => _email = v,
                  label: 'Email',
                  keyboardType: TextInputType.emailAddress,
                ),
                const SizedBox(height: 14),
                PickerField(
                  label: 'Department',
                  value: _departmentName(),
                  onTap: _pickDepartment,
                ),
                if (_departments.isEmpty) ...<Widget>[
                  const SizedBox(height: 10),
                  const NoticeBox(
                    'No departments have been set up yet. Ask the administrator.',
                    tone: Tone.grey,
                  ),
                ],
                const SizedBox(height: 14),
                PickerField(
                  label: 'Stream',
                  value: _streamLabel(_stream),
                  onTap: _pickStream,
                ),
                const SizedBox(height: 14),
                AuthField(
                  value: _password,
                  onChanged: (v) => _password = v,
                  label: 'Password',
                  isPassword: true,
                  supporting: 'At least 8 characters, with a letter and a digit',
                ),
                const SizedBox(height: 14),
                AuthField(
                  value: _password2,
                  onChanged: (v) => _password2 = v,
                  label: 'Confirm password',
                  isPassword: true,
                ),
                if (_error != null) ...<Widget>[
                  const SizedBox(height: 14),
                  ErrorBox(_error!),
                ],
                const SizedBox(height: 14),
                PrimaryButton(label: 'Create account', busy: _busy, onPressed: _register),
                LinkText(
                  'Already registered? Sign in',
                  align: TextAlign.center,
                  onPressed: () => setState(() {
                    _signUp = false;
                    _error = null;
                  }),
                ),
                const DividerLine(),
                const SizedBox(height: 14),
                SecondaryButton(label: 'Back', onPressed: widget.onBack),
              ]
            : <Widget>[
                AuthField(
                  value: _email,
                  onChanged: (v) => _email = v,
                  label: 'Email',
                  keyboardType: TextInputType.emailAddress,
                ),
                const SizedBox(height: 14),
                AuthField(
                  value: _password,
                  onChanged: (v) => _password = v,
                  label: 'Password',
                  isPassword: true,
                ),
                if (_error != null) ...<Widget>[
                  const SizedBox(height: 14),
                  ErrorBox(_error!),
                ],
                const SizedBox(height: 14),
                PrimaryButton(label: 'Sign in', busy: _busy, onPressed: _signIn),
                LinkText('Forgot password?', onPressed: widget.onForgot),
                const SizedBox(height: 4),
                const DividerLine(),
                const SizedBox(height: 6),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: <Widget>[
                    const Text(
                      'New student?',
                      style: TextStyle(color: AppColors.muted, fontSize: 14),
                    ),
                    LinkText(
                      'Create an account',
                      onPressed: () => setState(() {
                        _signUp = true;
                        _error = null;
                      }),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                SecondaryButton(label: 'Back', onPressed: widget.onBack),
              ],
      ),
    );
  }

  String _departmentName() {
    for (final Department d in _departments) {
      if (d.id == _departmentId) return d.name;
    }
    return 'Select department';
  }

  String _streamLabel(String value) {
    const labels = <String, String>{
      'REGULAR': 'Regular',
      'WEEKLY': 'Weekly',
      'EVENING': 'Evening',
    };
    return labels[value] ?? value;
  }

  Future<void> _pickDepartment() async {
    if (_departments.isEmpty) return;
    final chosen = await showPicker<String>(
      context: context,
      title: 'Choose department',
      options: _departments.map((d) => MapEntry<String, String>(d.id, d.name)).toList(),
    );
    if (chosen != null && mounted) setState(() => _departmentId = chosen);
  }

  Future<void> _pickStream() async {
    final chosen = await showPicker<String>(
      context: context,
      title: 'Choose stream',
      options: const <MapEntry<String, String>>[
        MapEntry<String, String>('REGULAR', 'Regular'),
        MapEntry<String, String>('WEEKLY', 'Weekly'),
        MapEntry<String, String>('EVENING', 'Evening'),
      ],
    );
    if (chosen != null && mounted) setState(() => _stream = chosen);
  }
}

class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key, required this.api, required this.onBackToSignIn});

  final ApiClient api;
  final VoidCallback onBackToSignIn;

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  String _step = 'email';
  String _email = '';
  String _code = '';
  String _password = '';
  String _password2 = '';
  bool _busy = false;
  bool _done = false;
  String? _error;
  String? _info;

  Future<void> _sendCode() async {
    setState(() {
      _busy = true;
      _error = null;
      _info = null;
    });
    try {
      await widget.api.post('/api/auth/forgot-password', <String, dynamic>{'email': _email});
      if (!mounted) return;
      setState(() {
        _step = 'code';
        _info =
            'If that email has an account, a 6-digit code has been sent. Check your inbox (and spam).';
      });
    } catch (e) {
      if (mounted) setState(() => _error = _message(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _reset() async {
    if (_password != _password2) {
      setState(() => _error = 'The two passwords do not match');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.api.post('/api/auth/reset-password', <String, dynamic>{
        'email': _email,
        'code': _code,
        'newPassword': _password,
      });
      if (!mounted) return;
      setState(() {
        _done = true;
        _busy = false;
      });
      await Future<void>.delayed(const Duration(milliseconds: 1800));
      if (mounted) widget.onBackToSignIn();
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = _message(e);
          _busy = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthShell(
      title: _step == 'email' ? 'Reset your password' : 'Enter the code',
      subtitle: _step == 'email'
          ? 'We will email you a 6-digit code'
          : 'Use the code from the email to choose a new password',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          if (_done)
            const NoticeBox('Password changed. Taking you to sign in…', tone: Tone.green)
          else if (_step == 'email') ...<Widget>[
            const Text(
              'Enter your account email and we will send you a 6-digit code.',
              style: TextStyle(color: AppColors.muted, fontSize: 14, height: 1.35),
            ),
            const SizedBox(height: 14),
            AuthField(
              value: _email,
              onChanged: (v) => _email = v,
              label: 'Email',
              keyboardType: TextInputType.emailAddress,
            ),
            if (_error != null) ...<Widget>[
              const SizedBox(height: 14),
              ErrorBox(_error!),
            ],
            const SizedBox(height: 14),
            PrimaryButton(label: 'Send code', busy: _busy, onPressed: _sendCode),
          ] else ...<Widget>[
            if (_info != null) NoticeBox(_info!, tone: Tone.green),
            const SizedBox(height: 14),
            AuthField(
              value: _code,
              onChanged: (v) {
                final digits = v.replaceAll(RegExp(r'[^0-9]'), '');
                setState(() => _code = digits.length > 6 ? digits.substring(0, 6) : digits);
              },
              label: '6-digit code',
              keyboardType: TextInputType.number,
              inputFormatters: <TextInputFormatter>[FilteringTextInputFormatter.digitsOnly],
            ),
            const SizedBox(height: 14),
            AuthField(
              value: _password,
              onChanged: (v) => _password = v,
              label: 'New password',
              isPassword: true,
              supporting: 'At least 8 characters, with a letter and a digit',
            ),
            const SizedBox(height: 14),
            AuthField(
              value: _password2,
              onChanged: (v) => _password2 = v,
              label: 'Repeat new password',
              isPassword: true,
            ),
            if (_error != null) ...<Widget>[
              const SizedBox(height: 14),
              ErrorBox(_error!),
            ],
            const SizedBox(height: 14),
            PrimaryButton(label: 'Change password', busy: _busy, onPressed: _reset),
            LinkText('No code? Send a new code', onPressed: _busy ? null : _sendCode),
          ],
          const SizedBox(height: 4),
          const DividerLine(),
          const SizedBox(height: 6),
          LinkText(
            'Back to sign in',
            align: TextAlign.center,
            onPressed: widget.onBackToSignIn,
          ),
        ],
      ),
    );
  }
}

class ForcePasswordChangeScreen extends StatefulWidget {
  const ForcePasswordChangeScreen({
    super.key,
    required this.api,
    required this.user,
    required this.onDone,
    required this.onSignOut,
  });

  final ApiClient api;
  final User user;
  final VoidCallback onDone;
  final VoidCallback onSignOut;

  @override
  State<ForcePasswordChangeScreen> createState() => _ForcePasswordChangeScreenState();
}

class _ForcePasswordChangeScreenState extends State<ForcePasswordChangeScreen> {
  String _current = '';
  String _password = '';
  String _password2 = '';
  bool _busy = false;
  String? _error;

  Future<void> _save() async {
    if (_password != _password2) {
      setState(() => _error = 'The two passwords do not match');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.api.post('/api/auth/change-password', <String, dynamic>{
        'currentPassword': _current,
        'newPassword': _password,
      });
      if (mounted) widget.onDone();
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = _message(e);
          _busy = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthShell(
      title: 'Choose your own password',
      subtitle: '${widget.user.fullName} · ${widget.user.email}',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          const NoticeBox(
            'You signed in with a temporary password. Set a new one to continue. Nobody else will know it.',
          ),
          const SizedBox(height: 14),
          AuthField(
            value: _current,
            onChanged: (v) => _current = v,
            label: 'Temporary password',
            isPassword: true,
          ),
          const SizedBox(height: 14),
          AuthField(
            value: _password,
            onChanged: (v) => _password = v,
            label: 'New password',
            isPassword: true,
            supporting: 'At least 8 characters, with a letter and a digit',
          ),
          const SizedBox(height: 14),
          AuthField(
            value: _password2,
            onChanged: (v) => _password2 = v,
            label: 'Repeat new password',
            isPassword: true,
          ),
          if (_error != null) ...<Widget>[
            const SizedBox(height: 14),
            ErrorBox(_error!),
          ],
          const SizedBox(height: 14),
          PrimaryButton(label: 'Save and continue', busy: _busy, onPressed: _save),
          const SizedBox(height: 4),
          const DividerLine(),
          const SizedBox(height: 6),
          LinkText('Sign out', align: TextAlign.center, onPressed: widget.onSignOut),
        ],
      ),
    );
  }
}

String _message(Object error) {
  if (error is ApiException) return error.message;
  final text = '$error';
  return text.startsWith('Exception: ') ? text.substring(11) : text;
}
