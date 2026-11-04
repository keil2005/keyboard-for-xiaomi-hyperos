package com.foldkb.deck;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * 常驻电脑键盘面板（深色键帽，照参考图排布）。
 * 触摸 -> InputRouter 提交字符 / 特殊键 / 组合键。
 */
public class KeyboardView extends View {

    private static class Key {
        String label; String altLabel; // altLabel: shift 后显示
        String out; String altOut;     // 实际输出
        int code;                      // 特殊键码，0=普通字符
        float weight;                  // 宽度权重
        boolean special;               // 修饰/功能键样式
        boolean toggle;                // 可锁定（Shift/Ctrl/Alt/Meta）
        RectF rect = new RectF();
        boolean pressed;
        boolean latched;               // 锁定状态
        Key(String label, String altLabel, String out, int code, float weight, boolean special, boolean toggle) {
            this.label = label;
            this.altLabel = altLabel;
            this.out = out;
            // shift 输出：单字母 -> 大写；否则取 altLabel（符号上档）
            if (out != null && out.length() == 1 && Character.isLetter(out.charAt(0)))
                this.altOut = out.toUpperCase(java.util.Locale.ROOT);
            else
                this.altOut = altLabel;
            this.code = code;
            this.weight = weight; this.special = special; this.toggle = toggle;
        }
    }

    private final java.util.List<Key[]> rows = new java.util.ArrayList<>();
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private Key activeKey;
    private boolean shiftOn, ctrlOn, altOn, metaOn, enMode = true;

    // 拼音模式
    private boolean pinyinOn = false;
    private int pinyinLen = 0;   // 当前拼音串长度（用于退格判断：>0 删拼音，==0 删文字）
    private PinyinListener pinyinListener;

    /** 拼音输入回调（由 DeckActivity 桥接到 EmojiBarView） */
    public interface PinyinListener {
        void onPinyinModeChanged(boolean on);
        void onPinyinLetter(char c);
        void onPinyinBackspace();
    }

    public void setPinyinListener(PinyinListener l) { this.pinyinListener = l; }
    public boolean isPinyinOn() { return pinyinOn; }
    public void setPinyinOn(boolean on) { pinyinOn = on; }

    /** 候选上屏后由外部通知，重置本地拼音长度计数 */
    public void resetPinyinLen() { pinyinLen = 0; }

    private float gap, radius;

    // 主题配色（从 DeckSettings 读取）
    private int themeBodyColor;      // 机身色
    private int themeKeyColor;       // 普通键帽
    private int themeSpecialColor;   // 功能键帽
    private int themeKeyPressed;     // 按下键帽
    private int themeShadowColor;    // 键帽投影
    private int themeTextColor;      // 键帽字符
    private int themeDimTextColor;   // 功能键字符
    private boolean backlight;       // 背光开关
    private int backlightColor;      // 背光色（= 主题强调色）
    private int backlightMode;       // 背光效果模式

    // 呼吸动画
    private android.animation.ValueAnimator breathAnim;
    private float breathPhase = 1.0f;   // 0~1，呼吸相位
    private float scanPhase = 0f;       // 0~1，扫描光带位置

    // 按键亮：按下后渐灭的键（记录按下时间，绘制时算衰减）
    private final java.util.Map<Key, Long> litKeys = new java.util.HashMap<>();
    private static final long KEYPRESS_FADE_MS = 380;  // 按键亮衰减时长

