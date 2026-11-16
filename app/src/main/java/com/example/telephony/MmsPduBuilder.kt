package com.example.telephony

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Lightweight OMA/WAP MMS M-Send.req PDU encoder so SmsManager.sendMultimediaMessage
 * can transmit text and media attachments cleanly without third-party PDU jars.
 */
object MmsPduBuilder {

    private const val HEADER_MMS_MESSAGE_TYPE = 0x8C
    private const val MESSAGE_TYPE_SEND_REQ = 0x80
    private const val HEADER_TRANSACTION_ID = 0x98
    private const val HEADER_MMS_VERSION = 0x8D
    private const val MMS_VERSION_1_2 = 0x92
    private const val HEADER_FROM = 0x89
    private const val FROM_INSERT_ADDRESS_TOKEN = 0x81
    private const val HEADER_TO = 0x97
    private const val HEADER_SUBJECT = 0x96
    private const val HEADER_CONTENT_TYPE = 0x84
    private const val CONTENT_TYPE_MULTIPART_MIXED = 0xA3 // well-known 0x23 | 0x80

    data class PreparedMmsPdu(
        val file: File,
        val contentUri: Uri,
        val sizeBytes: Int
    )

    fun buildSendReqPduBytes(
        recipient: String,
        bodyText: String?,
        subject: String? = null,
        attachmentBytes: ByteArray? = null,
        attachmentMimeType: String = "image/jpeg",
        attachmentFileName: String = "attachment.jpg",
        transactionId: String = "T${UUID.randomUUID().toString().take(12)}"
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // X-Mms-Message-Type: m-send-req
        out.write(HEADER_MMS_MESSAGE_TYPE)
        out.write(MESSAGE_TYPE_SEND_REQ)

        // X-Mms-Transaction-ID
        out.write(HEADER_TRANSACTION_ID)
        writeTextString(out, transactionId)

        // X-Mms-MMS-Version: 1.2
        out.write(HEADER_MMS_VERSION)
        out.write(MMS_VERSION_1_2)

        // From: insert-address-token
        out.write(HEADER_FROM)
        out.write(1)
        out.write(FROM_INSERT_ADDRESS_TOKEN)

        // To: formatted PLMN number when numeric/E.164
        val cleanRecipient = recipient.trim()
        val plmnFormatted = if (cleanRecipient.contains("@") || cleanRecipient.contains("/TYPE=")) {
            cleanRecipient
        } else {
            "$cleanRecipient/TYPE=PLMN"
        }
        out.write(HEADER_TO)
        writeEncodedStringValue(out, plmnFormatted)

        if (!subject.isNullOrBlank()) {
            out.write(HEADER_SUBJECT)
            writeEncodedStringValue(out, subject.trim())
        }

        // Content-Type: application/vnd.wap.multipart.mixed (must be final header before parts)
        out.write(HEADER_CONTENT_TYPE)
        out.write(CONTENT_TYPE_MULTIPART_MIXED)

        // Collect parts
        val parts = ArrayList<Triple<String, String?, ByteArray>>(2)
        if (!bodyText.isNullOrBlank()) {
            parts.add(
                Triple(
                    "text/plain",
                    "text_0.txt",
                    bodyText.toByteArray(StandardCharsets.UTF_8)
                )
            )
        }
        if (attachmentBytes != null && attachmentBytes.isNotEmpty()) {
            val safeMime = attachmentMimeType.ifBlank { "application/octet-stream" }
            val safeName = attachmentFileName.ifBlank { "media.bin" }
            parts.add(Triple(safeMime, safeName, attachmentBytes))
        }

        writeUintVar(out, parts.size)

        for ((index, part) in parts.withIndex()) {
            val (mimeType, fileName, data) = part
            val partHeaders = ByteArrayOutputStream()
            // Part Content-Type (plain null-terminated media type + UTF-8 charset if text)
            if (mimeType.equals("text/plain", ignoreCase = true)) {
                partHeaders.write("text/plain; charset=utf-8".toByteArray(StandardCharsets.US_ASCII))
            } else {
                partHeaders.write(mimeType.toByteArray(StandardCharsets.US_ASCII))
            }
            partHeaders.write(0)
            // Content-Location header (0x8E) + filename
            if (!fileName.isNullOrBlank()) {
                partHeaders.write(0x8E)
                writeTextString(partHeaders, fileName)
            }
            // Content-ID header (0xC0 | 0x40 -> 0xC0)
            partHeaders.write(0xC0)
            writeTextString(partHeaders, "<part_$index>")

            val headerBytes = partHeaders.toByteArray()
            writeUintVar(out, headerBytes.size)
            writeUintVar(out, data.size)
            out.write(headerBytes)
            out.write(data)
        }

        return out.toByteArray()
    }

    fun writePduToCacheFile(
        context: Context,
        pduBytes: ByteArray
    ): PreparedMmsPdu {
        val dir = File(context.cacheDir, "mms_outbox").apply { mkdirs() }
        // Keep cache bounded by cleaning outbox files older than 1 hour
        val cutoff = System.currentTimeMillis() - 3_600_000L
        dir.listFiles()?.forEach { old ->
            if (old.lastModified() < cutoff) {
                old.delete()
            }
        }
        val pduFile = File(dir, "mms_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.pdu")
        pduFile.writeBytes(pduBytes)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pduFile
        )
        return PreparedMmsPdu(
            file = pduFile,
            contentUri = uri,
            sizeBytes = pduBytes.size
        )
    }

    private fun writeTextString(out: ByteArrayOutputStream, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) >= 0x80) {
            out.write(0x7F)
        }
        out.write(bytes)
        out.write(0)
    }

    private fun writeEncodedStringValue(out: ByteArrayOutputStream, value: String) {
        val textBytes = value.toByteArray(StandardCharsets.UTF_8)
        // Encode as UTF-8 Encoded-string-value (MIBEnum 106 = 0x6A -> short int 0xEA)
        val inner = ByteArrayOutputStream()
        inner.write(0xEA) // Charset: UTF-8 (106 | 0x80)
        inner.write(textBytes)
        inner.write(0)
        val payload = inner.toByteArray()
        if (payload.size <= 30) {
            out.write(payload.size)
        } else {
            out.write(31)
            writeUintVar(out, payload.size)
        }
        out.write(payload)
    }

    private fun writeUintVar(out: ByteArrayOutputStream, value: Int) {
        var v = value
        val buf = IntArray(5)
        var i = 0
        do {
            buf[i++] = v and 0x7F
            v = v ushr 7
        } while (v != 0 && i < buf.size)
        for (j in i - 1 downTo 0) {
            val b = if (j > 0) buf[j] or 0x80 else buf[j]
            out.write(b)
        }
    }
}
