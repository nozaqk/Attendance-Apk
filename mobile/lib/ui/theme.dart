import 'package:flutter/material.dart';

/// SmartIQ's refined navy-and-blue design system. The palette stays true to the
/// original app while using clearer surfaces, softer borders and stronger
/// interaction states throughout the Flutter experience.
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
  static const Color lineSoft = Color(0xD1DDE3EA);
  static const Color blueBgSoft = Color(0x94E5EDFF);
  static const Color shadow = Color(0x1012263F);
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
    ).copyWith(
      headlineMedium: base.textTheme.headlineMedium?.copyWith(
        color: AppColors.textDark,
        fontSize: 24,
        fontWeight: FontWeight.w700,
        height: 1.2,
        letterSpacing: -0.4,
      ),
      titleLarge: base.textTheme.titleLarge?.copyWith(
        color: AppColors.textDark,
        fontSize: 18,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.2,
      ),
      bodyMedium: base.textTheme.bodyMedium?.copyWith(
        color: AppColors.textDark,
        fontSize: 14,
        height: 1.4,
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: AppColors.cardWhite,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      labelStyle: const TextStyle(color: AppColors.muted, fontSize: 14),
      floatingLabelStyle: const TextStyle(color: AppColors.navy, fontWeight: FontWeight.w600),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: AppColors.line),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: AppColors.navy, width: 1.5),
      ),
      errorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: AppColors.red),
      ),
      focusedErrorBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: AppColors.red, width: 1.5),
      ),
    ),
    snackBarTheme: const SnackBarThemeData(
      backgroundColor: AppColors.navy,
      contentTextStyle: TextStyle(color: Colors.white, fontSize: 14),
      behavior: SnackBarBehavior.floating,
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      color: AppColors.navy,
      linearTrackColor: AppColors.blueBg,
    ),
  );
}