    public KeyboardView(Context c) {
        super(c);
        float dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, c.getResources().getDisplayMetrics());
        gap = 3 * dp;
        radius = 6 * dp;
        textPaint.setTextAlign(Paint.Align.CENTER);
        dimPaint.setTextAlign(Paint.Align.CENTER);
        buildRows();
        loadTheme(c);
        applyBacklightAnimation();
    }

    /** 读取主题设置，映射为具体颜色 */
    private void loadTheme(Context c) {
        int theme = DeckSettings.theme(c);
        backlight = DeckSettings.backlightOn(c);
        backlightMode = DeckSettings.backlightMode(c);
        switch (theme) {
            case DeckSettings.THEME_DARK:
                themeBodyColor = 0xFF3C3C42;
                themeKeyColor = 0xFF525258;
                themeSpecialColor = 0xFF2E2E32;
                themeKeyPressed = 0xFF6A6A70;
                themeShadowColor = 0xFF202024;
                themeTextColor = 0xFFE8E8EC;
                themeDimTextColor = 0xFF9A9AA0;
                backlightColor = 0xFF00D2FF;
                break;
            case DeckSettings.THEME_SPACE:
                themeBodyColor = 0xFF141418;
                themeKeyColor = 0xFF232328;
                themeSpecialColor = 0xFF1A1A1E;
                themeKeyPressed = 0xFF3A3A40;
                themeShadowColor = 0xFF000000;
                themeTextColor = 0xFFF2F2F5;
                themeDimTextColor = 0xFF777780;
                backlightColor = 0xFF7B68EE;
                break;
            case DeckSettings.THEME_BLUE:
                themeBodyColor = 0xFF2C3E50;
                themeKeyColor = 0xFF34495E;
                themeSpecialColor = 0xFF243342;
                themeKeyPressed = 0xFF4A6278;
                themeShadowColor = 0xFF1A252F;
                themeTextColor = 0xFFECF0F1;
                themeDimTextColor = 0xFF9FB3C8;
                backlightColor = 0xFF4FC3F7;
                break;
            case DeckSettings.THEME_PURPLE:
                themeBodyColor = 0xFF3A2E55;
                themeKeyColor = 0xFF4A3A6A;
                themeSpecialColor = 0xFF32264A;
                themeKeyPressed = 0xFF5E4B85;
                themeShadowColor = 0xFF241B36;
                themeTextColor = 0xFFEFEBF7;
                themeDimTextColor = 0xFFB3A8CC;
                backlightColor = 0xFFB388FF;
                break;
            case DeckSettings.THEME_SILVER:
            default:
                themeBodyColor = 0xFFC8CAD0;
                themeKeyColor = 0xFF46464B;
                themeSpecialColor = 0xFF37373B;
                themeKeyPressed = 0xFF6A6A70;
                themeShadowColor = 0xFFB0B2B8;
                themeTextColor = 0xFFF2F2F5;
                themeDimTextColor = 0xFF9A9AA0;
                backlightColor = 0xFF4FC3F7;
                break;
        }
        textPaint.setColor(themeTextColor);
        dimPaint.setColor(themeDimTextColor);
    }

    /** 主题变化后刷新（供设置页回调） */
    public void refreshTheme(Context c) {
        loadTheme(c);
        // 模式变化时重建动画（时长不同）
        if (breathAnim != null) {
            breathAnim.cancel();
            breathAnim = null;
        }
        applyBacklightAnimation();
        invalidate();
    }

    /** 颜色混合透明度 */
    private int applyAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (android.graphics.Color.alpha(color) * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** 启动/停止背光动画（按模式） */
    private void applyBacklightAnimation() {
        if (backlight) {
            if (breathAnim == null) {
                breathAnim = android.animation.ValueAnimator.ofFloat(0f, 1f, 0f);
                breathAnim.setDuration(backlightMode == DeckSettings.BACKLIGHT_SCAN ? 3000 : 2600);
                breathAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                breathAnim.setRepeatMode(android.animation.ValueAnimator.RESTART);
                breathAnim.addUpdateListener(anim -> {
                    float v = (float) anim.getAnimatedValue();
                    if (backlightMode == DeckSettings.BACKLIGHT_SCAN) {
                        // 扫描：0→1→0 映射为光带从左到右扫一个来回
                        scanPhase = v;
                    } else {
                        breathPhase = v;
                    }
                    invalidate();
                });
            }
            if (!breathAnim.isRunning()) breathAnim.start();
        } else {
            if (breathAnim != null && breathAnim.isRunning()) breathAnim.cancel();
            breathPhase = 1.0f;
            scanPhase = 0f;
        }
    }

    private void buildRows() {
        // 数字行
        rows.add(new Key[]{
                k("`","~","`",1f), k("1","!","1",1f), k("2","@","2",1f), k("3","#","3",1f),
                k("4","$","4",1f), k("5","%","5",1f), k("6","^","6",1f), k("7","&","7",1f),
                k("8","*","8",1f), k("9","(","9",1f), k("0",")","0",1f), k("-","_","-",1f),
                k("=","+","=",1f),
                sp("⌫", KeyEventCodes.BACKSPACE, 1.6f)
        });
        rows.add(new Key[]{
                sp("⇥", KeyEventCodes.TAB, 1.5f),
                k("q","Q","q",1f), k("w","W","w",1f), k("e","E","e",1f), k("r","R","r",1f),
                k("t","T","t",1f), k("y","Y","y",1f), k("u","U","u",1f), k("i","I","i",1f),
                k("o","O","o",1f), k("p","P","p",1f), k("[","{","[",1f), k("]","}","]",1f),
                k("\\","|","\\",1.2f)
        });
        rows.add(new Key[]{
                sp("中/英", KeyEventCodes.LANG, 1.8f),   // 国行 Mac：Caps Lock 位标「中/英」
                k("a","A","a",1f), k("s","S","s",1f), k("d","D","d",1f), k("f","F","f",1f),
                k("g","G","g",1f), k("h","H","h",1f), k("j","J","j",1f), k("k","K","k",1f),
                k("l","L","l",1f), k(";",";",":",1f), k("'","\"","'",1f),
                sp("⏎", KeyEventCodes.ENTER, 1.9f)
        });
        rows.add(new Key[]{
                sp("⇧", KeyEventCodes.SHIFT, 2.3f),
                k("z","Z","z",1f), k("x","X","x",1f), k("c","C","c",1f), k("v","V","v",1f),
                k("b","B","b",1f), k("n","N","n",1f), k("m","M","m",1f), k(",","<",",",1f),
                k(".",">",".",1f), k("/","?","/",1f),
                sp("⇧", KeyEventCodes.SHIFT, 2.3f)
        });
        rows.add(new Key[]{
                sp("fn", KeyEventCodes.LANG, 0.9f),   // fn 亦作中/英切换（与 Caps Lock 位一致）
                sp("⌃", KeyEventCodes.CTRL, 1f),
                sp("⌥", KeyEventCodes.ALT, 1f),
                sp("⌘", KeyEventCodes.META, 1.2f),
                sp("space", KeyEventCodes.SPACE, 4.8f),
                sp("⌘", KeyEventCodes.META, 1.2f),
                sp("⌥", KeyEventCodes.ALT, 1f),
                sp("◀", KeyEventCodes.ARROW_LEFT, 0.85f),
                sp("▲", KeyEventCodes.ARROW_UP, 0.85f),
                sp("▼", KeyEventCodes.ARROW_DOWN, 0.85f),
                sp("▶", KeyEventCodes.ARROW_RIGHT, 0.85f)
        });
    }

    private Key k(String l, String al, String o, float w) {
        return new Key(l, al, o, 0, w, false, false);
    }
    private Key sp(String l, int code, float w) {
        return new Key(l, null, null, code, w, true, isToggle(code));
    }
    private boolean isToggle(int code) {
        return code == KeyEventCodes.SHIFT || code == KeyEventCodes.CTRL
                || code == KeyEventCodes.ALT || code == KeyEventCodes.META;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int hMode = MeasureSpec.getMode(hSpec);
        int hSize = MeasureSpec.getSize(hSpec);
        int h;
        if (hMode == MeasureSpec.EXACTLY) {
            h = hSize;   // 尊重父布局分配的高度（weight 场景关键）
        } else if (hMode == MeasureSpec.AT_MOST) {
            h = Math.min(hSize, rows.size() * (int) (w / 14f / 2.4f));
        } else {
            h = rows.size() * (int) (w / 14f / 2.4f);
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float rowH = (h - gap * (rows.size() + 1)) / rows.size();
        for (int r = 0; r < rows.size(); r++) {
            Key[] row = rows.get(r);
            float total = 0;
            for (Key k : row) total += k.weight;
            float avail = w - gap * (row.length + 1);
            float x = gap;
            for (Key k : row) {
                float kw = avail * k.weight / total;
                k.rect.set(x, gap + r * (rowH + gap), x + kw, gap + r * (rowH + gap) + rowH);
                drawKey(canvas, k);
                x += kw + gap;
            }
        }
    }

    private void drawKey(Canvas c, Key k) {
        // 中/英键：中文（拼音）模式常亮高亮，提示当前输入语言
        boolean langActive = k.code == KeyEventCodes.LANG && pinyinOn;
        boolean lit = k.pressed || k.latched || langActive;
        // 键帽投影
        float sh = radius * 0.3f;
        tmp.set(k.rect.left, k.rect.top + sh, k.rect.right, k.rect.bottom + sh);
        keyPaint.setColor(themeShadowColor);
        c.drawRoundRect(tmp, radius, radius, keyPaint);

        // 背光：键帽底部缝隙透光（发光色 = 主题强调色）
        if (backlight) {
            float glowAlpha;
            switch (backlightMode) {
                case DeckSettings.BACKLIGHT_SCAN: {
                    // 扫描：光带沿 X 轴扫过，键帽离光带中心越近越亮
                    float centerX = scanPhase * getWidth();
                    float dist = Math.abs(k.rect.centerX() - centerX);
                    float bandW = getWidth() * 0.28f;   // 光带半宽
                    float f = 1f - Math.min(1f, dist / bandW);
                    // 平滑过渡：用余弦让光带边缘柔和
                    f = (float) Math.max(0f, Math.cos((1f - f) * Math.PI / 2f));
                    glowAlpha = 0.18f + 0.82f * f;
                    break;
                }
                case DeckSettings.BACKLIGHT_KEYPRESS: {
                    // 按键亮：常驻微光 + 按下后渐灭的亮光
                    Long t = litKeys.get(k);
                    float pressGlow = 0f;
                    if (t != null) {
                        long elapsed = System.currentTimeMillis() - t;
                        if (elapsed < KEYPRESS_FADE_MS) {
                            pressGlow = 1f - (float) elapsed / KEYPRESS_FADE_MS;
                        } else {
                            litKeys.remove(k);
                        }
                    }
                    glowAlpha = 0.18f + 0.82f * pressGlow;
                    break;
                }
                case DeckSettings.BACKLIGHT_BREATH:
                default: {
                    // 呼吸：整体明暗周期变化
                    float breath = 0.55f + 0.45f * breathPhase;
                    glowAlpha = breath;
                    break;
                }
            }
            int glow = applyAlpha(backlightColor, glowAlpha);
            // 只在下缘画一圈细光晕，不铺满键帽
            shadowPaint.setColor(backlightColor);
            shadowPaint.setShadowLayer(radius * 0.9f, 0, radius * 0.15f, glow);
            tmp.set(k.rect);
            c.drawRoundRect(tmp, radius, radius, shadowPaint);
            shadowPaint.clearShadowLayer();
        }

        // 键帽本体
        int keyCol;
        if (lit) keyCol = themeKeyPressed;
        else keyCol = k.special ? themeSpecialColor : themeKeyColor;
        keyPaint.setColor(keyCol);
        tmp.set(k.rect);
        c.drawRoundRect(tmp, radius, radius, keyPaint);

        float textSize = k.rect.height() * 0.36f;
        Paint p = k.special ? dimPaint : textPaint;
        if (k.latched) p = textPaint;
        p.setTextSize(textSize);
        String label = k.label;
        if (!k.special && shiftOn && k.altLabel != null) label = k.altLabel;
        float baseline = k.rect.centerY() - (p.descent() + p.ascent()) / 2f;
        c.drawText(label, k.rect.centerX(), baseline, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                Key hit = hitTest(e.getX(), e.getY());
                if (hit != activeKey) {
                    if (activeKey != null && !activeKey.toggle) activeKey.pressed = false;
                    activeKey = hit;
                    if (hit != null) {
                        hit.pressed = true;
                        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) onPress(hit);
                    }
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (activeKey != null) {
                    if (activeKey.toggle) {
                        // 锁定类：按下即触发，抬手保持 latch
                        onReleaseToggle(activeKey);
                    } else {
                        activeKey.pressed = false;
                    }
                    activeKey = null;
                    invalidate();
                }
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private Key hitTest(float x, float y) {
        for (Key[] row : rows)
            for (Key k : row)
                if (k.rect.contains(x, y)) return k;
        return null;
    }

    private void onPress(Key k) {
        if (k.toggle) return;
        // 记录按键亮（背光「按键亮」模式用）
        litKeys.put(k, System.currentTimeMillis());
        // 按键亮渐灭需要持续重绘
        if (backlight && backlightMode == DeckSettings.BACKLIGHT_KEYPRESS) {
            postDelayed(() -> {
                invalidate();
                postDelayed(() -> { invalidate(); litKeys.clear(); }, KEYPRESS_FADE_MS + 60);
            }, 30);
        }
        // 声音 + 震动反馈
        SoundFx.key(getContext(), DeckSettings.soundType(getContext()),
                DeckSettings.vibrateOn(getContext()));
        if (k.code == KeyEventCodes.LANG) { // 中/英（Caps Lock 位 / fn）切换
            enMode = !enMode;
            pinyinOn = !pinyinOn;
            pinyinLen = 0;   // 切换时清空拼音缓冲计数
            if (pinyinListener != null) pinyinListener.onPinyinModeChanged(pinyinOn);
            invalidate();
            return;
        }

        // ---- 拼音模式：字母累积，退格删拼音/删文字，空格/回车退出 ----
        if (pinyinOn) {
            if (k.code == KeyEventCodes.BACKSPACE) {
                if (pinyinLen > 0) {
                    // 拼音串非空：删一个拼音字母
                    if (pinyinListener != null) pinyinListener.onPinyinBackspace();
                    pinyinLen--;
                } else {
                    // 拼音串已空：退格真正删除输入框里的已上屏文字
                    InputRouter.sendSpecial(KeyEventCodes.BACKSPACE);
                }
                invalidate();
                return;
            }
            if (k.code == KeyEventCodes.SPACE || k.code == KeyEventCodes.ENTER) {
                // 空格/回车：退出拼音模式（不输入）
                pinyinOn = false;
                pinyinLen = 0;
                if (pinyinListener != null) pinyinListener.onPinyinModeChanged(false);
                invalidate();
                return;
            }
            if (k.code == 0 && k.out != null && k.out.length() == 1
                    && Character.isLetter(k.out.charAt(0))) {
                // 字母：累积到拼音
                char c = Character.toLowerCase(k.out.charAt(0));
                if (pinyinListener != null) pinyinListener.onPinyinLetter(c);
                pinyinLen++;
                invalidate();
                return;
            }
            // 其他键（数字、符号、方向键）在拼音模式下仍正常输入
        }

        if (k.code != 0) {
            // 特殊键 / 空格
            if (k.code == KeyEventCodes.SPACE) {
                if (ctrlOn || altOn || metaOn) InputRouter.sendCombo(KeyEventCodes.KEY_A, mods());
                else InputRouter.commitText(" ");
            } else {
                int mods = mods();
                if (mods != 0 && isLetterCode(k)) {
                    InputRouter.sendCombo(letterCode(k), mods);
                } else {
                    InputRouter.sendSpecial(k.code);
                }
            }
            consumeOneShot();
            invalidate();
            return;
        }
        // 普通字符
        String out = (shiftOn && k.altOut != null) ? k.altOut : k.out;
        if (ctrlOn || altOn || metaOn) {
            int lc = letterCode(k);
            if (lc != 0) InputRouter.sendCombo(lc, mods());
        } else {
            InputRouter.commitText(out);
        }
        consumeOneShot();
        invalidate();
    }

    private void onReleaseToggle(Key k) {
        if (k.code == KeyEventCodes.SHIFT) shiftOn = !shiftOn;
        else if (k.code == KeyEventCodes.CTRL) ctrlOn = !ctrlOn;
        else if (k.code == KeyEventCodes.ALT) altOn = !altOn;
        else if (k.code == KeyEventCodes.META) metaOn = !metaOn;
        k.latched = (k.code == KeyEventCodes.SHIFT && shiftOn)
                || (k.code == KeyEventCodes.CTRL && ctrlOn)
                || (k.code == KeyEventCodes.ALT && altOn)
                || (k.code == KeyEventCodes.META && metaOn);
        invalidate();
    }

    private void consumeOneShot() {
        // Shift 敲一次自动取消；Ctrl/Alt/Meta 保持（跟电脑一致）
        if (shiftOn) {
            shiftOn = false;
            for (Key[] row : rows) for (Key k : row)
                if (k.code == KeyEventCodes.SHIFT) k.latched = false;
        }
    }

    private int mods() {
        int m = 0;
        if (shiftOn) m |= KeyEventCodes.MOD_SHIFT;
        if (ctrlOn)  m |= KeyEventCodes.MOD_CTRL;
        if (altOn)   m |= KeyEventCodes.MOD_ALT;
        if (metaOn)  m |= KeyEventCodes.MOD_META;
        return m;
    }

    private boolean isLetterCode(Key k) {
        return letterCode(k) != 0;
    }

    private int letterCode(Key k) {
        if (k.out == null || k.out.length() != 1) return 0;
        char ch = Character.toLowerCase(k.out.charAt(0));
        switch (ch) {
            case 'a': return KeyEventCodes.KEY_A;
            case 'c': return KeyEventCodes.KEY_C;
            case 'v': return KeyEventCodes.KEY_V;
            case 'x': return KeyEventCodes.KEY_X;
            case 'z': return KeyEventCodes.KEY_Z;
            default: return 0;
        }
    }

    /** DeckService 状态刷新用 */
    public void refresh() { invalidate(); }
}
