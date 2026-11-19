import 'package:flutter/material.dart';

import 'theme.dart';
import 'widgets.dart';

enum Role { lecturer, student }

/// App entry: choose one of the two secure portals.
class RolePicker extends StatelessWidget {
  const RolePicker({super.key, required this.onSelect});

  final ValueChanged<Role> onSelect;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.appBg,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (BuildContext context, BoxConstraints constraints) =>
              SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 36, 20, 28),
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minHeight: constraints.maxHeight > 64 ? constraints.maxHeight - 64 : 0,
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  const BrandHeader(
                    title: 'Attendance, made simple',
                    subtitle: 'Choose how you want to continue',
                  ),
                  const SizedBox(height: 28),
                  _RoleCard(
                    icon: Icons.cast_for_education_rounded,
                    title: 'Lecturer',
                    description: 'Start a class, verify attendance and review sessions.',
                    action: 'Open lecturer portal',
                    onTap: () => onSelect(Role.lecturer),
                  ),
                  const SizedBox(height: 14),
                  _RoleCard(
                    icon: Icons.school_rounded,
                    title: 'Student',
                    description: 'Check in to a live class and track your attendance.',
                    action: 'Open student portal',
                    onTap: () => onSelect(Role.student),
                  ),
                  const SizedBox(height: 24),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                    decoration: BoxDecoration(
                      color: AppColors.blueBgSoft,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: const Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Icon(Icons.verified_user_outlined, size: 19, color: AppColors.navy2),
                        SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            'Each portal has its own secure sign-in and keeps your account data separate.',
                            style: TextStyle(color: AppColors.navy2, fontSize: 13, height: 1.4),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Lecturer accounts are created by your administrator.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: AppColors.muted, fontSize: 12, height: 1.4),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _RoleCard extends StatelessWidget {
  const _RoleCard({
    required this.icon,
    required this.title,
    required this.description,
    required this.action,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String description;
  final String action;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.cardWhite,
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(18),
        child: Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: AppColors.line),
            boxShadow: const <BoxShadow>[
              BoxShadow(color: AppColors.shadow, blurRadius: 18, offset: Offset(0, 5)),
            ],
          ),
          child: Row(
            children: <Widget>[
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  color: AppColors.blueBg,
                  borderRadius: BorderRadius.circular(16),
                ),
                alignment: Alignment.center,
                child: Icon(icon, color: AppColors.navy, size: 27),
              ),
              const SizedBox(width: 15),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      title,
                      style: const TextStyle(
                        color: AppColors.textDark,
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      description,
                      style: const TextStyle(color: AppColors.muted, fontSize: 13, height: 1.4),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      action,
                      style: const TextStyle(
                        color: AppColors.blue,
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              const Icon(Icons.arrow_forward_rounded, color: AppColors.navy2, size: 20),
            ],
          ),
        ),
      ),
    );
  }
}
