package com.foldkb.deck;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

/**
 * 全局输入路由：键盘面板 -> IME InputConnection（优先）/ 无障碍 ACTION_SET_TEXT（回退）。
 * 鼠标手势统一走无障碍 dispatchGesture。
 */
public final class InputRouter {

    public interface TextSink {
        /** 提交一段普通文本 */
        void commitText(String text);
        /** 特殊键（回车/退格/方向/Tab…） */
        void sendSpecial(int code);
        /** 组合键，如 Ctrl+C。mods 见 KeyboardView */
        void sendCombo(int code, int mods);
        boolean isActive();
    }

    public interface MouseSink {
        void moveCursor(float dx, float dy, float sensitivity);
        void click(int button, boolean down);
        void scroll(float dx, float dy);
        void drag(float dx, float dy);
        boolean isActive();
    }

    // ime 优先（100% 兼容），accessibility 兜底
    private static volatile TextSink imeSink;
    private static volatile TextSink a11ySink;
    private static volatile MouseSink mouseSink;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private InputRouter() {}

    // 鼠标总开关（由 DeckActivity 的鼠标按钮切换）
    private static volatile boolean mouseEnabled = true;
    public static void setMouseEnabled(boolean on) { mouseEnabled = on; }
    public static boolean isMouseEnabled() { return mouseEnabled; }

    /** kind: "ime" 或 "a11y" */
    public static void setTextSink(String kind, TextSink s) {
        if ("ime".equals(kind)) imeSink = s; else a11ySink = s;
    }
    public static void setMouseSink(MouseSink s) { mouseSink = s; }

    private static TextSink pick() {
        TextSink i = imeSink;
        if (i != null && i.isActive()) return i;
        TextSink a = a11ySink;
        if (a != null && a.isActive()) return a;
        return null;
    }

    /** 调试：返回当前打字通道名 */
    public static String channelName() {
        TextSink i = imeSink;
        if (i != null && i.isActive()) return "ime";
        TextSink a = a11ySink;
        if (a != null && a.isActive()) return "a11y";
        return "none";
    }

    public static boolean textReady() { return pick() != null; }

    public static boolean mouseReady() {
        MouseSink s = mouseSink;
        return s != null && s.isActive();
    }

    /** 普通字符 — 若无聚焦输入框，先自动聚焦 */
    public static void commitText(String t) {
        tryAutoFocus();
        TextSink s = pick();
        if (s != null) s.commitText(t);
    }

    /** 特殊键：KeyEvent.KEYCODE_* 或自定义码 */
    public static void sendSpecial(int code) {
        tryAutoFocus();
        TextSink s = pick();
        if (s != null) s.sendSpecial(code);
    }

    public static void sendCombo(int code, int mods) {
        tryAutoFocus();
        TextSink s = pick();
        if (s != null) s.sendCombo(code, mods);
    }

    /** 若 IME 已激活但无 InputConnection（输入框未聚焦），自动聚焦上半屏输入框。
     *  加冷却：聚焦成功后 2s 内不再重复聚焦，避免每次按键都跨窗口扫树拖慢打字。 */
    private static long lastAutoFocus = 0;
    private static void tryAutoFocus() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastAutoFocus < 2000) return;   // 冷却中，跳过
        GhostImeService ime = GhostImeService.get();
        if (ime == null) return;
        if (ime.getCurrentInputConnection() != null) return; // 已有连接，无需聚焦
        DeckAccessibilityService a11y = DeckAccessibilityService.get();
        if (a11y != null) {
            a11y.focusNearestEdit();
            lastAutoFocus = now;
        }
    }

    public static void mouseMove(float dx, float dy, float sens) {
        if (!mouseEnabled) return;
        MouseSink s = mouseSink;
        if (s != null && s.isActive()) s.moveCursor(dx, dy, sens);
    }

    public static void mouseClick(int button, boolean down) {
        if (!mouseEnabled) return;
        MouseSink s = mouseSink;
        if (s != null && s.isActive()) s.click(button, down);
    }

    public static void mouseScroll(float dx, float dy) {
        if (!mouseEnabled) return;
        MouseSink s = mouseSink;
        if (s != null && s.isActive()) s.scroll(dx, dy);
    }

    public static void mouseDrag(float dx, float dy) {
        if (!mouseEnabled) return;
        MouseSink s = mouseSink;
        if (s != null && s.isActive()) s.drag(dx, dy);
    }

    public static void runOnMain(Runnable r) { MAIN.post(r); }
}
