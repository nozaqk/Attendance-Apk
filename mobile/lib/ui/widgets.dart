import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'theme.dart';

/// Status tones shared by every badge/notice in the app.
enum Tone { green, yellow, red, grey, blue }

const Map<String, Tone> _statusTone = <String, Tone>{
  'PRESENT': Tone.green,
  'VERIFIED': Tone.green,
  'ONLINE': Tone.green,
  'ACTIVE': Tone.green,
  'BLE': Tone.green,
  'LATE': Tone.yellow,
  'MANUAL': Tone.yellow,
  'LECTURER_MANUAL': Tone.yellow,
  'SCHEDULED': Tone.yellow,
  'PENDING': Tone.yellow,
  'ABSENT': Tone.red,
  'OFFLINE': Tone.red,
  'ERROR': Tone.red,
  'LOCKED': Tone.red,
  'CANCELLED': Tone.red,
  'ENDED': Tone.grey,
  'NONE': Tone.grey,
};

Tone toneFor(String status, [Tone? explicit]) =>
    explicit ?? _statusTone[status] ?? Tone.grey;

Color toneForeground(Tone tone) {
  switch (tone) {
    case Tone.green:
      return AppColors.green;
    case Tone.yellow:
      return AppColors.yellow;
    case Tone.red:
      return AppColors.red;
    case Tone.blue:
      return AppColors.blue;
    case Tone.grey:
      return AppColors.muted;
  }
}

Color toneBackground(Tone tone) {
  switch (tone) {
    case Tone.green:
      return AppColors.greenBg;
    case Tone.yellow:
      return AppColors.yellowBg;
    case Tone.red:
      return AppColors.redBg;
    case Tone.blue:
      return AppColors.blueBg;
    case Tone.grey:
      return AppColors.greyBg;
  }
}

class Badge extends StatelessWidget {
  const Badge(this.text, {super.key, this.tone});

  final String text;
  final Tone? tone;

  @override
  Widget build(BuildContext context) {
    final resolved = toneFor(text, tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: toneBackground(resolved),
        borderRadius: BorderRadius.circular(50),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: toneForeground(resolved),
          fontSize: 12,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}

/// The white card used by every screen: 12 radius, hairline border.
class AppCard extends StatelessWidget {
  const AppCard({
    super.key,
    this.title,
    this.actions,
    this.contentPadding = const EdgeInsets.all(16),
    this.spacing = 8,
    required this.child,
  });

  final String? title;
  final Widget? actions;
  final EdgeInsets contentPadding;
  final double spacing;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: AppColors.cardWhite,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.line),
      ),
      padding: contentPadding,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          if (title != null || actions != null) ...<Widget>[
            Row(
              children: <Widget>[
                if (title != null)
                  Expanded(
                    child: Text(
                      title!,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: AppColors.textDark,
                      ),
                    ),
                  ),
                if (actions != null) actions!,
              ],
            ),
            SizedBox(height: spacing),
          ],
          child,
        ],
      ),
    );
  }
}

class PrimaryButton extends StatelessWidget {
  const PrimaryButton({
    super.key,
    required this.label,
    this.onPressed,
    this.busy = false,
    this.enabled = true,
    this.width,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool busy;
  final bool enabled;
  final double? width;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width ?? double.infinity,
      height: 48,
      child: FilledButton(
        onPressed: enabled && !busy ? onPressed : null,
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.navy,
          disabledBackgroundColor: AppColors.navyDisabled,
          foregroundColor: Colors.white,
          disabledForegroundColor: Colors.white70,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ),
        child: Text(
          busy ? 'Please wait…' : label,
          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
        ),
      ),
    );
  }
}

class SecondaryButton extends StatelessWidget {
  const SecondaryButton({
    super.key,
    required this.label,
    this.onPressed,
    this.busy = false,
    this.enabled = true,
    this.width,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool busy;
  final bool enabled;
  final double? width;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width ?? double.infinity,
      height: 48,
      child: OutlinedButton(
        onPressed: enabled && !busy ? onPressed : null,
        style: OutlinedButton.styleFrom(
          side: const BorderSide(color: AppColors.line),
          backgroundColor: AppColors.cardWhite,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ),
        child: Text(
          busy ? 'Please wait…' : label,
          style: const TextStyle(
            color: AppColors.textDark,
            fontWeight: FontWeight.w600,
            fontSize: 15,
          ),
        ),
      ),
    );
  }
}

