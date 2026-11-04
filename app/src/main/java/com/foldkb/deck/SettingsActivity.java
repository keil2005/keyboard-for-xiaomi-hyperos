package com.foldkb.deck;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.CompoundButton;
import android.widget.Switch;

/**
 * 设置页：按键声音 / 按键震动 / 键盘主题 / 背光效果。
 * 纯代码构建（无 XML 布局），浅色优雅配色。
 */
public class SettingsActivity extends Activity {

    private LinearLayout container;
    private final java.util.List<TextView> soundRows = new java.util.ArrayList<>();
    private final java.util.List<TextView> backlightRows = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF2F2F5);

        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(container);
        setContentView(scroll);

        // 标题
        TextView title = new TextView(this);
        title.setText("FoldDeck 设置");
        title.setTextColor(0xFF1A1A1E);
        title.setTextSize(24);
        title.setPadding(0, 0, 0, dp(4));
        container.addView(title);

        TextView sub = new TextView(this);
        sub.setText("按键声音 · 震动 · 主题 · 背光");
        sub.setTextColor(0xFF8A8A90);
        sub.setTextSize(13);
        sub.setPadding(0, 0, 0, dp(20));
        container.addView(sub);

        // ---- 分组：按键声音 ----
        addSectionTitle("按键声音");
        addSoundRow("机械青轴", DeckSettings.SOUND_MECHANICAL, "清脆 click 声");
        addSoundRow("低沉 Thock", DeckSettings.SOUND_THOCK, "厚实低频声");
        addSoundRow("柔和轻声", DeckSettings.SOUND_SOFT, "轻微短音");
        addSoundRow("静音", DeckSettings.SOUND_OFF, "关闭声音");

        // ---- 分组：震动 ----
        addSectionTitle("按键震动");
        addSwitchRow("按键震动反馈", "按下键帽时触发系统震动", DeckSettings.vibrateOn(this),
                (b) -> DeckSettings.setVibrateOn(this, b));

        // ---- 分组：主题 ----
        addSectionTitle("键盘主题");
        addThemeRow("银灰铝合金", DeckSettings.THEME_SILVER, 0xFFC8CAD0);
        addThemeRow("深空灰", DeckSettings.THEME_DARK, 0xFF4A4A50);
        addThemeRow("太空黑", DeckSettings.THEME_SPACE, 0xFF1A1A1E);
        addThemeRow("深邃蓝", DeckSettings.THEME_BLUE, 0xFF2C3E50);
        addThemeRow("神秘紫", DeckSettings.THEME_PURPLE, 0xFF4A3A6A);

        // ---- 分组：背光 ----
        addSectionTitle("按键背光");
        addSwitchRow("按键背光效果", "键帽发光（当前主题色）", DeckSettings.backlightOn(this),
                (b) -> DeckSettings.setBacklightOn(this, b));

        // 背光效果模式（单选）
        addBacklightModeRow("呼吸", DeckSettings.BACKLIGHT_BREATH, "整体明暗缓慢变化");
        addBacklightModeRow("扫描", DeckSettings.BACKLIGHT_SCAN, "光带左右扫过键盘");
        addBacklightModeRow("按键亮", DeckSettings.BACKLIGHT_KEYPRESS, "按下键帽发光渐灭");

        // 初始化选中态
        refreshSoundRows();
        refreshBacklightRows();
    }

    // ---- 构建辅助 ----

    private void addSectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF8A8A90);
        t.setTextSize(13);
        t.setPadding(0, dp(20), 0, dp(8));
        container.addView(t);
    }

    /** 声音选项行（单选，带预览播放） */
    private void addSoundRow(String label, final int type, String desc) {
        final TextView row = new TextView(this);
        row.setText(label + "   " + desc);
        row.setTextSize(15);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(roundBg(0xFFFFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> {
            DeckSettings.setSoundType(this, type);
            // 立即试听一次
            SoundFx.key(this, type, false);
            refreshSoundRows();
        });
        row.setTag(type);
        soundRows.add(row);
        container.addView(row);
    }

    private void refreshSoundRows() {
        int cur = DeckSettings.soundType(this);
        for (TextView row : soundRows) {
            // 通过 tag 记住 type，比较是否选中
            Integer t = (Integer) row.getTag();
            if (t != null && t == cur) {
                row.setTextColor(0xFF1A73E8);
            } else {
                row.setTextColor(0xFF1A1A1E);
            }
        }
    }

    /** 背光效果模式行（单选） */
    private void addBacklightModeRow(String label, final int mode, String desc) {
        final TextView row = new TextView(this);
        row.setText(label + "   " + desc);
        row.setTextSize(14);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(roundBg(0xFFFFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        row.setOnClickListener(v -> {
            DeckSettings.setBacklightMode(this, mode);
            refreshBacklightRows();
        });
        row.setTag(mode);
        backlightRows.add(row);
        container.addView(row);
    }

    private void refreshBacklightRows() {
        int cur = DeckSettings.backlightMode(this);
        for (TextView row : backlightRows) {
            Integer m = (Integer) row.getTag();
            if (m != null && m == cur) {
                row.setTextColor(0xFF1A73E8);
            } else {
                row.setTextColor(0xFF1A1A1E);
            }
        }
    }

    /** 主题选项行（单选，带色块预览） */
    private void addThemeRow(String label, final int theme, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(roundBg(0xFFFFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        // 色块
        View swatch = new View(this);
        swatch.setBackground(roundBg(color));
        row.addView(swatch, new LinearLayout.LayoutParams(dp(22), dp(22)));

        // 文字
        TextView labelTv = new TextView(this);
        labelTv.setText("  " + label);
        labelTv.setTextSize(15);
        labelTv.setTextColor(DeckSettings.theme(this) == theme ? 0xFF1A73E8 : 0xFF1A1A1E);
        row.addView(labelTv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 选中标记
        if (DeckSettings.theme(this) == theme) {
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextColor(0xFF1A73E8);
            check.setTextSize(16);
            row.addView(check);
        }

        row.setOnClickListener(v -> {
            DeckSettings.setTheme(this, theme);
            // 简单重绘：重建页面
            recreate();
        });
        container.addView(row);
    }

    /** 开关行 */
    private void addSwitchRow(String label, String desc, boolean init, final OnChanged cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(roundBg(0xFFFFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(0xFF1A1A1E);
        labelTv.setTextSize(15);
        textCol.addView(labelTv);
        TextView descTv = new TextView(this);
        descTv.setText(desc);
        descTv.setTextColor(0xFF8A8A90);
        descTv.setTextSize(12);
        textCol.addView(descTv);
        row.addView(textCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(init);
        sw.setOnCheckedChangeListener((CompoundButton b, boolean checked) -> cb.changed(checked));
        row.addView(sw);

        container.addView(row);
    }

    interface OnChanged { void changed(boolean v); }

    private android.graphics.drawable.GradientDrawable roundBg(int color) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
