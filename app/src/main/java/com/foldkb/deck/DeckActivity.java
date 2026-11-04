package com.foldkb.deck;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 键盘面板 Activity：作为系统分屏的「下半屏」一方。
 * 半折笔记本形态下，上半屏 = 目标应用（微信等），本 Activity 占下半屏。
 * 布局（修订后）：
 *   表情触控条（Mac Touch Bar 风格，可左右滑动点选 emoji）
 *   → 键盘（占满剩余）
 *   → 底部栏（触控板居中缩窄 + 左下角退出开关）
 * 银灰铝合金配色，模拟 MacBook C 面。
 */
public class DeckActivity extends Activity {

    private LinearLayout root;
    private KeyboardView kb;
    private TrackpadView tp;
    private EmojiBarView emojiBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 让系统分屏能正确 resize 本 Activity
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        // 【关键】键盘窗口不参与焦点竞争：
        //   FLAG_NOT_FOCUSABLE 让本窗口可触摸但永不抢输入焦点，
        //   否则每次按键触摸都会把焦点从微信抢走 → 微信断开 IME 会话
        //   → getCurrentInputConnection() 失效 → 打一个字符就卡住。
        //   加上后，微信始终持有 IME 会话，打字稳定连续。
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        // 初始化悬浮鼠标光标（需要悬浮窗权限）
        CursorView.init(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bodyColor(this));   // 机身色（跟随主题）
        this.root = root;

        // ---- 表情触控条（Mac Touch Bar 风格，替换原格栅条/信息栏） ----
        EmojiBarView emojiBar = new EmojiBarView(this);
        this.emojiBar = emojiBar;
        root.addView(emojiBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- 键盘（吃掉剩余全部空间） ----
        KeyboardView kb = new KeyboardView(this);
        this.kb = kb;
        LinearLayout.LayoutParams lpKb = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lpKb.topMargin = dp(2);
        root.addView(kb, lpKb);

        // 桥接键盘拼音模式 <-> 流水屏
        kb.setPinyinListener(new KeyboardView.PinyinListener() {
            @Override public void onPinyinModeChanged(boolean on) {
                emojiBar.setPinyinMode(on);
            }
            @Override public void onPinyinLetter(char c) {
                emojiBar.addPinyinLetter(c);
            }
            @Override public void onPinyinBackspace() {
                emojiBar.backspacePinyin();
            }
        });
        // 候选上屏后，重置键盘侧的拼音长度计数（保证退格能继续删已上屏文字）
        emojiBar.setOnCommitListener(() -> kb.resetPinyinLen());

        // ---- 底部栏：触控板（居中缩窄一半）+ 左下角退出开关 ----
        // 用 FrameLayout 承载，触控板通过一个居中容器实现 50% 宽度
        FrameLayout bottom = new FrameLayout(this);
        LinearLayout.LayoutParams lpBottom = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(120));
        root.addView(bottom, lpBottom);

