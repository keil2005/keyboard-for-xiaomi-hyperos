package com.foldkb.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * MacBook 风格触控板（比真 Mac 偏大）。
 * 浅色玻璃面 + 细边框 + 微渐变，手势同前：
 * 滑动=光标 / 轻点=左键 / 双指点按=右键 / 双指滑动=滚动 / 长按拖动=拖拽。
 */
public class TrackpadView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG); // 内阴影层次
    private final RectF r = new RectF();
    private LinearGradient gloss;

    // 主题颜色
    private int themeFace;      // 触控板玻璃面
    private int themeFaceDeep;  // 玻璃面渐变深端
    private int themeBorder;    // 边框
    private int themeHint;      // 提示文字
    private boolean themeDark;  // 是否深色主题（决定高光强度）

    // 手势状态
    private float lastX, lastY;
    private long downTime;
    private boolean moved, dragging;
    private float startX, startY;
    private boolean twoFinger;

    private static final float CLICK_SLOP_DP = 12;
    private static final long LONG_PRESS_MS = 350;
    private float clickSlop;

    public TrackpadView(Context c) {
        super(c);
        float dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, c.getResources().getDisplayMetrics());
        clickSlop = CLICK_SLOP_DP * dp;
        hintPaint.setTextSize(10 * dp);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        loadTheme(c);
    }

    /** 读取主题，映射触控板配色 */
    private void loadTheme(Context c) {
        int theme = DeckSettings.theme(c);
        switch (theme) {
            case DeckSettings.THEME_DARK:
                themeFace = 0xFF3A3A40;
                themeFaceDeep = 0xFF2A2A30;
                themeBorder = 0xFF1E1E22;
                themeHint = 0xFF777780;
                themeDark = true;
                break;
            case DeckSettings.THEME_SPACE:
                themeFace = 0xFF202024;
                themeFaceDeep = 0xFF141418;
                themeBorder = 0xFF0A0A0E;
                themeHint = 0xFF5A5A62;
                themeDark = true;
                break;
            case DeckSettings.THEME_BLUE:
                themeFace = 0xFF3E5668;
                themeFaceDeep = 0xFF2C3E50;
                themeBorder = 0xFF1A2836;
                themeHint = 0xFF9FB3C8;
                themeDark = true;
                break;
            case DeckSettings.THEME_PURPLE:
                themeFace = 0xFF4A3D66;
                themeFaceDeep = 0xFF3A2E55;
                themeBorder = 0xFF241B36;
                themeHint = 0xFFB3A8CC;
                themeDark = true;
                break;
            case DeckSettings.THEME_SILVER:
            default:
                themeFace = 0xFFEEEEF2;
                themeFaceDeep = 0xFFD4D4D8;
                themeBorder = 0xFFA8AAB0;
                themeHint = 0xFF9A9CA2;
                themeDark = false;
                break;
        }
        bgPaint.setColor(themeFace);
        borderPaint.setColor(themeBorder);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.2f * c.getResources().getDisplayMetrics().density);
        hintPaint.setColor(themeHint);
    }

    /** 主题变化后刷新 */
    public void refreshTheme(Context c) {
        loadTheme(c);
        if (getWidth() > 0) {
            gloss = new LinearGradient(0, 0, 0, getHeight(),
                    themeFace, themeFaceDeep, Shader.TileMode.CLAMP);
            glossPaint.setShader(gloss);   // 关键：重新绑定 shader，否则玻璃渐变不更新
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        // 上亮下暗的微渐变，模拟玻璃反光
        gloss = new LinearGradient(0, 0, 0, h,
                themeFace, themeFaceDeep, Shader.TileMode.CLAMP);
        glossPaint.setShader(gloss);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = 2;
        r.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float rad = 12 * getResources().getDisplayMetrics().density;

        // 内阴影层次：底部一圈深色描边（模拟凹陷）
        edgePaint.setColor(android.graphics.Color.argb(40, 0, 0, 0));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(2.5f * getResources().getDisplayMetrics().density);
        canvas.drawRoundRect(r, rad, rad, edgePaint);

        // 底色 + 玻璃渐变
        canvas.drawRoundRect(r, rad, rad, bgPaint);
        if (gloss != null) canvas.drawRoundRect(r, rad, rad, glossPaint);
        // 边框
        canvas.drawRoundRect(r, rad, rad, borderPaint);

        // 顶部高光（玻璃反光，层次感；深色主题减弱）
        Paint hi = new Paint(Paint.ANTI_ALIAS_FLAG);
        int hiAlpha = themeDark ? 28 : 60;
        android.graphics.LinearGradient hiGrad = new android.graphics.LinearGradient(
                0, inset, 0, getHeight() * 0.4f,
                android.graphics.Color.argb(hiAlpha, 255, 255, 255),
                android.graphics.Color.argb(0, 255, 255, 255),
                android.graphics.Shader.TileMode.CLAMP);
        hi.setShader(hiGrad);
        canvas.drawRoundRect(r, rad, rad, hi);

        // 提示（极淡，不抢视觉）
        canvas.drawText("点按=左键 · 双指=右键 · 滚动 · 拖拽",
                getWidth() / 2f, getHeight() / 2f, hintPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                moved = false;
                dragging = false;
                twoFinger = false;
                lastX = startX = e.getX();
                lastY = startY = e.getY();
                downTime = System.currentTimeMillis();
                postDelayed(longPressCheck, LONG_PRESS_MS);
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (e.getPointerCount() == 2) {
                    twoFinger = true;
                    removeCallbacks(longPressCheck);
                    lastX = (e.getX(0) + e.getX(1)) / 2f;
                    lastY = (e.getY(0) + e.getY(1)) / 2f;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (twoFinger && e.getPointerCount() >= 2) {
                    float cx = (e.getX(0) + e.getX(1)) / 2f;
                    float cy = (e.getY(0) + e.getY(1)) / 2f;
                    float dx = cx - lastX, dy = cy - lastY;
                    lastX = cx; lastY = cy;
                    moved = true;
                    InputRouter.mouseScroll(dx * 0.8f, dy * 0.8f);
                } else if (!twoFinger) {
                    float dx = e.getX() - lastX, dy = e.getY() - lastY;
                    lastX = e.getX(); lastY = e.getY();
                    if (Math.abs(e.getX() - startX) > clickSlop || Math.abs(e.getY() - startY) > clickSlop) {
                        moved = true;
                        removeCallbacks(longPressCheck);
                    }
                    if (dragging) InputRouter.mouseDrag(dx, dy);
                    else if (moved) InputRouter.mouseMove(dx, dy, 1.2f);   // 1.2x 加速
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (e.getPointerCount() == 2 && twoFinger) {
                    if (!moved && System.currentTimeMillis() - downTime < 400) {
                        InputRouter.mouseClick(2, true);
                    }
                    twoFinger = false;
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(longPressCheck);
                if (!twoFinger && !moved && !dragging) {
                    if (System.currentTimeMillis() - downTime < 300) {
                        InputRouter.mouseClick(1, true);
                    }
                }
                if (dragging) {
                    InputRouter.mouseClick(1, false);
                    dragging = false;
                }
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private final Runnable longPressCheck = new Runnable() {
        @Override
        public void run() {
            if (!moved && !twoFinger) {
                dragging = true;
                InputRouter.mouseClick(1, true);
            }
        }
    };
}
