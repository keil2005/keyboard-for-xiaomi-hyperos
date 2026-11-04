package com.foldkb.deck;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;

import java.lang.ref.WeakReference;

/**
 * 注入通道 B：零高度隐形输入法。
 * 关键作用（修订后）：
 *  1. 抢走输入焦点，阻止系统键盘弹出遮挡面板。
 *  2. 通过 InputConnection.commitText 打字（100% 兼容）。
 *  3. 【不再】报告半屏高度 / 强制 resize —— 那是上半屏黑屏的元凶：
 *     分屏下半屏已被 DeckActivity 占用，IME 再报半屏高度会二次压缩上半屏，
 *     触发微信 Activity 重建 → 叠加无障碍自动聚焦 → 死循环黑屏。
 *     现改为零高度 + 永不强制显示，上半屏由系统分屏自行锁定。
 */
public class GhostImeService extends InputMethodService implements InputRouter.TextSink {

    private static WeakReference<GhostImeService> ref = new WeakReference<>(null);

    public static GhostImeService get() { return ref.get(); }

    @Override
    public void onCreate() {
        super.onCreate();
        ref = new WeakReference<>(this);
        InputRouter.setTextSink("ime", this);
        // 不再 requestShowSelf —— 避免强制显示 resize 上半屏
    }

    @Override
    public void onDestroy() {
        if (ref.get() == this) ref = new WeakReference<>(null);
        super.onDestroy();
    }

    /** 零高度输入视图：不占空间、不逼系统 resize 上半屏 */
    @Override
    public View onCreateInputView() {
        return null;
    }

    @Override
    public View onCreateCandidatesView() { return null; }

    /** 永不显示输入视图 —— 系统不会为 IME 预留空间，上半屏保持锁定 */
    @Override
    public boolean onEvaluateInputViewShown() { return false; }

    @Override
    public boolean onEvaluateFullscreenMode() { return false; }

    /** 输入结束不强制重显 */
    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
    }

    // ---------------- TextSink ----------------

    private InputConnection ic() { return getCurrentInputConnection(); }

    @Override
    public boolean isActive() { return ic() != null; }

    @Override
    public void commitText(final String text) {
        InputConnection c = ic();
        if (c != null) c.commitText(text, 1);
    }

    @Override
    public void sendSpecial(final int code) {
        InputConnection c = ic();
        if (c == null) return;
        switch (code) {
            case KeyEventCodes.ENTER:     c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                                          c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)); break;
            case KeyEventCodes.BACKSPACE: c.deleteSurroundingText(1, 0); break;
            case KeyEventCodes.TAB:       commitSpecial(c, KeyEvent.KEYCODE_TAB); break;
            case KeyEventCodes.SPACE:     c.commitText(" ", 1); break;
            case KeyEventCodes.ESCAPE:    commitSpecial(c, KeyEvent.KEYCODE_ESCAPE); break;
            case KeyEventCodes.ARROW_LEFT:  commitSpecial(c, KeyEvent.KEYCODE_DPAD_LEFT); break;
            case KeyEventCodes.ARROW_RIGHT: commitSpecial(c, KeyEvent.KEYCODE_DPAD_RIGHT); break;
            case KeyEventCodes.ARROW_UP:    commitSpecial(c, KeyEvent.KEYCODE_DPAD_UP); break;
            case KeyEventCodes.ARROW_DOWN:  commitSpecial(c, KeyEvent.KEYCODE_DPAD_DOWN); break;
            case KeyEventCodes.HOME:        commitSpecial(c, KeyEvent.KEYCODE_MOVE_HOME); break;
            case KeyEventCodes.END:         commitSpecial(c, KeyEvent.KEYCODE_MOVE_END); break;
            default: break;
        }
    }

    private void commitSpecial(InputConnection c, int keyCode) {
        c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        c.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    @Override
    public void sendCombo(final int code, final int mods) {
        InputConnection c = ic();
        if (c == null) return;
        int meta = 0;
        if ((mods & KeyEventCodes.MOD_CTRL) != 0)  meta |= KeyEvent.META_CTRL_ON;
        if ((mods & KeyEventCodes.MOD_SHIFT) != 0) meta |= KeyEvent.META_SHIFT_ON;
        if ((mods & KeyEventCodes.MOD_ALT) != 0)   meta |= KeyEvent.META_ALT_ON;
        if ((mods & KeyEventCodes.MOD_META) != 0)  meta |= KeyEvent.META_META_ON;

        int keyCode = mapComboKeyCode(code);
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return;
        long now = android.os.SystemClock.uptimeMillis();
        c.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
        c.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta));
    }

    private int mapComboKeyCode(int code) {
        switch (code) {
            case KeyEventCodes.KEY_A: return KeyEvent.KEYCODE_A;
            case KeyEventCodes.KEY_C: return KeyEvent.KEYCODE_C;
            case KeyEventCodes.KEY_V: return KeyEvent.KEYCODE_V;
            case KeyEventCodes.KEY_X: return KeyEvent.KEYCODE_X;
            case KeyEventCodes.KEY_Z: return KeyEvent.KEYCODE_Z;
            default: return KeyEvent.KEYCODE_UNKNOWN;
        }
    }
}
