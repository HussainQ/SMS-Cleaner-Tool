package com.example.telephony

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver required by Android to allow the app to qualify as the Default SMS app.
 * Parses incoming SMS PDUs, writes them to Telephony Inbox when CleanSMS is Default SMS App,
 * and notifies active UI observers so threads update immediately.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            return
        }
        Log.d("CleanSMS", "SMS deliver action received")

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (t: Throwable) {
            Log.e("CleanSMS", "Failed to extract SMS messages from intent", t)
            emptyArray()
        }
        if (messages.isNullOrEmpty()) {
            IncomingTelephonyNotifier.notifyIncoming(com.example.model.MessageProtocol.SMS)
            return
        }

        // Reassemble multi-part SMS segments by originating address
        val grouped = LinkedHashMap<String, StringBuilder>()
        var latestTimestamp = System.currentTimeMillis()
        for (sms in messages) {
            val sender = sms.originatingAddress ?: sms.displayOriginatingAddress ?: "Unknown"
            val body = sms.messageBody ?: sms.displayMessageBody ?: ""
            grouped.getOrPut(sender) { StringBuilder() }.append(body)
            if (sms.timestampMillis > 0L) {
                latestTimestamp = sms.timestampMillis
            }
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isDefault = TelephonyManagerHelper.isDefaultSmsApp(context)
                for ((sender, bodyBuilder) in grouped) {
                    val fullBody = bodyBuilder.toString()
                    if (isDefault) {
                        val values = ContentValues().apply {
                            put(Telephony.Sms.ADDRESS, sender)
                            put(Telephony.Sms.BODY, fullBody)
                            put(Telephony.Sms.DATE, latestTimestamp)
                            put(Telephony.Sms.READ, 0)
                            put(Telephony.Sms.SEEN, 0)
                            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                        }
                        try {
                            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                        } catch (e: Exception) {
                            Log.e("CleanSMS", "Failed to store delivered SMS in Inbox", e)
                        }
                    }
                    IncomingTelephonyNotifier.notifyIncoming(
                        protocol = com.example.model.MessageProtocol.SMS,
                        sender = sender,
                        bodyPreview = fullBody
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
