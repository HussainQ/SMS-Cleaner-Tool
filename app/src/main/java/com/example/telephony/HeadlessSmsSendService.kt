package com.example.telephony

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Headless SMS Send Service required for default SMS app role on Android.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