class DangerButton extends StatelessWidget {
  const DangerButton({
    super.key,
    required this.label,
    this.onPressed,
    this.busy = false,
    this.enabled = true,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool busy;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 48,
      child: FilledButton(
        onPressed: enabled && !busy ? onPressed : null,
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.red,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        ),
        child: Text(
          busy ? 'Please wait…' : label,
          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
        ),
      ),
    );
  }
}

class LinkText extends StatelessWidget {
  const LinkText(this.label, {super.key, this.onPressed, this.align});

  final String label;
  final VoidCallback? onPressed;
  final TextAlign? align;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      onPressed: onPressed,
      style: TextButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 4),
        minimumSize: const Size(0, 32),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
      ),
      child: Text(
        label,
        textAlign: align,
        style: const TextStyle(
          color: AppColors.blue,
          fontSize: 14,
          fontWeight: FontWeight.w500,
        ),
      ),
    );
  }
}

/// Compact underlined link (the "Try again" style used by ErrorBox).
class UnderlineLink extends StatelessWidget {
  const UnderlineLink(this.label, {super.key, this.onPressed});

  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      onPressed: onPressed,
      style: TextButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 2),
        minimumSize: const Size(0, 28),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
      ),
      child: Text(
        label,
        style: const TextStyle(
          color: AppColors.blue,
          fontSize: 14,
          decoration: TextDecoration.underline,
        ),
      ),
    );
  }
}

class NoticeBox extends StatelessWidget {
  const NoticeBox(this.text, {super.key, this.tone = Tone.yellow});

  final String text;
  final Tone tone;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: toneBackground(tone),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Text(
        text,
        style: TextStyle(color: toneForeground(tone), fontSize: 14, height: 1.35),
      ),
    );
  }
}

class ErrorBox extends StatelessWidget {
  const ErrorBox(this.message, {super.key, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        NoticeBox('Something went wrong. $message', tone: Tone.red),
        if (onRetry != null) UnderlineLink('Try again', onPressed: onRetry),
      ],
    );
  }
}

class EmptyBox extends StatelessWidget {
  const EmptyBox(this.title, {super.key, this.hint});

  final String title;
  final String? hint;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: <Widget>[
          Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
          ),
          if (hint != null) ...<Widget>[
            const SizedBox(height: 4),
            Text(
              hint!,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted, fontSize: 14, height: 1.35),
            ),
          ],
        ],
      ),
    );
  }
}

class LoadingRow extends StatelessWidget {
  const LoadingRow({super.key, this.label = 'Loading…'});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 14),
      child: Row(
        children: <Widget>[
          const SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.navy),
          ),
          const SizedBox(width: 8),
          Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 14)),
        ],
      ),
    );
  }
}

class MarkCircle extends StatelessWidget {
  const MarkCircle({super.key, required this.present});

  final bool present;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 30,
      height: 30,
      decoration: BoxDecoration(
        color: present ? AppColors.greenBg : AppColors.redBg,
        shape: BoxShape.circle,
      ),
      alignment: Alignment.center,
      child: Text(
        present ? '✓' : '✗',
        style: TextStyle(
          color: present ? AppColors.green : AppColors.red,
          fontWeight: FontWeight.bold,
          fontSize: 14,
        ),
      ),
    );
  }
}

class DividerLine extends StatelessWidget {
  const DividerLine({super.key});

  @override
  Widget build(BuildContext context) =>
      Container(height: 1, color: AppColors.line, width: double.infinity);
}

/// Brand block: emblem, product name and the current portal/step.
class BrandHeader extends StatelessWidget {
  const BrandHeader({super.key, required this.title, this.subtitle});

