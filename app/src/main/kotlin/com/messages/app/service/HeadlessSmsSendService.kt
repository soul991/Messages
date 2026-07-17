package com.messages.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager

/**
 * Default-SMS mandatory component: ACTION_RESPOND_VIA_MESSAGE.
 * Sends quick "can't talk now" replies from the dialer.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recipient = intent?.data?.schemeSpecificPart
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!recipient.isNullOrBlank() && !text.isNullOrBlank()) {
            try {
                val sms = getSystemService(SmsManager::class.java)
                sms.sendTextMessage(recipient, null, text, null, null)
            } catch (_: Exception) {
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
