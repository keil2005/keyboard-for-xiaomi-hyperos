package com.foldkb.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.OverScroller;

/**
 * Mac Touch Bar 风格表情触控条。
 * 深色底 + 一排彩色 emoji，左右滑动平滑滚动，点选即通过 InputRouter 输入。
 * emoji 使用系统内置字体渲染（无需额外资源）。
 */
public class EmojiBarView extends View {

    /** 常用 emoji 序列（系统字体自带，无需打包资源） */
    private static final String[] EMOJIS = {
            "😀", "😂", "🥰", "😍", "😘", "😅", "🤣", "😊",
            "😎", "🤩", "😭", "😢", "😡", "😱", "🥺", "😴",
            "🤔", "👍", "👎", "👏", "🙏", "💪", "🤝", "👌",
            "✌️", "🤞", "❤️", "🧡", "💛", "💚", "💙", "💜",
            "🖤", "💔", "💯", "🔥", "✨", "⭐", "🎉", "🎊",
            "🌸", "🌹", "🍀", "🌈", "☀️", "🌙", "⛄", "🍔",
            "🍕", "🍜", "☕", "🍺", "🎁", "🎈", "🏆", "⚽",
            "🎮", "🎵", "📷", "📱", "💻", "🚗", "✈️", "🚀"
    };

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final Paint pyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);   // 拼音串
    private final Paint candPaint = new Paint(Paint.ANTI_ALIAS_FLAG); // 候选字
    private final RectF tmp = new RectF();

    private final float emojiSize;      // 每个 emoji 的字号
    private final float itemW;          // 每格宽度
    private final float pad;

    private OverScroller scroller;
    private VelocityTracker velocity;

    private int scrollX;                // 当前滚动偏移（逻辑像素）
    private int maxScroll;              // 最大滚动量
    private float lastTouchX;
    private boolean dragging;

    // 触控条高度（dp）
    private static final int BAR_HEIGHT_DP = 40;

    // ---- 拼音模式 ----
    private boolean pinyinMode = false;
    private PinyinEngine engine = new PinyinEngine();
    private java.util.List<String> candidates = new java.util.ArrayList<>();
    private android.graphics.RectF[] candidateRects = new android.graphics.RectF[0];

    /** 候选上屏后的回调（用于通知键盘重置拼音长度计数） */
    public interface OnCommitListener { void onCommitted(); }
    private OnCommitListener commitListener;
    public void setOnCommitListener(OnCommitListener l) { this.commitListener = l; }

    public EmojiBarView(Context c) {
        super(c);
        float dp = c.getResources().getDisplayMetrics().density;
        emojiSize = 22 * dp;
        pad = 8 * dp;
        itemW = 40 * dp;

        bgPaint.setColor(0xFF1C1C1E);
        textPaint.setTextAlign(Paint.Align.CENTER);
        pyPaint.setColor(0xFFE8E8EC);
        pyPaint.setTextSize(20 * dp);
        pyPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        candPaint.setColor(0xFF4FC3F7);
        candPaint.setTextSize(20 * dp);
        candPaint.setTextAlign(Paint.Align.CENTER);

        scroller = new OverScroller(c);
        setWillNotDraw(false);
    }

    // ---- 拼音模式对外接口 ----

    public boolean isPinyinMode() { return pinyinMode; }

    public void setPinyinMode(boolean on) {
        pinyinMode = on;
        if (!on) { engine.clear(); candidates.clear(); }
        invalidate();
    }

    public void addPinyinLetter(char c) {
        engine.addLetter(c);
        refreshCandidates();
    }

    public void backspacePinyin() {
        engine.backspace();
        refreshCandidates();
    }

    public void clearPinyin() {
        engine.clear();
        candidates.clear();
        invalidate();
    }

    private void refreshCandidates() {
        candidates = engine.getCandidates(20);
        // 重新计算候选矩形
        int n = candidates.size();
        candidateRects = new android.graphics.RectF[n];
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float dp = getResources().getDisplayMetrics().density;
        int h = (int) (BAR_HEIGHT_DP * dp);
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();

        // 长条屏幕：黑色底色 + 四角小圆角，四周留一点空隙模拟真实屏幕边框
        float inset = 2f * getResources().getDisplayMetrics().density;
        float rad = 8f * getResources().getDisplayMetrics().density;
        android.graphics.Paint screenPaint = new android.graphics.Paint(Paint.ANTI_ALIAS_FLAG);
        screenPaint.setColor(0xFF0A0A0C);
        canvas.drawRoundRect(inset, inset, w - inset, h - inset, rad, rad, screenPaint);

        // 内部内容在圆角范围内绘制
        if (pinyinMode) {
            drawPinyin(canvas, w, h);
            return;
        }

        // 计算可见范围
        int total = EMOJIS.length;
        int first = Math.max(0, scrollX / (int) itemW);
        int last = Math.min(total - 1, (int) ((scrollX + w) / itemW) + 1);

        canvas.save();
        canvas.clipRect(0, 0, w, h);
        textPaint.setTextSize(emojiSize);
        float baseline = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
        for (int i = first; i <= last; i++) {
            float cx = pad + i * itemW + itemW / 2f - scrollX;
            canvas.drawText(EMOJIS[i], cx, baseline, textPaint);
        }
        canvas.restore();

        // 左右渐隐遮罩（Touch Bar 风格，提示可滑动）
        drawFadeMasks(canvas, w, h);
    }

    /** 拼音模式：绘制拼音串 + 候选字 */
    private void drawPinyin(Canvas canvas, float w, float h) {
        String py = engine.getPinyin();
        float baseline = h / 2f - (pyPaint.descent() + pyPaint.ascent()) / 2f;

        // 拼音串（左侧）
        float x = pad;
        if (py.isEmpty()) {
            pyPaint.setColor(0xFF6A6A70);
            canvas.drawText("拼音", x, baseline, pyPaint);
            pyPaint.setColor(0xFFE8E8EC);
        } else {
            canvas.drawText(py, x, baseline, pyPaint);
            // 光标
            float pyWidth = pyPaint.measureText(py);
            float cx = x + pyWidth + 4;
            canvas.drawRect(cx, h * 0.25f, cx + 2, h * 0.75f, pyPaint);
        }

        // 候选字（右侧，逐个绘制并记录点击区域）
        float cx = w * 0.35f;
        candPaint.setTextSize(20 * getResources().getDisplayMetrics().density);
        float candBaseline = h / 2f - (candPaint.descent() + candPaint.ascent()) / 2f;
        candidateRects = new android.graphics.RectF[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            String c = candidates.get(i);
            float cw = candPaint.measureText(c) + 16 * getResources().getDisplayMetrics().density;
            if (cx + cw > w) break;  // 超出就不画了
            float cy = h * 0.12f;
            candidateRects[i] = new android.graphics.RectF(cx, cy, cx + cw, h - cy);
            canvas.drawText(c, cx + cw / 2f, candBaseline, candPaint);
            cx += cw;
        }
    }

    private void drawFadeMasks(Canvas canvas, float w, float h) {
        Paint fade = new Paint(Paint.ANTI_ALIAS_FLAG);
        android.graphics.LinearGradient lgLeft = new android.graphics.LinearGradient(
                0, 0, 24 * getResources().getDisplayMetrics().density, 0,
                0xFF1C1C1E, 0x001C1C1E, android.graphics.Shader.TileMode.CLAMP);
        fade.setShader(lgLeft);
        canvas.drawRect(0, 0, 24 * getResources().getDisplayMetrics().density, h, fade);

        android.graphics.LinearGradient lgRight = new android.graphics.LinearGradient(
                w - 24 * getResources().getDisplayMetrics().density, 0, w, 0,
                0x001C1C1E, 0xFF1C1C1E, android.graphics.Shader.TileMode.CLAMP);
        fade.setShader(lgRight);
        canvas.drawRect(w - 24 * getResources().getDisplayMetrics().density, 0, w, h, fade);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        maxScroll = Math.max(0, (int) (EMOJIS.length * itemW + pad * 2 - w));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // 拼音模式：只处理点击候选，不支持滑动
        if (pinyinMode) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                handlePinyinTap(e.getX(), e.getY());
            }
            return true;
        }

        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                lastTouchX = e.getX();
                scroller.forceFinished(true);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - lastTouchX;
                lastTouchX = e.getX();
                int nx = scrollX - (int) dx;
                scrollX = clampScroll(nx);
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_UP: {
                dragging = false;
                velocity.computeCurrentVelocity(1000);
                float vx = velocity.getXVelocity();
                // 判断是点击还是滑动
                if (Math.abs(vx) < 500 && Math.abs(e.getX() - downX) < 16 * getResources().getDisplayMetrics().density) {
                    // 点击：命中某个 emoji
                    handleTap(e.getX());
                } else {
                    // 惯性滑动
                    scroller.fling(scrollX, 0, (int) -vx, 0, 0, maxScroll, 0, 0);
                    postOnAnimation(scrollRunnable);
                }
                velocity.recycle();
                velocity = null;
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (velocity != null) { velocity.recycle(); velocity = null; }
                return true;
        }
        return super.onTouchEvent(e);
    }

    private float downX;

    // 记录 down 位置用于点击判定（在 ACTION_DOWN 里已存 lastTouchX，这里复用）
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) downX = ev.getX();
        return super.dispatchTouchEvent(ev);
    }

    private void handleTap(float x) {
        int idx = (int) ((x + scrollX - pad) / itemW);
        if (idx >= 0 && idx < EMOJIS.length) {
            InputRouter.commitText(EMOJIS[idx]);
        }
    }

    /** 拼音模式下点击候选字上屏 */
    private void handlePinyinTap(float x, float y) {
        for (int i = 0; i < candidateRects.length; i++) {
            android.graphics.RectF r = candidateRects[i];
            if (r != null && r.contains(x, y)) {
                String c = candidates.get(i);
                InputRouter.commitText(c);  // 上屏
                clearPinyin();              // 上屏后清空拼音，继续输入
                if (commitListener != null) commitListener.onCommitted();
                return;
            }
        }
    }

    private int clampScroll(int v) {
        if (v < 0) return 0;
        if (v > maxScroll) return maxScroll;
        return v;
    }

    private final Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (scroller.computeScrollOffset()) {
                scrollX = clampScroll(scroller.getCurrX());
                invalidate();
                postOnAnimation(this);
            }
        }
    };
}
