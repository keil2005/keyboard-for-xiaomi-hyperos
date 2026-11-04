package com.foldkb.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * 悬浮鼠标光标 — 跟随触控板手势移动的视觉指针。
 * 独立 overlay 窗口，不拦截触摸（FLAG_NOT_TOUCHABLE）。
 * macOS 风格箭头：黑色填充 + 白色描边。
 */
public class CursorView extends View {

    private static CursorView instance;
    private static WindowManager wm;
    private static WindowManager.LayoutParams params;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();

    // ---- 生命周期 ----

    public static void init(Context ctx) {
        if (instance != null) return;
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        instance = new CursorView(ctx);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 400;
        params.y = 400;
        try {
            wm.addView(instance, params);
        } catch (Exception ignored) {}
    }

    /** 更新光标到屏幕绝对坐标 (x, y) */
    public static void setPosition(float x, float y) {
        if (instance == null || wm == null) return;
        int cx = (int) x - instance.getMeasuredWidth() / 3;   // 箭头尖端对准坐标
        int cy = (int) y - instance.getMeasuredHeight() / 8;
        if (cx == params.x && cy == params.y) return;         // 避免无谓刷新
        params.x = cx;
        params.y = cy;
        try {
            wm.updateViewLayout(instance, params);
        } catch (Exception ignored) {}
    }

    public static void destroy() {
        if (instance != null && wm != null) {
            try { wm.removeView(instance); } catch (Exception ignored) {}
        }
        instance = null;
    }

    /** 显示/隐藏光标（关闭鼠标时隐藏指针） */
    public static void setVisible(boolean visible) {
        if (instance == null) return;
        instance.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    public static boolean isShowing() { return instance != null; }

    // ---- 绘制 ----

    private CursorView(Context c) {
        super(c);
        float dp = c.getResources().getDisplayMetrics().density;

        fillPaint.setColor(0xFF1A1A1A);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setColor(0xFFFFFFFF);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.8f * dp);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        // macOS 指针形状（局部坐标，s=缩放）
        float s = 1.6f * dp;
        arrow.moveTo(0, 0);                       // 尖端
        arrow.lineTo(0, 17 * s);                  // 左边下
        arrow.lineTo(4.2f * s, 13.2f * s);        // 凹口
        arrow.lineTo(7.2f * s, 20 * s);           // 尾下
        arrow.lineTo(10.2f * s, 18.6f * s);       // 尾右
        arrow.lineTo(7.4f * s, 12 * s);           // 回上
        arrow.lineTo(12 * s, 11.5f * s);          // 右翼
        arrow.close();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float dp = getResources().getDisplayMetrics().density;
        setMeasuredDimension((int) (14 * dp * 1.6f), (int) (22 * dp * 1.6f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(arrow, fillPaint);
        canvas.drawPath(arrow, strokePaint);
    }
}