        // 居中容器：宽度占 50%，触控板填满它
        FrameLayout trackSlot = new FrameLayout(this);
        FrameLayout.LayoutParams lpSlot = new FrameLayout.LayoutParams(
                0, FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        bottom.addView(trackSlot, lpSlot);

        // 触控板：填满 50% 宽度的居中容器
        TrackpadView tp = new TrackpadView(this);
        this.tp = tp;
        FrameLayout.LayoutParams lpTp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(110),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lpTp.leftMargin = dp(10);
        lpTp.rightMargin = dp(10);
        lpTp.bottomMargin = dp(6);
        trackSlot.addView(tp, lpTp);

        // 左下角三个圆钮（横排，缩小以避开居中触控板）
        final int BTN = dp(32);   // 按钮边长（原 44 太大，与触控板重叠）
        final int BTN_GAP = dp(8);
        final int BTN_LEFT = dp(10);
        final int BTN_BOTTOM = dp(8);
        final float BTN_TEXT = 13f;

        // 电源键
        TextView powerBtn = new TextView(this);
        powerBtn.setText("⏻");
        powerBtn.setTextColor(0xFFB8B8BE);
        powerBtn.setTextSize(BTN_TEXT);
        powerBtn.setGravity(Gravity.CENTER);
        powerBtn.setBackground(roundBg(0xFF3A3A3E));
        powerBtn.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams lpPower = new FrameLayout.LayoutParams(BTN, BTN);
        lpPower.gravity = Gravity.BOTTOM | Gravity.START;
        lpPower.leftMargin = BTN_LEFT;
        lpPower.bottomMargin = BTN_BOTTOM;
        bottom.addView(powerBtn, lpPower);

        // 设置按钮
        TextView settingsBtn = new TextView(this);
        settingsBtn.setText("⚙");
        settingsBtn.setTextColor(0xFFB8B8BE);
        settingsBtn.setTextSize(BTN_TEXT);
        settingsBtn.setGravity(Gravity.CENTER);
        settingsBtn.setBackground(roundBg(0xFF3A3A3E));
        settingsBtn.setOnClickListener(v -> {
            android.content.Intent i = new android.content.Intent(this, SettingsActivity.class);
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        FrameLayout.LayoutParams lpSet = new FrameLayout.LayoutParams(BTN, BTN);
        lpSet.gravity = Gravity.BOTTOM | Gravity.START;
        lpSet.leftMargin = BTN_LEFT + BTN + BTN_GAP;
        lpSet.bottomMargin = BTN_BOTTOM;
        bottom.addView(settingsBtn, lpSet);

        // 鼠标开关按钮
        TextView mouseBtn = new TextView(this);
        mouseBtn.setText("🖱");
        mouseBtn.setGravity(Gravity.CENTER);
        mouseBtn.setTextSize(BTN_TEXT);
        mouseBtn.setBackground(roundBg(0xFF3A3A3E));
        mouseBtn.setOnClickListener(v -> {
            boolean cur = DeckSettings.mouseOn(this);
            boolean next = !cur;
            DeckSettings.setMouseOn(this, next);
            InputRouter.setMouseEnabled(next);
            CursorView.setVisible(next);   // 关闭鼠标时同步隐藏光标
            updateMouseBtn(mouseBtn, next);
        });
        FrameLayout.LayoutParams lpMouse = new FrameLayout.LayoutParams(BTN, BTN);
        lpMouse.gravity = Gravity.BOTTOM | Gravity.START;
        lpMouse.leftMargin = BTN_LEFT + (BTN + BTN_GAP) * 2;
        lpMouse.bottomMargin = BTN_BOTTOM;
        bottom.addView(mouseBtn, lpMouse);
        // 初始化状态
        InputRouter.setMouseEnabled(DeckSettings.mouseOn(this));
        updateMouseBtn(mouseBtn, DeckSettings.mouseOn(this));
        CursorView.setVisible(DeckSettings.mouseOn(this));   // 启动时按开关状态显示/隐藏光标

        setContentView(root);

        // 在布局完成后设置居中槽宽度为 50%
        bottom.post(() -> {
            FrameLayout.LayoutParams sl = (FrameLayout.LayoutParams) trackSlot.getLayoutParams();
            sl.width = bottom.getWidth() / 2;
            trackSlot.setLayoutParams(sl);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从设置页返回后刷新主题（机身色 + 键盘色 + 触控板）
        if (root != null) root.setBackgroundColor(bodyColor(this));
        if (kb != null) kb.refreshTheme(this);
        if (tp != null) tp.refreshTheme(this);
        // 隐藏状态栏，让分屏区域最大化
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CursorView.destroy();
    }

    /** 圆角背景 */
    private android.graphics.drawable.GradientDrawable roundBg(int color) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(16));
        return g;
    }

    /** 更新鼠标按钮外观（开启=蓝色高亮，关闭=暗灰） */
    private void updateMouseBtn(TextView btn, boolean on) {
        if (on) {
            btn.setTextColor(0xFF4FC3F7);
            btn.setBackground(roundBg(0xFF2A4A5A));
        } else {
            btn.setTextColor(0xFF6A6A70);
            btn.setBackground(roundBg(0xFF3A3A3E));
        }
    }

    /** 机身背景色（跟随主题） */
    private int bodyColor(Activity a) {
        switch (DeckSettings.theme(a)) {
            case DeckSettings.THEME_DARK:   return 0xFF3C3C42;
            case DeckSettings.THEME_SPACE:  return 0xFF141418;
            case DeckSettings.THEME_BLUE:   return 0xFF2C3E50;
            case DeckSettings.THEME_PURPLE: return 0xFF3A2E55;
            case DeckSettings.THEME_SILVER:
            default:                        return 0xFFC8CAD0;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 供外部（广播/主界面）启动键盘 Activity */
    public static void start(android.content.Context ctx) {
        android.content.Intent i = new android.content.Intent(ctx, DeckActivity.class);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}
