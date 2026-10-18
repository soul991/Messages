package com.messages.macrobenchmark

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object BenchmarkSeeder {
    fun seed(wipe: Boolean = true) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext

        val appCtx = try {
            targetContext.createPackageContext("com.messages.app.benchmark", Context.CONTEXT_IGNORE_SECURITY)
        } catch (_: Exception) {
            targetContext
        }

        val prefs = appCtx.getSharedPreferences("debug_harness", Context.MODE_PRIVATE)
        var token = prefs.getString("token", null)
        if (token == null) {
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            token = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString("token", token).commit()
        }

        val intent = Intent("com.messages.app.debug.SEED").apply {
            component = ComponentName("com.messages.app.benchmark", "com.messages.app.debug.SeedDataReceiver")
            putExtra("token", token)
            putExtra("wipe", wipe)
            if (wipe) {
                putExtra("confirm", "WIPE_ALL_DATA")
            }
        }

        val latch = CountDownLatch(1)
        var resultCode = Activity.RESULT_CANCELED
        var resultData: String? = null

        targetContext.sendOrderedBroadcast(
            intent,
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    resultCode = getResultCode()
                    resultData = getResultData()
                    latch.countDown()
                }
            },
            null,
            Activity.RESULT_CANCELED,
            null,
            null,
        )

        val completed = latch.await(20, TimeUnit.SECONDS)
        check(completed && resultCode == Activity.RESULT_OK) {
            "Database seeding failed! completed=$completed, resultCode=$resultCode, resultData=$resultData"
        }
    }
}
