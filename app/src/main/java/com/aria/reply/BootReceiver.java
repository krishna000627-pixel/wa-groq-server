package com.aria.reply;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the persistent Aria follow-up notification after device reboot. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        AriaStore store = new AriaStore(c);
        if (store.pendingActionCount() > 0)
            Notifications.task(c, store);
    }
}