  final String title;
  final String? subtitle;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: <Widget>[
        Image.asset(
          'assets/branding/smartiq_emblem.png',
          width: 70,
          height: 62,
          fit: BoxFit.contain,
        ),
        const SizedBox(height: 8),
        const Text(
          'SmartIQ',
          style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AppColors.navy),
        ),
        const SizedBox(height: 2),
        Text(
          title,
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            color: AppColors.muted,
          ),
        ),
        if (subtitle != null) ...<Widget>[
          const SizedBox(height: 4),
          Text(
            subtitle!,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 13, color: AppColors.muted, height: 1.35),
          ),
        ],
      ],
    );
  }
}

/// The single text field used by every form: 10 radius, optional reveal toggle.
class AuthField extends StatefulWidget {
  const AuthField({
    super.key,
    required this.value,
    required this.onChanged,
    required this.label,
    this.isPassword = false,
    this.keyboardType,
    this.supporting,
    this.enabled = true,
    this.inputFormatters,
    this.maxLines = 1,
  });

  final String value;
  final ValueChanged<String> onChanged;
  final String label;
  final bool isPassword;
  final TextInputType? keyboardType;
  final String? supporting;
  final bool enabled;
  final List<TextInputFormatter>? inputFormatters;
  final int maxLines;

  @override
  State<AuthField> createState() => _AuthFieldState();
}

class _AuthFieldState extends State<AuthField> {
  late final TextEditingController _controller =
      TextEditingController(text: widget.value);
  bool _revealed = false;

  @override
  void didUpdateWidget(covariant AuthField oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Keep the field in sync when the screen resets it (e.g. clearing the code).
    if (widget.value != _controller.text) {
      _controller.value = TextEditingValue(
        text: widget.value,
        selection: TextSelection.collapsed(offset: widget.value.length),
      );
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: _controller,
      onChanged: widget.onChanged,
      enabled: widget.enabled,
      obscureText: widget.isPassword && !_revealed,
      keyboardType: widget.isPassword ? TextInputType.text : widget.keyboardType,
      inputFormatters: widget.inputFormatters,
      maxLines: widget.maxLines,
      style: const TextStyle(fontSize: 15, color: AppColors.textDark),
      decoration: InputDecoration(
        labelText: widget.label,
        helperText: widget.supporting,
        helperMaxLines: 2,
        helperStyle: const TextStyle(fontSize: 12, color: AppColors.muted),
        labelStyle: const TextStyle(fontSize: 14, color: AppColors.muted),
        floatingLabelStyle: const TextStyle(fontSize: 13, color: AppColors.navy),
        filled: true,
        fillColor: AppColors.cardWhite,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: AppColors.line),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: AppColors.line),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: AppColors.navy, width: 1.4),
        ),
        disabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: AppColors.line),
        ),
        suffixIcon: widget.isPassword
            ? TextButton(
                onPressed: () => setState(() => _revealed = !_revealed),
                child: Text(
                  _revealed ? 'Hide' : 'Show',
                  style: const TextStyle(
                    color: AppColors.muted,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              )
            : null,
      ),
    );
  }
}

/// Read-only field that opens a dropdown (department / semester / stream / course).
class PickerField extends StatelessWidget {
  const PickerField({
    super.key,
    required this.label,
    required this.value,
    required this.onTap,
    this.enabled = true,
  });

  final String label;
  final String value;
  final VoidCallback onTap;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: enabled ? onTap : null,
      borderRadius: BorderRadius.circular(10),
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          enabled: enabled,
          suffixIcon: const Icon(Icons.keyboard_arrow_down, color: AppColors.muted),
        ),
        child: Text(
          value,
          style: const TextStyle(fontSize: 15, color: AppColors.textDark),
        ),
      ),
    );
  }
}

/// Shows a modal picker sheet and returns the chosen value.
Future<T?> showPicker<T>({
  required BuildContext context,
  required String title,
  required List<MapEntry<T, String>> options,
}) {
  return showModalBottomSheet<T>(
    context: context,
    backgroundColor: AppColors.cardWhite,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (BuildContext ctx) {
      return SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 18, 20, 6),
              child: Text(
                title,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
              ),
            ),
            Flexible(
              child: ListView(
                shrinkWrap: true,
                children: options
                    .map(
                      (MapEntry<T, String> entry) => ListTile(
                        title: Text(entry.value, style: const TextStyle(fontSize: 15)),
                        onTap: () => Navigator.of(ctx).pop(entry.key),
                      ),
                    )
                    .toList(),
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      );
    },
  );
}

