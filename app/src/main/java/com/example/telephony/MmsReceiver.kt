package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.model.MessageProtocol

/**
 * MMS WAP Push receiver required by Android for default SMS app compliance.
 * Notifies open UI observers so MMS threads refresh promptly.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CleanSMS", "MMS deliver action received: ${intent.action}")
        IncomingTelephonyNotifier.notifyIncoming(protocol = MessageProtocol.MMS)
    }
}
