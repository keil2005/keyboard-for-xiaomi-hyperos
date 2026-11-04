package com.foldkb.deck;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * 注入通道 A（主力）：
 *  文字  -> 聚焦输入框 ACTION_SET_TEXT / ACTION_SET_SELECTION
 *  鼠标  -> dispatchGesture 点按/拖拽/滚动
 *  组合键-> 剪贴板 + 节点动作（复制/粘贴/全选）
 */
public class DeckAccessibilityService extends AccessibilityService
        implements InputRouter.TextSink, InputRouter.MouseSink {

    private static DeckAccessibilityService instance;
    public static DeckAccessibilityService get() { return instance; }

    // 虚拟光标位置（屏幕绝对坐标，由鼠标注入维护）
    private float cursorX = -1, cursorY = -1;
    private static final float SWIPE = 180f; // 手势基准像素

    @Override
    protected void onServiceConnected() {
        instance = this;
        InputRouter.setTextSink("a11y", this);
        InputRouter.setMouseSink(this);
        // 关键：追加 FLAG_RETRIEVE_INTERACTIVE_WINDOWS，分屏下才能读到微信窗口
        try {
            android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                setServiceInfo(info);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        InputRouter.setTextSink("a11y", null);
        InputRouter.setMouseSink(null);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        // 不再自动 focusNearestEdit —— 那是黑屏死循环的元凶：
        // 聚焦 → IME resize → Activity 重建 → 窗口变化 → 再聚焦，无限循环。
        // 输入框聚焦改由用户主动操作（触控板点击 / 键盘按键时 tryAutoFocus 兜底）。
    }

    @Override
    public void onInterrupt() { }

    /** 上半屏应用包名（目标 App，默认微信） */
    private static final String SELF_PKG = "com.foldkb.deck";

    /**
     * 实时抓取「上半屏应用」输入框的文本 + 光标位置，供下半屏预览区镜像显示。
     * 返回 "文本\u0001光标位置"，无聚焦输入框时返回 null。
     * 注意：分屏下 getRootInActiveWindow() 常指向 FoldDeck 自己，
     *       必须遍历所有窗口，跳过 FoldDeck，找上半屏应用的输入框。
     */
    public String getFocusedText() {
        AccessibilityNodeInfo node = findEditInUpperApp();
        if (node == null) return null;
        try {
            CharSequence cur = node.getText();
            if (cur == null) cur = "";
            int sel = Math.max(node.getTextSelectionStart(), 0);
            return cur.toString() + "\u0001" + sel;
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---------------- TextSink ----------------

    @Override
    public boolean isActive() { return true; }

    /** 找到上半屏应用（非 FoldDeck）里聚焦的输入框 */
    private AccessibilityNodeInfo findFocused() {
        // 优先：上半屏应用窗口内的焦点
        AccessibilityNodeInfo node = findEditInUpperApp();
        if (node != null) return node;
        // 兜底：当前 active window 的焦点
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        return focused;
    }

    /**
     * 遍历所有无障碍窗口，跳过 FoldDeck 自身，定位上半屏应用的输入框。
     * 优先返回「已聚焦」的编辑框；否则返回第一个可编辑节点。
     */
    private AccessibilityNodeInfo findEditInUpperApp() {
        try {
            List<android.view.accessibility.AccessibilityWindowInfo> wins = getWindows();
            if (wins == null) return null;
            AccessibilityNodeInfo firstEdit = null;
            for (android.view.accessibility.AccessibilityWindowInfo w : wins) {
                if (w == null) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) continue;
                CharSequence pkg = root.getPackageName();
                if (pkg != null && SELF_PKG.contentEquals(pkg)) continue; // 跳过自己
                AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (f != null) return f;
                AccessibilityNodeInfo e = findFirstEditable(root);
                if (e != null && firstEdit == null) firstEdit = e;
            }
            return firstEdit;
        } catch (Exception e) {
            return null;
        }
    }

    private AccessibilityNodeInfo findFirstEditable(AccessibilityNodeInfo root) {
        java.util.LinkedList<AccessibilityNodeInfo> q = new java.util.LinkedList<>();
        q.add(root);
        int guard = 0;
        while (!q.isEmpty() && guard++ < 2000) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            if (n.isEditable()) return n;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) q.add(c);
            }
        }
        return null;
    }

    // 缓存最近一次找到的输入框节点，避免每次按键全树扫描（打字慢的主因）
    private AccessibilityNodeInfo cachedEdit = null;
    private long cachedAt = 0;
    private static final long CACHE_TTL = 5000; // 5s 内复用，超时重找

    /** 取输入框节点：优先复用缓存（refresh 有效则直接用），失效才全树重找 */
    private AccessibilityNodeInfo obtainEdit() {
        long now = SystemClock.uptimeMillis();
        if (cachedEdit != null && (now - cachedAt) < CACHE_TTL) {
            if (cachedEdit.refresh()) {
                return cachedEdit;   // 缓存仍有效，直接复用（省全树扫描）
            }
            cachedEdit = null;
        }
        AccessibilityNodeInfo n = findEditInUpperApp();
        cachedEdit = n;
        cachedAt = now;
        return n;
    }

    /** 节点失效时清除缓存 */
    private void invalidateEditCache() { cachedEdit = null; }

    @Override
    public void commitText(final String text) {
        run(() -> {
            AccessibilityNodeInfo node = obtainEdit();
            if (node == null) return;
            CharSequence cur = node.getText();
            String base = cur == null ? "" : cur.toString();
            int selStart = Math.max(node.getTextSelectionStart(), 0);
            int selEnd = Math.max(node.getTextSelectionEnd(), selStart);
            if (selStart > base.length()) selStart = base.length();
            if (selEnd > base.length()) selEnd = base.length();
            String out = base.substring(0, selStart) + text + base.substring(selEnd);
            Bundle b = new Bundle();
            b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, out);
            boolean ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
            if (!ok) invalidateEditCache();
            // 光标跟到插入点后（仅当节点明确报告选区时）
            int pos = selStart + text.length();
            Bundle sel = new Bundle();
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, pos);
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, pos);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
        });
    }

    @Override
    public void sendSpecial(final int code) {
        run(() -> {
            AccessibilityNodeInfo node = obtainEdit();
            switch (code) {
                case KeyEventCodes.ENTER:
                    if (node != null) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            boolean ok = node.performAction(
                                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                            if (!ok) commitText("\n");
                        } else {
                            commitText("\n");
                        }
                    }
                    break;
                case KeyEventCodes.BACKSPACE:
                    if (node != null) {
                        CharSequence cur = node.getText();
                        String base = cur == null ? "" : cur.toString();
                        if (base.isEmpty()) return;
                        int s = Math.max(node.getTextSelectionStart(), 0);
                        int e = Math.max(node.getTextSelectionEnd(), s);
                        if (s == e && s > 0) s = s - 1;      // 无选区则删前一字符
                        if (s > base.length()) s = base.length();
                        if (e > base.length()) e = base.length();
                        String out = base.substring(0, s) + base.substring(e);
                        Bundle b = new Bundle();
                        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, out);
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
                        Bundle sel = new Bundle();
                        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, s);
                        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, s);
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
                    }
                    break;
                case KeyEventCodes.TAB:
                    commitText("\t");
                    break;
                case KeyEventCodes.ARROW_LEFT:
                case KeyEventCodes.ARROW_RIGHT:
                case KeyEventCodes.ARROW_UP:
                case KeyEventCodes.ARROW_DOWN:
                    moveCursorSel(node, code);
                    break;
                case KeyEventCodes.HOME:
                case KeyEventCodes.END:
                    if (node != null) {
                        CharSequence cur = node.getText();
                        int len = cur == null ? 0 : cur.length();
                        int pos = code == KeyEventCodes.HOME ? 0 : len;
                        Bundle sel = new Bundle();
                        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, pos);
                        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, pos);
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
                    }
                    break;
                case KeyEventCodes.ESCAPE:
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    break;
                default:
                    break;
            }
        });
    }

    private void moveCursorSel(AccessibilityNodeInfo node, int code) {
        if (node == null) return;
        CharSequence cur = node.getText();
        if (cur == null) return;
        int len = cur.length();
        int pos = Math.max(node.getTextSelectionStart(), 0);
        switch (code) {
            case KeyEventCodes.ARROW_LEFT:  pos = Math.max(0, pos - 1); break;
            case KeyEventCodes.ARROW_RIGHT: pos = Math.min(len, pos + 1); break;
            case KeyEventCodes.ARROW_UP:    pos = Math.max(0, pos - 16); break;
            case KeyEventCodes.ARROW_DOWN:  pos = Math.min(len, pos + 16); break;
        }
        Bundle sel = new Bundle();
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, pos);
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, pos);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
    }

    @Override
    public void sendCombo(final int code, final int mods) {
        run(() -> {
            AccessibilityNodeInfo node = findFocused();
            boolean ctrl = (mods & KeyEventCodes.MOD_CTRL) != 0;
            boolean meta = (mods & KeyEventCodes.MOD_META) != 0;
            if ((ctrl || meta) && node != null) {
                switch (code) {
                    case KeyEventCodes.KEY_C: node.performAction(AccessibilityNodeInfo.ACTION_COPY); return;
                    case KeyEventCodes.KEY_V: node.performAction(AccessibilityNodeInfo.ACTION_PASTE); return;
                    case KeyEventCodes.KEY_X: node.performAction(AccessibilityNodeInfo.ACTION_CUT); return;
                    case KeyEventCodes.KEY_A: {
                        CharSequence cur = node.getText();
                        int len = cur == null ? 0 : cur.length();
                        Bundle sel = new Bundle();
                        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len);
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
                        return;
                    }
                    case KeyEventCodes.KEY_Z: // 撤销无公开 API，退而求其次
                    default: break;
                }
            }
            // 其余组合键无公开注入通道，忽略
        });
    }

    // ---------------- MouseSink ----------------

    @Override
    public void moveCursor(float dx, float dy, float sensitivity) {
        if (!InputRouter.isMouseEnabled()) return;
        ensureCursorInit();
        cursorX += dx * sensitivity;
        cursorY += dy * sensitivity;
        // 钳位到屏幕
        android.view.WindowManager wm2 = (android.view.WindowManager)
                getSystemService(Context.WINDOW_SERVICE);
        android.graphics.Point sz = new android.graphics.Point();
        wm2.getDefaultDisplay().getRealSize(sz);
        if (cursorX < 0) cursorX = 0;
        if (cursorY < 0) cursorY = 0;
        if (cursorX > sz.x) cursorX = sz.x;
        if (cursorY > sz.y) cursorY = sz.y;
        // 同步视觉光标
        CursorView.setPosition(cursorX, cursorY);
    }

    @Override
    public void click(int button, boolean down) {
        if (!InputRouter.isMouseEnabled()) return;   // 双保险：开关关闭时拒绝注入
        ensureCursorInit();                           // 光标未初始化时先兜底定位，避免「开关后首次点击失效」
        if (!down) return; // 抬手才触发一次点按
        final float x = cursorX, y = cursorY;
        run(() -> tapAt(x, y));
    }

    @Override
    public void scroll(float dx, float dy) {
        if (!InputRouter.isMouseEnabled()) return;
        // 滚动 = 在光标处做短距滑动（方向与内容滚动一致）
        ensureCursorInit();
        final float x = cursorX;
        final float y = cursorY;
        float ddy = -dy * 2f;   // 手指向下 -> 内容上移
        float ddx = -dx * 2f;
        if (Math.abs(ddy) < 4 && Math.abs(ddx) < 4) return;
        final float ex = x + ddx, ey = y + ddy;
        run(() -> swipeAt(x, y, ex, ey, 120));
    }

    @Override
    public void drag(float dx, float dy) {
        if (!InputRouter.isMouseEnabled()) return;
        ensureCursorInit();
        float nx = cursorX + dx, ny = cursorY + dy;
        final float sx = cursorX, sy = cursorY, ex = nx, ey = ny;
        cursorX = nx; cursorY = ny;
        CursorView.setPosition(cursorX, cursorY);
        run(() -> swipeAt(sx, sy, ex, ey, 300));
    }

    /** 光标尚未初始化时，将其定位到上半屏中心（分屏下目标 App 所在区域） */
    private void ensureCursorInit() {
        if (cursorX >= 0) return;
        android.view.WindowManager wm = (android.view.WindowManager)
                getSystemService(Context.WINDOW_SERVICE);
        android.graphics.Point p = new android.graphics.Point();
        wm.getDefaultDisplay().getRealSize(p);
        cursorX = p.x / 2f;
        cursorY = p.y / 4f;   // 初始在上半屏中心
        CursorView.setPosition(cursorX, cursorY);
    }

    // ---------------- 分屏 ----------------

    /**
     * 触发系统分屏（把当前前台 App 挤到上半屏主屏）。
     * 返回是否成功。HyperOS 可能屏蔽此 global action，失败时返回 false。
     */
    public boolean toggleSplitScreen() {
        try {
            return performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
        } catch (Exception e) {
            return false;
        }
    }

    /** 判断当前是否已在分屏状态 */
    public boolean isSplitScreen() {
        try {
            for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                if (w != null && w.getType() ==
                        android.view.accessibility.AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ---------------- 自动聚焦 ----------------

    /** 找到「上半屏应用」的可编辑节点并聚焦（打字前兜底，跨窗口定位微信） */
    public void focusNearestEdit() {
        AccessibilityNodeInfo node = findEditInUpperApp();
        if (node == null) return;
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    // ---------------- 手势工具 ----------------

    private void tapAt(float x, float y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 60));
        dispatchGesture(b.build(), null, null);
    }

    private void swipeAt(float x1, float y1, float x2, float y2, long dur) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(dur, 80)));
        dispatchGesture(b.build(), null, null);
    }

    private void run(Runnable r) { InputRouter.runOnMain(r); }

    /** 供调试/其他组件检查服务存活 */
    public static boolean isAlive() { return instance != null; }
}
