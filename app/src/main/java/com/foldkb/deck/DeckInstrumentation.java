package com.foldkb.deck;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * 遥控测试通道：通过 am instrument 调用，绕过 shell 的 INJECT_EVENTS 限制。
 * Instrumentation（UiAutomation）是系统认可的注入源，可对任意界面点击/滑动/按键。
 *
 * 用法：
 *   adb shell am instrument -w -e click 300,500 com.foldkb.deck/.DeckInstrumentation
 *   adb shell am instrument -w -e swipe 100,800,600,800,300 com.foldkb.deck/.DeckInstrumentation
 *   adb shell am instrument -w -e key 66 com.foldkb.deck/.DeckInstrumentation        (ENTER)
 *   adb shell am instrument -w -e text abc com.foldkb.deck/.DeckInstrumentation
 */
public class DeckInstrumentation extends Instrumentation {

    private static final String TAG = "DeckInstr";
    private Bundle args;

    @Override
    public void onCreate(Bundle arguments) {
        this.args = arguments;
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        try {
            UiAutomation ua = getUiAutomation();
            if (ua == null) {
                Log.e(TAG, "UiAutomation null");
                finish(Activity.RESULT_CANCELED, bundle("error", "no uiautomation"));
                return;
            }
            String click = args != null ? args.getString("click") : null;
            String swipe = args != null ? args.getString("swipe") : null;
            String key = args != null ? args.getString("key") : null;
            String text = args != null ? args.getString("text") : null;

            if (click != null) {
                String[] p = click.split(",");
                float x = Float.parseFloat(p[0].trim());
                float y = Float.parseFloat(p[1].trim());
                tap(ua, x, y);
                Log.i(TAG, "clicked " + x + "," + y);
                finish(Activity.RESULT_OK, bundle("result", "clicked " + x + "," + y));
            } else if (swipe != null) {
                String[] p = swipe.split(",");
                float x1 = Float.parseFloat(p[0].trim());
                float y1 = Float.parseFloat(p[1].trim());
                float x2 = Float.parseFloat(p[2].trim());
                float y2 = Float.parseFloat(p[3].trim());
                int dur = p.length > 4 ? Integer.parseInt(p[4].trim()) : 300;
                swipe(ua, x1, y1, x2, y2, dur);
                Log.i(TAG, "swiped " + swipe);
                finish(Activity.RESULT_OK, bundle("result", "swiped"));
            } else if (key != null) {
                int code = Integer.parseInt(key.trim());
                keyPress(ua, code);
                Log.i(TAG, "key " + code);
                finish(Activity.RESULT_OK, bundle("result", "key " + code));
            } else if (text != null) {
                typeText(ua, text);
                Log.i(TAG, "typed " + text);
                finish(Activity.RESULT_OK, bundle("result", "typed"));
            } else {
                finish(Activity.RESULT_OK, bundle("result", "noop"));
            }
        } catch (Exception e) {
            Log.e(TAG, "fail", e);
            finish(Activity.RESULT_CANCELED, bundle("error", e.toString()));
        }
    }

    private Bundle bundle(String k, String v) {
        Bundle b = new Bundle();
        b.putString(k, v);
        return b;
    }

    private void tap(UiAutomation ua, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        ua.injectInputEvent(down, true);
        MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        ua.injectInputEvent(up, true);
        down.recycle();
        up.recycle();
    }

    private void swipe(UiAutomation ua, float x1, float y1, float x2, float y2, int dur) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x1, y1, 0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        ua.injectInputEvent(down, true);
        int steps = Math.max(dur / 16, 2);
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float x = x1 + (x2 - x1) * t;
            float y = y1 + (y2 - y1) * t;
            MotionEvent move = MotionEvent.obtain(now, now + (dur * i / steps),
                    MotionEvent.ACTION_MOVE, x, y, 0);
            move.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            ua.injectInputEvent(move, true);
            move.recycle();
            SystemClock.sleep(16);
        }
        MotionEvent up = MotionEvent.obtain(now, now + dur, MotionEvent.ACTION_UP, x2, y2, 0);
        up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        ua.injectInputEvent(up, true);
        down.recycle();
        up.recycle();
    }

    private void keyPress(UiAutomation ua, int code) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0);
        ua.injectInputEvent(down, true);
        KeyEvent up = new KeyEvent(now, now + 50, KeyEvent.ACTION_UP, code, 0);
        ua.injectInputEvent(up, true);
    }

    /** ASCII 文本 → 逐字符键事件（够测试用） */
    private void typeText(UiAutomation ua, String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int code = charToKeycode(ch);
            if (code >= 0) {
                long now = SystemClock.uptimeMillis();
                KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0);
                ua.injectInputEvent(down, true);
                KeyEvent up = new KeyEvent(now, now + 40, KeyEvent.ACTION_UP, code, 0);
                ua.injectInputEvent(up, true);
                SystemClock.sleep(30);
            }
        }
    }

    private int charToKeycode(char ch) {
        if (ch >= 'a' && ch <= 'z') return KeyEvent.KEYCODE_A + (ch - 'a');
        if (ch >= 'A' && ch <= 'Z') return KeyEvent.KEYCODE_A + (ch - 'A');
        if (ch >= '0' && ch <= '9') return KeyEvent.KEYCODE_0 + (ch - '0');
        if (ch == ' ') return KeyEvent.KEYCODE_SPACE;
        if (ch == '\n') return KeyEvent.KEYCODE_ENTER;
        return -1;
    }
}