Future<bool> confirmDialog(
  BuildContext context, {
  required String title,
  required String message,
  required String confirmLabel,
  bool danger = false,
}) async {
  final result = await showDialog<bool>(
    context: context,
    builder: (BuildContext ctx) => AlertDialog(
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17)),
      content: Text(message, style: const TextStyle(fontSize: 14, height: 1.4)),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(ctx).pop(false),
          child: const Text('Cancel', style: TextStyle(color: AppColors.textDark)),
        ),
        TextButton(
          onPressed: () => Navigator.of(ctx).pop(true),
          child: Text(
            confirmLabel,
            style: TextStyle(
              color: danger ? AppColors.red : AppColors.navy,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
      ],
    ),
  );
  return result ?? false;
}

/// Scrollable screen body with the app's 16 gutters and 14 rhythm.
class ScreenBody extends StatelessWidget {
  const ScreenBody({super.key, required this.children, this.padding});

  final List<Widget> children;
  final EdgeInsets? padding;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: padding ?? const EdgeInsets.all(16),
      children: <Widget>[
        for (int i = 0; i < children.length; i++) ...<Widget>[
          if (i > 0) const SizedBox(height: 14),
          children[i],
        ],
        const SizedBox(height: 24),
      ],
    );
  }
}

class ScreenTitle extends StatelessWidget {
  const ScreenTitle(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) => Text(
        text,
        style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
      );
}

/// Big centred status strip (verified / warning).
class BigStatus extends StatelessWidget {
  const BigStatus(this.text, {super.key, this.green = true});

  final String text;
  final bool green;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: green ? AppColors.greenBg : AppColors.yellowBg,
        borderRadius: BorderRadius.circular(12),
      ),
      alignment: Alignment.center,
      child: Text(
        text,
        style: TextStyle(
          color: green ? AppColors.green : AppColors.yellow,
          fontWeight: FontWeight.bold,
          fontSize: 16,
        ),
      ),
    );
  }
}

/// Total / present / absent strip.
class CountsHead extends StatelessWidget {
  const CountsHead({super.key, required this.total, required this.present, required this.absent});

  final int total;
  final int present;
  final int absent;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: <Widget>[
        _cell(total.toString(), 'Students', AppColors.muted),
        const SizedBox(width: 8),
        _cell(present.toString(), 'Present', AppColors.green),
        const SizedBox(width: 8),
        _cell(absent.toString(), 'Absent', AppColors.red),
      ],
    );
  }

  Widget _cell(String value, String label, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
        decoration: BoxDecoration(
          color: AppColors.cardWhite,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: AppColors.line),
        ),
        child: Column(
          children: <Widget>[
            Text(
              value,
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: color),
            ),
            Text(label, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
          ],
        ),
      ),
    );
  }
}

/// Bottom navigation bar: emoji icon + 11pt label, navy when selected.
class AppBottomBar extends StatelessWidget {
  const AppBottomBar({
    super.key,
    required this.items,
    required this.index,
    required this.onSelect,
  });

  final List<AppTab> items;
  final int index;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.cardWhite,
        border: Border(top: BorderSide(color: AppColors.line)),
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: <Widget>[
            for (int i = 0; i < items.length; i++)
              Expanded(
                child: InkWell(
                  onTap: () => onSelect(i),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: <Widget>[
                        Text(items[i].icon, style: const TextStyle(fontSize: 20)),
                        const SizedBox(height: 2),
                        Text(
                          items[i].label,
                          style: TextStyle(
                            fontSize: 11,
                            color: i == index ? AppColors.navy : AppColors.muted,
                            fontWeight:
                                i == index ? FontWeight.w600 : FontWeight.normal,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class AppTab {
  const AppTab(this.icon, this.label);

  final String icon;
  final String label;
}
