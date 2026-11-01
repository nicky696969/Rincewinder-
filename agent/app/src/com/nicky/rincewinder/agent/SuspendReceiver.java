package com.nicky.rincewinder.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Otázka #8 sondy: vysílá tělo SUSPEND při MENU, nebo jen při vypnutí? Jen zapíše čas a akci do logu. */
public class SuspendReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        FileLog.get().init();
        FileLog.get().log("BROADCAST " + (intent == null ? "null" : intent.getAction()));
    }
}
