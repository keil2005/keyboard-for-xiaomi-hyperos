package com.foldkb.deck;

/** 特殊键码 + 修饰键位掩码（自定义空间，避开 KeyEvent.KEYCODE_* 冲突） */
public final class KeyEventCodes {
    private KeyEventCodes() {}

    public static final int ENTER    = 1001;
    public static final int BACKSPACE= 1002;
    public static final int TAB      = 1003;
    public static final int ESCAPE   = 1004;
    public static final int SPACE    = 1005;

    public static final int ARROW_LEFT  = 1101;
    public static final int ARROW_RIGHT = 1102;
    public static final int ARROW_UP    = 1103;
    public static final int ARROW_DOWN  = 1104;
    public static final int HOME        = 1105;
    public static final int END         = 1106;

    public static final int SHIFT = 1201;
    public static final int CTRL  = 1202;
    public static final int ALT   = 1203;
    public static final int META  = 1204;
    public static final int LANG  = 1301;   // 中/英输入切换（国行 Mac Caps Lock 位置）

    // 字母区（sendCombo 用）
    public static final int KEY_A = 2001;
    public static final int KEY_C = 2003;
    public static final int KEY_V = 2006;
    public static final int KEY_X = 2008;
    public static final int KEY_Z = 2009;

    // 修饰键掩码
    public static final int MOD_SHIFT = 1;
    public static final int MOD_CTRL  = 2;
    public static final int MOD_ALT   = 4;
    public static final int MOD_META  = 8;
}
