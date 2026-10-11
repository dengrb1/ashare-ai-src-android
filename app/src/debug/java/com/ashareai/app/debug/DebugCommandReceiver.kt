package com.ashareai.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DebugBridgeActivity.ACTION) return
        context.startActivity(Intent(context, DebugBridgeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(DebugBridgeActivity.EXTRA_COMMAND, intent.getStringExtra(DebugBridgeActivity.EXTRA_COMMAND))
            putExtra(DebugBridgeActivity.EXTRA_REPORT_ID, intent.getStringExtra(DebugBridgeActivity.EXTRA_REPORT_ID))
        })
    }
}
