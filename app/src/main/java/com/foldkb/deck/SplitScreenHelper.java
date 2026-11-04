package com.foldkb.deck;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 系统分屏助手：把目标应用（微信）顶到上半屏，FoldDeck 留在下半屏。
 *
 * 正确方向（Android 官方文档）：
 *   FLAG_ACTIVITY_LAUNCH_ADJACENT + FLAG_ACTIVITY_NEW_TASK 启动目标 Activity，
 *   会让"当前全屏运行的应用"（FoldDeck 自己）进入分屏，并在相邻窗口（上半屏）启动目标 App。
 *
 * 兜底：无障碍 performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)。
 */
public final class SplitScreenHelper {

    private SplitScreenHelper() {}

    /**
     * 进入上下分屏：target 在上半屏，FoldDeck 键盘在下半屏。
     * 先启动键盘 Activity（占下半屏），再带 adjacent flag 启动目标 App。
     */
    public static void enterSplit(Activity activity, String targetPkg) {
        // 1) 先启动键盘 Activity（它将成为分屏的一方）
        DeckActivity.start(activity);

        // 2) 延迟启动目标 App，带 adjacent flag 尝试进上半屏
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Intent target = activity.getPackageManager().getLaunchIntentForPackage(targetPkg);
            if (target == null) return;
            target.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            try {
                activity.startActivity(target);
            } catch (Exception e) {
                DeckAccessibilityService a11y = DeckAccessibilityService.get();
                if (a11y != null) a11y.toggleSplitScreen();
            }
        }, 500);
    }

    /** 直接对当前前台 App 触发分屏（无障碍兜底），供广播/外部调用 */
    public static boolean toggleSplit(Context ctx) {
        DeckAccessibilityService a11y = DeckAccessibilityService.get();
        if (a11y != null && a11y.toggleSplitScreen()) {
            return true;
        }
        return false;
    }
}
