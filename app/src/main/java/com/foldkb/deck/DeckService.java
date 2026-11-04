package com.foldkb.deck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 常驻悬浮面板：屏幕下半 50% = MacBook 风格下半机身。
 * 顶栏 = 喇叭格栅条（经典流水屏）→ 键盘 → 触控板。
 * 银灰铝合金配色，模拟真实 MacBook C 面。
 */
public class DeckService extends Service {

    private static DeckService instance;
    public static boolean isRunning() { return instance != null; }

    private WindowManager wm;
    private View panel;
    private TextView statusDot, statusText;
    private static final String CHANNEL = "folddeck";

    // ---- MacBook 配色 ----
    static final int BODY       = 0xFFC8CAD0;   // 银灰铝合金机身
    static final int BODY_DARK  = 0xFFB0B2B8;   // 稍暗边缘
    static final int GRILLE_BG  = 0xFF7A7A80;   // 喇叭格栅底色
    static final int GRILLE_DOT = 0xFF5E5E64;   // 格栅微孔
    static final int TRACKPAD_BG= 0xFFD8D8DC;   // 触控板玻璃面
    static final int TRACKPAD_BD= 0xFFA0A2A8;   // 触控板边框

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        startForegroundWithNotification();
        buildPanel();
        CursorView.init(this);
    }

    @Override
    public void onDestroy() {
        CursorView.destroy();
        if (panel != null && wm != null) {
            try { wm.removeView(panel); } catch (Exception ignored) {}
        }
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void startForegroundWithNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "FoldDeck",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent it = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, it,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder nb = new Notification.Builder(this, CHANNEL)
                .setContentTitle("FoldDeck 运行中")
                .setContentText("下半屏键盘面板已常驻")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pi)
                .setOngoing(true);
        startForeground(1, nb.build());
    }

    private void buildPanel() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BODY);

        // ---- 状态条（深色窄条） ----
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setBackgroundColor(0xFF2A2A2E);
        status.setPadding(dp(12), dp(5), dp(12), dp(5));
        status.setGravity(Gravity.CENTER_VERTICAL);

        statusDot = new TextView(this);
        statusDot.setText("●");
        statusDot.setTextColor(0xFFE5534B);
        statusDot.setTextSize(11);
        status.addView(statusDot);

        statusText = new TextView(this);
        statusText.setText(" 通道检测中…");
        statusText.setTextColor(0xFFDEDEE2);
        statusText.setTextSize(11);
        LinearLayout.LayoutParams lp0 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        status.addView(statusText, lp0);

        TextView hideBtn = new TextView(this);
        hideBtn.setText("✕");
        hideBtn.setTextColor(0xFF9A9AA0);
        hideBtn.setTextSize(13);
        hideBtn.setPadding(dp(10), dp(2), dp(2), dp(2));
        hideBtn.setOnClickListener(v -> stopSelf());
        status.addView(hideBtn);

        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- 喇叭格栅条（经典流水屏） ----
        View grille = new GrilleView(this);
        root.addView(grille, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(18)));

        // ---- 键盘（72% 空间） ----
        KeyboardView kb = new KeyboardView(this);
        LinearLayout.LayoutParams lpKb = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.72f);
        lpKb.topMargin = dp(2);
        root.addView(kb, lpKb);

        // ---- 触控板（28% 空间，贴底部） ----
        TrackpadView tp = new TrackpadView(this);
        LinearLayout.LayoutParams lpTp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.28f);
        lpTp.bottomMargin = dp(6);
        lpTp.leftMargin = dp(14);
        lpTp.rightMargin = dp(14);
        root.addView(tp, lpTp);

        // ---- 悬浮窗：屏幕精确 50% ----
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                screenHalfHeight(),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM;
        params.y = 0;

        panel = root;
        wm.addView(panel, params);

        root.post(statusTicker);
    }

    // ---- 喇叭格栅（深灰条 + 微孔阵列，复刻 MacBook 扬声器） ----
    private static class GrilleView extends View {
        private final Paint bgPaint = new Paint();
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float spacing, radius;

        GrilleView(Context c) {
            super(c);
            float dp = c.getResources().getDisplayMetrics().density;
            bgPaint.setColor(GRILLE_BG);
            dotPaint.setColor(GRILLE_DOT);
            spacing = 5 * dp;
            radius = 1.2f * dp;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
            float cx = spacing / 2, cy = getHeight() / 2f;
            while (cx < getWidth()) {
                canvas.drawCircle(cx, cy, radius, dotPaint);
                cx += spacing;
            }
        }
    }

    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            if (panel == null) return;
            boolean text = InputRouter.textReady();
            boolean mouse = InputRouter.mouseReady();
            if (text && mouse) {
                statusDot.setTextColor(0xFF4CAF50);
                statusText.setText(" IME + 无障碍 双通道就绪");
            } else if (text) {
                statusDot.setTextColor(0xFFFFC107);
                statusText.setText(" 打字就绪 · 鼠标待开启无障碍");
            } else if (mouse) {
                statusDot.setTextColor(0xFFFFC107);
                statusText.setText(" 鼠标就绪 · 打字待选择输入法");
            } else {
                statusDot.setTextColor(0xFFE5534B);
                statusText.setText(" 请开启无障碍 + 选择 FoldDeck 输入法");
            }
            panel.postDelayed(this, 1500);
        }
    };

    private int screenHalfHeight() {
        WindowManager wm2 = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        android.graphics.Point p = new android.graphics.Point();
        wm2.getDefaultDisplay().getRealSize(p);
        return p.y / 2;   // 精确 50%
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** MainActivity 里点"启动面板"调用 */
    public static void start(Context ctx) {
        Intent i = new Intent(ctx, DeckService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }
}
