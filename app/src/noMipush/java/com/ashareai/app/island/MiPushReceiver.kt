package com.ashareai.app.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Build-time fallback used only when the proprietary MiPush AAR is absent. */
class MiPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                PushManager.handleMessage(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }
}
