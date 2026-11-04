package com.foldkb.deck;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * 按键声音 + 震动反馈。
 * 声音：SoundPool 加载真实机械键盘采样（res/raw 下合成 wav），
 *   低延迟 + 支持并发，连续打字不卡。
 * 震动：系统 Vibrator（VibrationEffect.createOneShot）。
 */
public final class SoundFx {

    private static SoundPool pool;
    private static Vibrator vibrator;

    // 声音资源 id（SoundPool load 后返回）
    private static int sClick = 0, sThock = 0, sSoft = 0;
    private static boolean loaded = false;

    private SoundFx() {}

    /** 懒加载 SoundPool 并预加载三个音效 */
    private static synchronized void ensureLoaded(Context c) {
        if (loaded) return;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder()
                    .setMaxStreams(8)
                    .setAudioAttributes(attrs)
                    .build();
            sClick = pool.load(c, R.raw.key_click, 1);
            sThock = pool.load(c, R.raw.key_thock, 1);
            sSoft = pool.load(c, R.raw.key_soft, 1);
            loaded = true;
        } catch (Exception e) {
            // 加载失败则静默（不崩溃），保留 ToneGenerator 兜底逻辑可后续补充
            pool = null;
        }
    }

    private static Vibrator vib(Context c) {
        if (vibrator == null) {
            vibrator = (Vibrator) c.getApplicationContext()
                    .getSystemService(Context.VIBRATOR_SERVICE);
        }
        return vibrator;
    }

    /** 按键时调用：播放声音 + 震动（按当前设置） */
    public static void key(Context c, int soundType, boolean vibrateOn) {
        playSample(c, soundType);
        if (vibrateOn) vibrate(c);
    }

    private static void playSample(Context c, int type) {
        if (type == DeckSettings.SOUND_OFF) return;
        ensureLoaded(c);
        if (pool == null) return;
        try {
            int sid = 0;
            switch (type) {
                case DeckSettings.SOUND_MECHANICAL: sid = sClick; break;
                case DeckSettings.SOUND_THOCK:      sid = sThock; break;
                case DeckSettings.SOUND_SOFT:       sid = sSoft;  break;
                default: return;
            }
            if (sid != 0) {
                pool.play(sid, 1.0f, 1.0f, 1, 0, 1.0f);
            }
        } catch (Exception ignored) {}
    }

    private static void vibrate(Context c) {
        try {
            Vibrator v = vib(c);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(15);
            }
        } catch (Exception ignored) {}
    }
}
