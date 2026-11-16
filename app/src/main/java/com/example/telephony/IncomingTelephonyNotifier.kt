package com.example.telephony

import com.example.model.MessageProtocol
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class IncomingTelephonyEvent(
    val protocol: MessageProtocol,
    val sender: String?,
    val bodyPreview: String?,
    val receivedAtMs: Long = System.currentTimeMillis()
)

object IncomingTelephonyNotifier {
    private val _events = MutableSharedFlow<IncomingTelephonyEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<IncomingTelephonyEvent> = _events.asSharedFlow()

    fun notifyIncoming(
        protocol: MessageProtocol,
        sender: String? = null,
        bodyPreview: String? = null
    ) {
        _events.tryEmit(
            IncomingTelephonyEvent(
                protocol = protocol,
                sender = sender,
                bodyPreview = bodyPreview
            )
        )
    }
}
