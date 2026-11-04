package com.foldkb.deck;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 设置向导：逐项开启权限 -> 一键启动下半屏键盘面板。
 * 启动后自动拉起目标应用（上半屏），形成上下分屏。
 */
public class MainActivity extends Activity {

    private TextView a11yRow, imeRow, overlayRow;
    private static final String TARGET_PKG = "com.tencent.mm"; // 微信，可改

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF121214);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));

        TextView title = new TextView(this);
        title.setText("FoldDeck 半屏键盘");
        title.setTextSize(24);
        title.setTextColor(0xFFF2F2F5);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("18 Fold 半折形态 · 上半屏 = 微信等应用\n下半屏 = 常驻电脑键盘 + 触控板");
        sub.setTextSize(13);
        sub.setTextColor(0xFF9A9AA0);
        sub.setPadding(0, dp(6), 0, dp(20));
        root.addView(sub);

        overlayRow = makeStep("① 悬浮窗权限", "允许在其他应用上层显示键盘面板", v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        root.addView(overlayRow);

        a11yRow = makeStep("② 无障碍服务", "打字写入 + 鼠标点击/滚动/拖拽通道", v -> {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(i);
        });
        root.addView(a11yRow);

        imeRow = makeStep("③ 选择输入法", "启用并切换到「FoldDeck 隐形输入法」\n（屏蔽系统键盘弹出）", v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });
        root.addView(imeRow);

        // 启动按钮：启动键盘 Activity（分屏下半屏）
        TextView start = new TextView(this);
        start.setText("▶ 启动键盘面板");
        start.setTextSize(17);
        start.setTextColor(0xFFFFFFFF);
        start.setGravity(Gravity.CENTER);
        start.setBackgroundColor(0xFF5B8DEF);
        start.setPadding(0, dp(16), 0, dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        start.setLayoutParams(lp);
        start.setOnClickListener(v -> {
            DeckActivity.start(this);
            finish();
        });
        root.addView(start);

        TextView only = new TextView(this);
        only.setText("启动键盘并打开微信（分屏）");
        only.setTextSize(13);
        only.setTextColor(0xFF5B8DEF);
        only.setGravity(Gravity.CENTER);
        only.setPadding(0, dp(14), 0, dp(10));
        only.setOnClickListener(v -> launch());
        root.addView(only);

        TextView tip = new TextView(this);
        tip.setText("\n提示：首次开启权限后返回本页，圆点变绿即就绪。\n" +
                "「应用对」用法：半折手机 → 把 FoldDeck 键盘和微信分屏一次 →\n" +
                "长按分屏分割线存成「应用对」，之后一键上下分屏启动。");
        tip.setTextSize(12);
        tip.setTextColor(0xFF6A6A70);
        root.addView(tip);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRows();
    }

    private void launch() {
        // 尝试系统分屏：微信进上半屏，FoldDeck 键盘在下半屏
        SplitScreenHelper.enterSplit(this, TARGET_PKG);
    }

    private void refreshRows() {
        setRowState(overlayRow, Settings.canDrawOverlays(this));
        setRowState(a11yRow, isA11yEnabled());
        setRowState(imeRow, isImeSelected());
    }

    private void setRowState(TextView row, boolean ok) {
        String text = (String) row.getTag();
        row.setText((ok ? "✅ " : "⚪ ") + text);
        row.setTextColor(ok ? 0xFF4CAF50 : 0xFFDEDEE2);
    }

    private TextView makeStep(String title, String desc, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        String full = title + "\n" + desc;
        tv.setTag(full);
        tv.setText("⚪ " + full);
        tv.setTextSize(15);
        tv.setTextColor(0xFFDEDEE2);
        tv.setPadding(dp(16), dp(14), dp(16), dp(14));
        tv.setBackgroundColor(0xFF1E1E20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(onClick);
        return tv;
    }

    private boolean isA11yEnabled() {
        ComponentName expected = new ComponentName(this, DeckAccessibilityService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName cn = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(cn)) return true;
        }
        return false;
    }

    private boolean isImeSelected() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName expected = new ComponentName(this, GhostImeService.class);
        return expected.flattenToString().equals(cur);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
