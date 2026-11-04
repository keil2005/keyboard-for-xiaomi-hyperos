package com.foldkb.deck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 广播接收器：远程重启面板，避免 force-stop 杀掉无障碍服务。
 * 用法：adb shell am broadcast -a com.foldkb.deck.ACTION_PANEL --es cmd restart
 */
public class PanelReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String cmd = intent != null ? intent.getStringExtra("cmd") : null;
        if ("start".equals(cmd)) {
            // 启动键盘 Activity（分屏下半屏）
            DeckActivity.start(ctx);
        } else if ("stop".equals(cmd)) {
            ctx.stopService(new Intent(ctx, DeckService.class));
        } else if ("restart".equals(cmd)) {
            ctx.stopService(new Intent(ctx, DeckService.class));
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> DeckActivity.start(ctx), 300);
        } else if ("split".equals(cmd)) {
            // 用无障碍触发分屏（把当前前台 App 挤到上半屏）
            DeckAccessibilityService a11y = DeckAccessibilityService.get();
            if (a11y != null) a11y.toggleSplitScreen();
        }
    }
}
