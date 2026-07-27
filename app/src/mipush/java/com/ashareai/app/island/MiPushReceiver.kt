package com.ashareai.app.island

import android.content.Context
import com.xiaomi.mipush.sdk.ErrorCode
import com.xiaomi.mipush.sdk.MiPushClient
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import com.xiaomi.mipush.sdk.MiPushMessage
import com.xiaomi.mipush.sdk.PushMessageReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Official MiPush callback bridge, compiled only when app/libs contains the SDK AAR. */
class MiPushReceiver : PushMessageReceiver() {
    override fun onNotificationMessageArrived(context: Context, message: MiPushMessage) {
        launch(context) { PushManager.handlePayload(context, message.content, arrived = true, clicked = false) }
    }

    override fun onNotificationMessageClicked(context: Context, message: MiPushMessage) {
        launch(context) { PushManager.handlePayload(context, message.content, arrived = false, clicked = true) }
    }

    override fun onReceiveRegisterResult(context: Context, message: MiPushCommandMessage) {
        if (message.command == MiPushClient.COMMAND_REGISTER && message.resultCode == ErrorCode.SUCCESS.toLong()) {
            PushManager.bindAuthenticatedDevice(context)
        }
    }

    override fun onCommandResult(context: Context, message: MiPushCommandMessage) {
        if (message.command == MiPushClient.COMMAND_REGISTER && message.resultCode == ErrorCode.SUCCESS.toLong()) {
            PushManager.bindAuthenticatedDevice(context)
        }
    }

    private fun launch(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }
}
