package com.foldkb.deck;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 全局设置：按键声音 / 震动 / 主题色 / 背光。
 * 用 SharedPreferences 持久化，静态方法读写。
 */
public final class DeckSettings {

    private static final String PREFS = "deck_settings";

    // 键
    private static final String K_SOUND_ON = "sound_on";
    private static final String K_SOUND_TYPE = "sound_type";
    private static final String K_VIBRATE_ON = "vibrate_on";
    private static final String K_THEME = "theme";
    private static final String K_BACKLIGHT = "backlight";
    private static final String K_BACKLIGHT_MODE = "backlight_mode";
    private static final String K_MOUSE_ON = "mouse_on";

    // 音色类型
    public static final int SOUND_MECHANICAL = 0;  // 机械青轴 click
    public static final int SOUND_THOCK = 1;       // 低沉 thock
    public static final int SOUND_SOFT = 2;        // 柔和
    public static final int SOUND_OFF = 3;         // 静音

    // 主题
    public static final int THEME_SILVER = 0;   // 银灰铝合金（默认）
    public static final int THEME_DARK = 1;     // 深空灰
    public static final int THEME_SPACE = 2;    // 太空黑
    public static final int THEME_BLUE = 3;     // 蓝色
    public static final int THEME_PURPLE = 4;   // 紫色

    // 背光效果模式
    public static final int BACKLIGHT_BREATH = 0;  // 呼吸（整体明暗）
    public static final int BACKLIGHT_SCAN = 1;    // 扫描（光带左右扫过）
    public static final int BACKLIGHT_KEYPRESS = 2;// 按键亮（按下发光渐灭）

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- 声音 ----
    public static boolean soundOn(Context c) {
        return soundType(c) != SOUND_OFF;
    }
    public static int soundType(Context c) {
        return sp(c).getInt(K_SOUND_TYPE, SOUND_MECHANICAL);
    }
    public static void setSoundType(Context c, int type) {
        sp(c).edit().putInt(K_SOUND_TYPE, type).apply();
    }

    // ---- 震动 ----
    public static boolean vibrateOn(Context c) {
        return sp(c).getBoolean(K_VIBRATE_ON, true);
    }
    public static void setVibrateOn(Context c, boolean on) {
        sp(c).edit().putBoolean(K_VIBRATE_ON, on).apply();
    }

    // ---- 主题色 ----
    public static int theme(Context c) {
        return sp(c).getInt(K_THEME, THEME_SILVER);
    }
    public static void setTheme(Context c, int t) {
        sp(c).edit().putInt(K_THEME, t).apply();
    }

    // ---- 背光 ----
    public static boolean backlightOn(Context c) {
        return sp(c).getBoolean(K_BACKLIGHT, false);
    }
    public static void setBacklightOn(Context c, boolean on) {
        sp(c).edit().putBoolean(K_BACKLIGHT, on).apply();
    }

    // ---- 背光效果模式 ----
    public static int backlightMode(Context c) {
        return sp(c).getInt(K_BACKLIGHT_MODE, BACKLIGHT_BREATH);
    }
    public static void setBacklightMode(Context c, int mode) {
        sp(c).edit().putInt(K_BACKLIGHT_MODE, mode).apply();
    }

    // ---- 鼠标开关 ----
    public static boolean mouseOn(Context c) {
        return sp(c).getBoolean(K_MOUSE_ON, true);
    }
    public static void setMouseOn(Context c, boolean on) {
        sp(c).edit().putBoolean(K_MOUSE_ON, on).apply();
    }
}
