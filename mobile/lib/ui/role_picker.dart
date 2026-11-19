import 'package:flutter/material.dart';

import 'theme.dart';
import 'widgets.dart';

enum Role { lecturer, student }

/// App entry: one card per portal, same rhythm as the sign-in screens so the
/// whole auth flow feels like one product.
class RolePicker extends StatelessWidget {
  const RolePicker({super.key, required this.onSelect});

  final ValueChanged<Role> onSelect;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.appBg,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 48, 20, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              const BrandHeader(
                title: 'Choose your role',
                subtitle: 'Every role keeps its own login and data',
              ),
              const SizedBox(height: 22),
              _RoleCard(
                monogram: 'L',
                title: 'Lecturer',
                description: 'Start a class, verify attendance and review sessions',
                onTap: () => onSelect(Role.lecturer),
              ),
              const SizedBox(height: 14),
              _RoleCard(
                monogram: 'S',
                title: 'Student',
                description: 'Check in to a live class and track your attendance',
                onTap: () => onSelect(Role.student),
              ),
              const SizedBox(height: 22),
              const Text(
                'Sign in with your existing account. The administrator creates lecturer accounts.',
                textAlign: TextAlign.center,
                style: TextStyle(color: AppColors.muted, fontSize: 13, height: 1.4),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RoleCard extends StatelessWidget {
  const _RoleCard({
    required this.monogram,
    required this.title,
    required this.description,
    required this.onTap,
  });

  final String monogram;
  final String title;
  final String description;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.cardWhite,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.line),
          ),
          child: Row(
            children: <Widget>[
              Container(
                width: 44,
                height: 44,
                decoration: const BoxDecoration(
                  color: AppColors.blueBg,
                  shape: BoxShape.circle,
                ),
                alignment: Alignment.center,
                child: Text(
                  monogram,
                  style: const TextStyle(
                    color: AppColors.navy,
                    fontSize: 17,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      title,
                      style: const TextStyle(
                        color: AppColors.textDark,
                        fontSize: 17,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      description,
                      style: const TextStyle(color: AppColors.muted, fontSize: 13, height: 1.35),
                    ),
                  ],
                ),
              ),
              const Icon(Icons.keyboard_arrow_right, color: AppColors.muted),
            ],
          ),
        ),
      ),
    );
  }
}
