package com.personal.detectivedialer.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import com.personal.detectivedialer.data.repository.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Respond via message" service — a mandatory component of the default-SMS-app
 * contract. The in-call UI starts this with ACTION_RESPOND_VIA_MESSAGE when the
 * user declines a call with a quick text response; we send it and stop.
 */
@AndroidEntryPoint
class HeadlessSmsSendService : Service() {

    @Inject lateinit var smsRepository: SmsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // recipient comes from the data URI (sms:/smsto:), text from EXTRA_TEXT
        val recipient = intent.data?.schemeSpecificPart.orEmpty()
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        if (recipient.isBlank() || text.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            runCatching { smsRepository.send(recipient, text) }
                .onFailure { Log.e(TAG, "Respond-via-message send failed", it) }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HeadlessSmsSend"
    }
}
