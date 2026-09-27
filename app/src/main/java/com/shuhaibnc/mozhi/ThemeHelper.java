package com.shuhaibnc.mozhi;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

/**
 * Material 3 dynamic color + light/dark theme handling.
 * On Android 12+ the accent follows the wallpaper (Material You) via the
 * runtime system_accent1_* colors; older versions use the static palette.
 */
public final class ThemeHelper {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    private static final String PREFS = "mozhi_prefs";
    private static final String KEY_THEME = "theme_mode";

    private ThemeHelper() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getThemeMode(Context c) {
        return prefs(c).getInt(KEY_THEME, MODE_SYSTEM);
    }

    public static void setThemeMode(Context c, int mode) {
        prefs(c).edit().putInt(KEY_THEME, mode).apply();
    }

    public static boolean isDark(Context c) {
        int mode = getThemeMode(c);
        if (mode == MODE_DARK) return true;
        if (mode == MODE_LIGHT) return false;
        int night = c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Must be called before super.onCreate(). */
    public static void applyTheme(Activity a) {
        a.setTheme(isDark(a) ? R.style.Theme_Mozhi_Dark : R.style.Theme_Mozhi);
    }

    /**
     * Wrap the base context so the resource night-mode follows the chosen
     * theme. Call from Activity.attachBaseContext().
     */
    public static Context wrapContext(Context base) {
        boolean dark = isDark(base);
        Configuration config = new Configuration(
                base.getResources().getConfiguration());
        int night = dark ? Configuration.UI_MODE_NIGHT_YES
                         : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(config);
    }

    /** Wallpaper-based accent on API 31+, static Material blue otherwise. */
    public static int accentColor(Context c) {
        boolean dark = isDark(c);
        if (Build.VERSION.SDK_INT >= 31) {
            String name = dark ? "system_accent1_200" : "system_accent1_600";
            int id = c.getResources().getIdentifier(name, "color", "android");
            if (id != 0) {
                try {
                    return c.getResources().getColor(id, c.getTheme());
                } catch (Exception ignored) {}
            }
        }
        return dark ? 0xFF8AB4F8 : 0xFF1A73E8;
    }

    /** Darken a color for the status bar. */
    public static int darker(int color) {
        float f = 0.78f;
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * f);
        int g = (int) (((color >> 8) & 0xFF) * f);
        int b = (int) ((color & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Tint status bar + navigation bar. */
    public static void styleChrome(Activity a) {
        if (Build.VERSION.SDK_INT >= 21) {
            boolean dark = isDark(a);
            a.getWindow().setStatusBarColor(
                    dark ? 0xFF000000 : 0xFFF0F2F8);
            if (Build.VERSION.SDK_INT >= 26) {
                a.getWindow().setNavigationBarColor(
                        dark ? 0xFF000000 : 0xFFF0F2F8);
            }
        }
    }
}
