import 'package:flutter/material.dart';

/// The web palette (`src/shared/styles.css`) reused so the Flutter build looks
/// exactly like the web app and like the previous native build.
class AppColors {
  const AppColors._();

  static const Color navy = Color(0xFF12263F);
  static const Color navy2 = Color(0xFF1D3A5F);
  static const Color appBg = Color(0xFFF3F5F8);
  static const Color cardWhite = Color(0xFFFFFFFF);
  static const Color textDark = Color(0xFF1B2430);
  static const Color muted = Color(0xFF5D6B7C);
  static const Color line = Color(0xFFDDE3EA);
  static const Color green = Color(0xFF1A7F4B);
  static const Color greenBg = Color(0xFFE3F4EA);
  static const Color yellow = Color(0xFF8A5A00);
  static const Color yellowBg = Color(0xFFFFF2CC);
  static const Color red = Color(0xFFB42318);
  static const Color redBg = Color(0xFFFDE7E4);
  static const Color blue = Color(0xFF1D4ED8);
  static const Color blueBg = Color(0xFFE5EDFF);
  static const Color greyBg = Color(0xFFE9EDF2);
  static const Color selectedTile = Color(0xFFF1F5FB);
  static const Color navyDisabled = Color(0xFF9AA7B5);
}

ThemeData buildAppTheme() {
  final base = ThemeData(
    useMaterial3: true,
    brightness: Brightness.light,
    colorScheme: const ColorScheme.light(
      primary: AppColors.navy,
      onPrimary: Colors.white,
      secondary: AppColors.navy2,
      onSecondary: Colors.white,
      surface: AppColors.cardWhite,
      onSurface: AppColors.textDark,
      error: AppColors.red,
      outline: AppColors.line,
    ),
    scaffoldBackgroundColor: AppColors.appBg,
    dividerColor: AppColors.line,
  );

  return base.copyWith(
    textTheme: base.textTheme.apply(
      bodyColor: AppColors.textDark,
      displayColor: AppColors.textDark,
      fontSizeFactor: 1.0,
    ),
    snackBarTheme: const SnackBarThemeData(
      backgroundColor: AppColors.navy,
      contentTextStyle: TextStyle(color: Colors.white, fontSize: 14),
      behavior: SnackBarBehavior.floating,
    ),
  );
}
