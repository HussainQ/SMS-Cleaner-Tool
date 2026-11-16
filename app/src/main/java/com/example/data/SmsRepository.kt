package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.example.model.ComposeAttachment
import com.example.model.ContactInfo
import com.example.model.MessageProtocol
import com.example.model.MessagingHelper
import com.example.model.PhoneNormalizer
import com.example.model.RcsDeliveryStatus
import com.example.model.SmsCategory
import com.example.model.SmsMessageItem
import com.example.telephony.MmsPduBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

data class SendMessageResult(
    val success: Boolean,
    val messageItem: SmsMessageItem? = null,
    val persistedUri: Uri? = null,
    val statusNote: String = "",
    val errorMessage: String? = null
)

data class ProviderThreadState(
    val isArchived: Boolean = false,
    val isSpam: Boolean = false,
    val folderSource: String = ""
)

class SmsRepository(private val context: Context) {

    /**
     * Reads device contacts using ContactsContract when READ_CONTACTS is granted.
     * Optimized with LinkedHashMap aggregation to avoid duplicate allocations.
     */
    fun readDeviceContacts(): List<ContactInfo> {
        val byContactId = LinkedHashMap<String, MutableContactAccumulator>()
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )
            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val contactId = if (idIdx >= 0) it.getString(idIdx) ?: continue else continue
                    val displayName = if (nameIdx >= 0) it.getString(nameIdx)?.trim().orEmpty() else ""
                    val rawNumber = if (numIdx >= 0) it.getString(numIdx)?.trim().orEmpty() else ""
                    if (rawNumber.isEmpty() && displayName.isEmpty()) continue

                    val acc = byContactId.getOrPut(contactId) {
                        MutableContactAccumulator(
                            id = contactId,
                            displayName = displayName.ifBlank { rawNumber }
                        )
                    }
                    if (rawNumber.isNotBlank()) {
                        acc.numbers.add(rawNumber)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w("CleanSMS", "READ_CONTACTS permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e("CleanSMS", "Error reading device contacts: ${e.message}")
        }

        return byContactId.values.map {
            ContactInfo(
                id = it.id,
                displayName = it.displayName,
                phoneNumbers = it.numbers.toList(),
                isFromDeviceContacts = true
            )
        }
    }

    /**
     * Builds an O(1) lookup index from normalized phone keys / names to ContactInfo.
     */
    fun buildContactLookupIndex(contacts: List<ContactInfo>): Map<String, ContactInfo> {
        if (contacts.isEmpty()) return emptyMap()
        val index = HashMap<String, ContactInfo>(contacts.size * 3)
        for (contact in contacts) {
            for (phone in contact.phoneNumbers) {
                for (key in PhoneNormalizer.extractMatchKeys(phone)) {
                    index.putIfAbsent(key, contact)
                }
            }
            val lowerName = contact.displayName.trim().lowercase()
            if (lowerName.isNotEmpty()) {
                index.putIfAbsent(lowerName, contact)
            }
        }
        return index
    }

    /**
     * Merges device contacts with existing Google Messages / SMS thread senders
     * so users can filter by both saved contacts and un-saved SMS threads.
     */
    fun getMergedContactsForMessages(
        messages: List<SmsMessageItem>,
        deviceContacts: List<ContactInfo>
    ): List<ContactInfo> {
        val merged = LinkedHashMap<String, ContactInfo>()
        val seenKeys = HashSet<String>()

        for (contact in deviceContacts) {
            merged[contact.id] = contact
            for (phone in contact.phoneNumbers) {
                seenKeys.add(PhoneNormalizer.primaryKey(phone))
            }
            seenKeys.add(contact.displayName.trim().lowercase())
        }

        // Link senders from existing Google Messages / SMS threads
        for (msg in messages) {
            val primaryKey = PhoneNormalizer.primaryKey(msg.address)
            val contactNameKey = msg.contactName?.trim()?.lowercase()
            if (primaryKey in seenKeys || (contactNameKey != null && contactNameKey in seenKeys)) {
                continue
            }
            seenKeys.add(primaryKey)
            val label = msg.contactName?.takeIf { it.isNotBlank() } ?: msg.address
            val syntheticId = msg.contactId ?: "thread_sender_$primaryKey"
            merged[syntheticId] = ContactInfo(
                id = syntheticId,
                displayName = label,
                phoneNumbers = listOf(msg.address),
                isFromDeviceContacts = false,
                threadId = msg.threadId.takeIf { it > 0 }
            )
        }

        return merged.values.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Reads thread-level archive/spam/blocked metadata from Telephony Threads (`conversations?simple=true`)
     * written by Google Messages, AOSP Messaging, Samsung/OEM apps, or third-party SMS clients.
     */
    fun readDeviceThreadStates(): Map<Long, ProviderThreadState> {
        val states = HashMap<Long, ProviderThreadState>()
        try {
            val simpleThreadsUri = Uri.parse("content://mms-sms/conversations?simple=true")
            val cursor = context.contentResolver.query(simpleThreadsUri, null, null, null, null)
            cursor?.use { c ->
                val idIdx = c.getColumnIndex(Telephony.Threads._ID).takeIf { it >= 0 }
                    ?: c.getColumnIndex("_id")
                if (idIdx < 0) return@use

                val archivedIdx = listOf("archived", "is_archived", "archive")
                    .firstNotNullOfOrNull { col -> c.getColumnIndex(col).takeIf { it >= 0 } } ?: -1
                val spamIdx = listOf("spam", "is_spam", "spam_type", "junk", "blocked")
                    .firstNotNullOfOrNull { col -> c.getColumnIndex(col).takeIf { it >= 0 } } ?: -1
                val folderIdx = listOf("folder", "box")
                    .firstNotNullOfOrNull { col -> c.getColumnIndex(col).takeIf { it >= 0 } } ?: -1

                while (c.moveToNext()) {
                    val threadId = c.getLong(idIdx)
                    if (threadId <= 0L) continue
                    val folderVal = if (folderIdx >= 0) c.getString(folderIdx)?.lowercase().orEmpty() else ""
                    val isArchived = (archivedIdx >= 0 && c.getInt(archivedIdx) > 0) ||
                        folderVal.contains("archiv")
                    val isSpam = (spamIdx >= 0 && c.getInt(spamIdx) > 0) ||
                        folderVal.contains("spam") || folderVal.contains("junk")
                    if (isArchived || isSpam) {
                        val src = when {
                            isSpam -> MessagingHelper.FOLDER_SOURCE_PROVIDER_SPAM
                            else -> MessagingHelper.FOLDER_SOURCE_PROVIDER_ARCHIVED
                        }
                        states[threadId] = ProviderThreadState(
                            isArchived = isArchived,
                            isSpam = isSpam,
                            folderSource = src
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("CleanSMS", "Thread state query fallback: ${e.message}")
        }
        return states
    }

    /**
     * Best-effort write of the standard Telephony Threads `archived` flag so Google Messages and other
     * Android SMS apps observe Archive/Unarchive state when the provider permits updates.
     */
    fun updateTelephonyThreadArchived(threadId: Long, archived: Boolean): Boolean {
        if (threadId <= 0L) return false
        return try {
            val values = ContentValues().apply {
                put("archived", if (archived) 1 else 0)
            }
            val updated = context.contentResolver.update(
                Uri.parse("content://mms-sms/conversations"),
                values,
                "${Telephony.Threads._ID} = ?",
                arrayOf(threadId.toString())
            )
            updated > 0
        } catch (e: Exception) {
            Log.d("CleanSMS", "Telephony thread archive sync skipped: ${e.message}")
            false
        }
    }

    private fun isNumberBlockedSafely(address: String, cache: MutableMap<String, Boolean>): Boolean {
        val key = PhoneNormalizer.primaryKey(address)
        cache[key]?.let { return it }
        val blocked = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                BlockedNumberContract.canCurrentUserBlockNumbers(context)
            ) {
                BlockedNumberContract.isBlocked(context, address)
            } else {
                false
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
        cache[key] = blocked
        return blocked
    }

    /**
     * Reads all SMS, MMS, and RCS-indicated messages from the device's Telephony ContentProvider,
     * preserves Telephony/cross-app Archived & Spam metadata, and links device Contacts in O(1).
     */
    fun readDeviceSms(contactIndex: Map<String, ContactInfo> = emptyMap()): List<SmsMessageItem> {
        val messageList = ArrayList<SmsMessageItem>(64)
        val threadStates = readDeviceThreadStates()
        val blockedCache = HashMap<String, Boolean>()

        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.READ
            )

            // Query null projection when needed so custom vendor archive/spam columns on SMS rows
            // can also be detected when exposed, falling back cleanly to standard projection.
            val cursor = try {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    null,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )
            } catch (_: Exception) {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )
            }

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val threadIdIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)
                val rowArchivedIdx = listOf("archived", "is_archived")
                    .firstNotNullOfOrNull { col -> it.getColumnIndex(col).takeIf { idx -> idx >= 0 } } ?: -1
                val rowSpamIdx = listOf("spam", "is_spam", "spam_type", "blocked")
                    .firstNotNullOfOrNull { col -> it.getColumnIndex(col).takeIf { idx -> idx >= 0 } } ?: -1

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx).toString()
                    val address = it.getString(addressIdx) ?: "Unknown"
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx)
                    val threadId = if (threadIdIdx >= 0) it.getLong(threadIdIdx) else 0L
                    val isRead = if (readIdx >= 0) it.getInt(readIdx) == 1 else true

                    val tState = threadStates[threadId]
                    val rowArchived = rowArchivedIdx >= 0 && it.getInt(rowArchivedIdx) > 0
                    val rowSpam = rowSpamIdx >= 0 && it.getInt(rowSpamIdx) > 0
                    val isBlocked = isNumberBlockedSafely(address, blockedCache)

                    val isArchived = rowArchived || (tState?.isArchived == true)
                    val isSpamMarked = rowSpam || isBlocked || (tState?.isSpam == true)
                    val folderSource = when {
                        isBlocked -> MessagingHelper.FOLDER_SOURCE_BLOCKED_NUMBER
                        rowSpam || tState?.isSpam == true -> MessagingHelper.FOLDER_SOURCE_PROVIDER_SPAM
                        isArchived -> MessagingHelper.FOLDER_SOURCE_PROVIDER_ARCHIVED
                        else -> ""
                    }

                    // Check for RCS indicators: conversation threads, JIDs, or E.164 phone chats
                    val isRcsChat = threadId > 0 && (address.startsWith("+") || address.contains("@"))

                    val linkedContact = if (contactIndex.isNotEmpty()) {
                        contactIndex[PhoneNormalizer.primaryKey(address)]
                            ?: contactIndex[address.trim().lowercase()]
                    } else null

                    messageList.add(
                        SmsMessageItem(
                            id = id,
                            address = address,
                            body = body,
                            date = date,
                            type = type,
                            threadId = threadId,
                            protocol = if (isRcsChat) MessageProtocol.RCS_CHAT else MessageProtocol.SMS,
                            isRcsChat = isRcsChat,
                            rcsDeliveryStatus = if (isRcsChat) {
                                if (type == Telephony.Sms.MESSAGE_TYPE_SENT || isRead) RcsDeliveryStatus.READ else RcsDeliveryStatus.DELIVERED
                            } else null,
                            contactName = linkedContact?.displayName,
                            contactId = linkedContact?.id,
                            isRead = isRead || type != Telephony.Sms.MESSAGE_TYPE_INBOX,
                            isArchived = isArchived,
                            isSpamMarked = isSpamMarked,
                            folderSource = folderSource
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CleanSMS", "Error reading device SMS/RCS: ${e.message}")
        }

        val mmsList = readDeviceMms(contactIndex, threadStates, blockedCache)
        if (mmsList.isNotEmpty()) {
            messageList.addAll(mmsList)
            messageList.sortByDescending { it.date }
        }
        return messageList
    }

    /**
     * Reads MMS messages from content://mms along with participant address, text/media parts,
     * and inherited thread archive/spam/blocked status.
     */
    fun readDeviceMms(
        contactIndex: Map<String, ContactInfo> = emptyMap(),
        threadStates: Map<Long, ProviderThreadState> = emptyMap(),
        blockedCache: MutableMap<String, Boolean> = HashMap()
    ): List<SmsMessageItem> {
        val mmsItems = ArrayList<SmsMessageItem>(16)
        try {
            val projection = arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.READ,
                Telephony.Mms.SUBJECT
            )
            val cursor = context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Mms.DATE} DESC"
            )
            cursor?.use {
                val idIdx = it.getColumnIndex(Telephony.Mms._ID)
                val threadIdx = it.getColumnIndex(Telephony.Mms.THREAD_ID)
                val dateIdx = it.getColumnIndex(Telephony.Mms.DATE)
                val boxIdx = it.getColumnIndex(Telephony.Mms.MESSAGE_BOX)
                val readIdx = it.getColumnIndex(Telephony.Mms.READ)
                val subIdx = it.getColumnIndex(Telephony.Mms.SUBJECT)

                while (it.moveToNext()) {
                    if (idIdx < 0) continue
                    val mmsId = it.getLong(idIdx)
                    val threadId = if (threadIdx >= 0) it.getLong(threadIdx) else 0L
                    val rawDateSec = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                    val dateMs = if (rawDateSec in 1L..9_999_999_999L) rawDateSec * 1000L else rawDateSec
                    val msgBox = if (boxIdx >= 0) it.getInt(boxIdx) else Telephony.Mms.MESSAGE_BOX_INBOX
                    val isInbox = msgBox == Telephony.Mms.MESSAGE_BOX_INBOX
                    val isRead = if (readIdx >= 0) it.getInt(readIdx) == 1 else true
                    val subject = if (subIdx >= 0) it.getString(subIdx)?.takeIf { s -> s.isNotBlank() } else null

                    val address = queryMmsParticipantAddress(mmsId, isInbox) ?: "Unknown"
                    val (bodyText, attachUri, attachMime, attachName) = queryMmsParts(mmsId)

                    val tState = threadStates[threadId]
                    val isBlocked = isNumberBlockedSafely(address, blockedCache)
                    val isArchived = tState?.isArchived == true
                    val isSpamMarked = isBlocked || (tState?.isSpam == true)
                    val folderSource = when {
                        isBlocked -> MessagingHelper.FOLDER_SOURCE_BLOCKED_NUMBER
                        tState?.isSpam == true -> MessagingHelper.FOLDER_SOURCE_PROVIDER_SPAM
                        isArchived -> MessagingHelper.FOLDER_SOURCE_PROVIDER_ARCHIVED
                        else -> ""
                    }

                    val linkedContact = if (contactIndex.isNotEmpty()) {
                        contactIndex[PhoneNormalizer.primaryKey(address)]
                            ?: contactIndex[address.trim().lowercase()]
                    } else null

                    mmsItems.add(
                        SmsMessageItem(
                            id = "mms_$mmsId",
                            address = address,
                            body = bodyText.ifBlank { subject ?: (attachName?.let { n -> "[Media: $n]" } ?: "[MMS Message]") },
                            date = dateMs,
                            type = if (isInbox) Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT,
                            protocol = MessageProtocol.MMS,
                            threadId = threadId,
                            contactName = linkedContact?.displayName,
                            contactId = linkedContact?.id,
                            isRead = isRead || !isInbox,
                            attachmentUri = attachUri,
                            attachmentMimeType = attachMime,
                            attachmentLabel = attachName,
                            mmsSubject = subject,
                            isArchived = isArchived,
                            isSpamMarked = isSpamMarked,
                            folderSource = folderSource
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CleanSMS", "Error reading device MMS: ${e.message}")
        }
        return mmsItems
    }

    private fun queryMmsParticipantAddress(mmsId: Long, isInbox: Boolean): String? {
        val targetAddrType = if (isInbox) 137 else 151 // 137 = FROM, 151 = TO (PduHeaders)
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        var fallback: String? = null
        try {
            val cursor = context.contentResolver.query(
                addrUri,
                arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
                null,
                null,
                null
            )
            cursor?.use {
                val addrIdx = it.getColumnIndex(Telephony.Mms.Addr.ADDRESS)
                val typeIdx = it.getColumnIndex(Telephony.Mms.Addr.TYPE)
                while (it.moveToNext()) {
                    val addr = if (addrIdx >= 0) it.getString(addrIdx)?.trim() else null
                    val type = if (typeIdx >= 0) it.getInt(typeIdx) else 0
                    if (addr.isNullOrBlank() || addr.equals("insert-address-token", ignoreCase = true)) continue
                    if (type == targetAddrType) return addr
                    if (fallback == null) fallback = addr
                }
            }
        } catch (_: Exception) {
        }
        return fallback
    }

    private data class MmsPartsResult(
        val text: String,
        val attachmentUri: String?,
        val attachmentMimeType: String?,
        val attachmentLabel: String?
    )

    private fun queryMmsParts(mmsId: Long): MmsPartsResult {
        val partUri = Uri.parse("content://mms/part")
        val textParts = StringBuilder()
        var attachUri: String? = null
        var attachMime: String? = null
        var attachLabel: String? = null

        try {
            val cursor = context.contentResolver.query(
                partUri,
                arrayOf(
                    Telephony.Mms.Part._ID,
                    Telephony.Mms.Part.CONTENT_TYPE,
                    Telephony.Mms.Part.TEXT,
                    Telephony.Mms.Part._DATA,
                    Telephony.Mms.Part.NAME,
                    Telephony.Mms.Part.CONTENT_LOCATION
                ),
                "${Telephony.Mms.Part.MSG_ID} = ?",
                arrayOf(mmsId.toString()),
                null
            )
            cursor?.use {
                val idIdx = it.getColumnIndex(Telephony.Mms.Part._ID)
                val ctIdx = it.getColumnIndex(Telephony.Mms.Part.CONTENT_TYPE)
                val textIdx = it.getColumnIndex(Telephony.Mms.Part.TEXT)
                val dataIdx = it.getColumnIndex(Telephony.Mms.Part._DATA)
                val nameIdx = it.getColumnIndex(Telephony.Mms.Part.NAME)
                val clIdx = it.getColumnIndex(Telephony.Mms.Part.CONTENT_LOCATION)

                while (it.moveToNext()) {
                    val partId = if (idIdx >= 0) it.getLong(idIdx) else continue
                    val ct = (if (ctIdx >= 0) it.getString(ctIdx) else null).orEmpty().lowercase()
                    if (ct == "text/plain") {
                        val directText = if (textIdx >= 0) it.getString(textIdx) else null
                        val resolvedText = if (!directText.isNullOrEmpty()) {
                            directText
                        } else if (dataIdx >= 0 && !it.getString(dataIdx).isNullOrBlank()) {
                            readMmsPartTextStream(Uri.parse("content://mms/part/$partId"))
                        } else ""
                        if (resolvedText.isNotBlank()) {
                            if (textParts.isNotEmpty()) textParts.append("\n")
                            textParts.append(resolvedText.trim())
                        }
                    } else if (ct.startsWith("image/") || ct.startsWith("video/") || ct.startsWith("audio/") || ct == "text/x-vcard") {
                        if (attachUri == null) {
                            attachUri = "content://mms/part/$partId"
                            attachMime = ct
                            attachLabel = (if (nameIdx >= 0) it.getString(nameIdx) else null)
                                ?: (if (clIdx >= 0) it.getString(clIdx) else null)
                                ?: "Attachment"
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return MmsPartsResult(textParts.toString(), attachUri, attachMime, attachLabel)
    }

    private fun readMmsPartTextStream(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Marks all SMS & MMS rows in a conversation thread as read/seen in Android Telephony
     * when CleanSMS holds Default SMS or write permissions.
     */
    fun markThreadAsRead(threadId: Long, address: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            var updated = 0
            if (threadId > 0L) {
                updated += context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(threadId.toString())
                )
                updated += context.contentResolver.update(
                    Telephony.Mms.CONTENT_URI,
                    values,
                    "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0",
                    arrayOf(threadId.toString())
                )
            } else if (address.isNotBlank()) {
                updated += context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.READ} = 0",
                    arrayOf(address)
                )
            }
            updated > 0
        } catch (e: Exception) {
            Log.w("CleanSMS", "Could not mark thread read in Telephony: ${e.message}")
            false
        }
    }

    private fun resolveSmsManager(): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (t: Throwable) {
            Log.e("CleanSMS", "Unable to acquire SmsManager: ${t.message}")
            null
        }
    }

    /**
     * Sends an SMS (or RCS-flagged local chat in demo mode; carrier SMS fallback on device when
     * third-party RCS transport is restricted by Android) and inserts it into Telephony Sent box
     * if CleanSMS holds the Default SMS App role.
     */
    fun sendSmsMessage(
        address: String,
        body: String,
        threadId: Long = 0L,
        contactName: String? = null,
        contactId: String? = null,
        preferredProtocol: MessageProtocol = MessageProtocol.SMS,
        isDefaultSmsApp: Boolean,
        hasSendSmsPermission: Boolean,
        isDemoMode: Boolean
    ): SendMessageResult {
        val trimmedAddress = address.trim()
        val trimmedBody = body.trim()
        if (trimmedAddress.isBlank()) {
            return SendMessageResult(success = false, errorMessage = "Enter a valid phone number or contact.")
        }
        if (trimmedBody.isBlank()) {
            return SendMessageResult(success = false, errorMessage = "Message cannot be empty.")
        }

        val now = System.currentTimeMillis()

        if (isDemoMode) {
            val effectiveProto = if (preferredProtocol == MessageProtocol.MMS) MessageProtocol.SMS else preferredProtocol
            val demoItem = SmsMessageItem(
                id = "sent_${now}_${UUID.randomUUID().toString().take(6)}",
                address = trimmedAddress,
                body = trimmedBody,
                date = now,
                type = Telephony.Sms.MESSAGE_TYPE_SENT,
                protocol = effectiveProto,
                threadId = threadId,
                isRcsChat = effectiveProto == MessageProtocol.RCS_CHAT,
                rcsDeliveryStatus = if (effectiveProto == MessageProtocol.RCS_CHAT) RcsDeliveryStatus.SENT else null,
                contactName = contactName,
                contactId = contactId,
                category = SmsCategory.NORMAL,
                isSelected = false,
                isRead = true
            )
            val note = if (effectiveProto == MessageProtocol.RCS_CHAT) {
                "RCS chat reply sent (Demo Mode)"
            } else {
                "SMS sent (Demo Mode)"
            }
            return SendMessageResult(success = true, messageItem = demoItem, statusNote = note)
        }

        if (!hasSendSmsPermission) {
            return SendMessageResult(
                success = false,
                errorMessage = "SEND_SMS permission is required to send messages on this device."
            )
        }

        val smsManager = resolveSmsManager()
            ?: return SendMessageResult(
                success = false,
                errorMessage = "Device telephony SMS service is unavailable."
            )

        return try {
            val parts = smsManager.divideMessage(trimmedBody)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(trimmedAddress, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(trimmedAddress, null, trimmedBody, null, null)
            }

            val resolvedThreadId = if (threadId > 0L) {
                threadId
            } else {
                runCatching { Telephony.Threads.getOrCreateThreadId(context, trimmedAddress) }.getOrDefault(0L)
            }

            var persistedUri: Uri? = null
            if (isDefaultSmsApp) {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, trimmedAddress)
                    put(Telephony.Sms.BODY, trimmedBody)
                    put(Telephony.Sms.DATE, now)
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    if (resolvedThreadId > 0L) {
                        put(Telephony.Sms.THREAD_ID, resolvedThreadId)
                    }
                }
                persistedUri = runCatching {
                    context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                }.getOrNull()
            }

            val rowId = persistedUri?.lastPathSegment ?: "sent_${now}_${UUID.randomUUID().toString().take(6)}"
            val actualProtocol = if (preferredProtocol == MessageProtocol.RCS_CHAT) {
                // Public Android APIs restrict third-party apps from native Jibe RCS transport;
                // transmit reliably over carrier SMS and surface the fallback transparently.
                MessageProtocol.SMS
            } else {
                MessageProtocol.SMS
            }

            val sentItem = SmsMessageItem(
                id = rowId,
                address = trimmedAddress,
                body = trimmedBody,
                date = now,
                type = Telephony.Sms.MESSAGE_TYPE_SENT,
                protocol = actualProtocol,
                threadId = resolvedThreadId,
                isRcsChat = false,
                contactName = contactName,
                contactId = contactId,
                category = SmsCategory.NORMAL,
                isSelected = false,
                isRead = true
            )
            val note = when {
                preferredProtocol == MessageProtocol.RCS_CHAT ->
                    "Sent via carrier SMS fallback (Android limits RCS transport to system Messages)."
                !isDefaultSmsApp ->
                    "SMS dispatched via carrier network. Set CleanSMS as Default SMS app to store sent log in Telephony."
                else -> "SMS sent"
            }
            SendMessageResult(
                success = true,
                messageItem = sentItem,
                persistedUri = persistedUri,
                statusNote = note
            )
        } catch (e: SecurityException) {
            SendMessageResult(
                success = false,
                errorMessage = "Permission denied while sending SMS: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e("CleanSMS", "Error sending SMS: ${e.message}", e)
            SendMessageResult(
                success = false,
                errorMessage = "Failed to send SMS: ${e.message ?: "Unknown carrier error"}"
            )
        }
    }

    /**
     * Sends an MMS message containing text and/or a Photo-Picker-selected media attachment.
     */
    fun sendMmsMessage(
        address: String,
        body: String,
        attachment: ComposeAttachment?,
        threadId: Long = 0L,
        contactName: String? = null,
        contactId: String? = null,
        isDefaultSmsApp: Boolean,
        hasSendSmsPermission: Boolean,
        isDemoMode: Boolean
    ): SendMessageResult {
        val trimmedAddress = address.trim()
        val trimmedBody = body.trim()
        if (trimmedAddress.isBlank()) {
            return SendMessageResult(success = false, errorMessage = "Enter a valid recipient.")
        }
        if (trimmedBody.isBlank() && attachment == null) {
            return SendMessageResult(success = false, errorMessage = "Add text or attach media before sending MMS.")
        }

        val now = System.currentTimeMillis()
        val displayBody = trimmedBody.ifBlank {
            "[MMS Attachment: ${attachment?.displayName ?: "Media"}]"
        }

        if (isDemoMode) {
            val demoItem = SmsMessageItem(
                id = "mms_sent_${now}_${UUID.randomUUID().toString().take(6)}",
                address = trimmedAddress,
                body = displayBody,
                date = now,
                type = Telephony.Sms.MESSAGE_TYPE_SENT,
                protocol = MessageProtocol.MMS,
                threadId = threadId,
                contactName = contactName,
                contactId = contactId,
                category = SmsCategory.NORMAL,
                isSelected = false,
                isRead = true,
                attachmentUri = attachment?.uriString,
                attachmentMimeType = attachment?.mimeType,
                attachmentLabel = attachment?.displayName
            )
            return SendMessageResult(
                success = true,
                messageItem = demoItem,
                statusNote = "MMS message sent (Demo Mode)"
            )
        }

        if (!hasSendSmsPermission) {
            return SendMessageResult(
                success = false,
                errorMessage = "SEND_SMS permission is required to dispatch MMS."
            )
        }

        val smsManager = resolveSmsManager()
            ?: return SendMessageResult(
                success = false,
                errorMessage = "Device telephony MMS service is unavailable."
            )

        return try {
            val attachmentBytes = attachment?.let { att ->
                context.contentResolver.openInputStream(Uri.parse(att.uriString))?.use { it.readBytes() }
            }
            val pduBytes = MmsPduBuilder.buildSendReqPduBytes(
                recipient = trimmedAddress,
                bodyText = trimmedBody.takeIf { it.isNotBlank() },
                attachmentBytes = attachmentBytes,
                attachmentMimeType = attachment?.mimeType ?: "image/jpeg",
                attachmentFileName = attachment?.displayName ?: "media.jpg"
            )
            val preparedPdu = MmsPduBuilder.writePduToCacheFile(context, pduBytes)
            smsManager.sendMultimediaMessage(context, preparedPdu.contentUri, null, null, null)

            val resolvedThreadId = if (threadId > 0L) {
                threadId
            } else {
                runCatching { Telephony.Threads.getOrCreateThreadId(context, trimmedAddress) }.getOrDefault(0L)
            }

            val sentItem = SmsMessageItem(
                id = "mms_sent_${now}_${UUID.randomUUID().toString().take(6)}",
                address = trimmedAddress,
                body = displayBody,
                date = now,
                type = Telephony.Sms.MESSAGE_TYPE_SENT,
                protocol = MessageProtocol.MMS,
                threadId = resolvedThreadId,
                contactName = contactName,
                contactId = contactId,
                category = SmsCategory.NORMAL,
                isSelected = false,
                isRead = true,
                attachmentUri = attachment?.uriString,
                attachmentMimeType = attachment?.mimeType,
                attachmentLabel = attachment?.displayName
            )
            val note = if (isDefaultSmsApp) "MMS dispatched via carrier" else "MMS dispatched via SmsManager"
            SendMessageResult(success = true, messageItem = sentItem, statusNote = note)
        } catch (e: SecurityException) {
            SendMessageResult(success = false, errorMessage = "Permission denied sending MMS: ${e.message}")
        } catch (e: Exception) {
            Log.e("CleanSMS", "Error sending MMS: ${e.message}", e)
            SendMessageResult(success = false, errorMessage = "Failed to send MMS: ${e.message ?: "Carrier error"}")
        }
    }

    /**
     * Links an existing list of messages against a freshly loaded contact index on a background thread.
     */
    fun linkMessagesToContacts(
        messages: List<SmsMessageItem>,
        contactIndex: Map<String, ContactInfo>
    ): List<SmsMessageItem> {
        if (contactIndex.isEmpty()) return messages
        return messages.map { msg ->
            val linked = contactIndex[PhoneNormalizer.primaryKey(msg.address)]
                ?: contactIndex[msg.address.trim().lowercase()]
            if (linked != null && (msg.contactName != linked.displayName || msg.contactId != linked.id)) {
                msg.copy(contactName = linked.displayName, contactId = linked.id)
            } else {
                msg
            }
        }
    }

    /**
     * Permanently deletes messages from the Android Telephony ContentProvider.
     * Requires app to be the Default SMS app.
     */
    fun deleteFromDeviceSms(messageIds: List<String>): Int {
        var deletedCount = 0
        try {
            for (idStr in messageIds) {
                if (idStr.startsWith("mms_")) {
                    val mmsIdLong = idStr.removePrefix("mms_").toLongOrNull() ?: continue
                    val mmsUri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsIdLong)
                    val rows = context.contentResolver.delete(mmsUri, null, null)
                    if (rows > 0) deletedCount += rows
                } else {
                    val idLong = idStr.toLongOrNull() ?: continue
                    val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, idLong)
                    val rows = context.contentResolver.delete(uri, null, null)
                    if (rows > 0) deletedCount += rows
                }
            }
        } catch (e: Exception) {
            Log.e("CleanSMS", "Failed to delete SMS/MMS/RCS: ${e.message}")
        }
        return deletedCount
    }

    /**
     * Testing Feature: Restores previously deleted messages back into Android's Telephony SMS ContentProvider
     * when CleanSMS holds the Default SMS role.
     */
    fun restoreMessagesToDeviceSms(messages: List<SmsMessageItem>): Int {
        var restoredCount = 0
        try {
            for (msg in messages) {
                // Skip synthetic demo IDs when restoring to real telephony provider
                if (msg.id.startsWith("demo_")) continue
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, msg.address)
                    put(Telephony.Sms.BODY, msg.body)
                    put(Telephony.Sms.DATE, msg.date)
                    put(Telephony.Sms.TYPE, msg.type.takeIf { it > 0 } ?: Telephony.Sms.MESSAGE_TYPE_INBOX)
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    if (msg.threadId > 0) {
                        put(Telephony.Sms.THREAD_ID, msg.threadId)
                    }
                }
                val insertedUri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                if (insertedUri != null) restoredCount++
            }
        } catch (e: Exception) {
            Log.e("CleanSMS", "Failed to restore SMS to device provider: ${e.message}")
        }
        return restoredCount
    }

    /**
     * Provides a realistic demo dataset with linked contacts and 30+ messages showcasing:
     * - RCS Chat duplicate retries (with threadId preservation & read receipts)
     * - SMS duplicate message groups from same senders
     * - Expired and active OTPs
     * - Marketing promotions and offers
     * - Phishing / spam alerts
     * - Genuine conversations and receipts
     */
    fun getDemoSmsList(): List<SmsMessageItem> {
        val now = System.currentTimeMillis()
        val oneHour = 3600 * 1000L
        val oneDay = 24 * oneHour

        return listOf(
            // RCS CHAT DUPLICATE GROUP 1: Sarah Miller (Thread #101)
            SmsMessageItem(
                id = "demo_rcs_1a",
                address = "+1 (555) 392-1084",
                contactName = "Sarah Miller",
                contactId = "demo_contact_sarah",
                body = "Hey! Are we still meeting at the cafe at 4:30? Let me know when you're on the way.",
                date = now - (20 * 60 * 1000L),
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 101L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.READ,
                category = SmsCategory.DUPLICATE
            ),
            SmsMessageItem(
                id = "demo_rcs_1b",
                address = "+1 (555) 392-1084",
                contactName = "Sarah Miller",
                contactId = "demo_contact_sarah",
                body = "Hey! Are we still meeting at the cafe at 4:30? Let me know when you're on the way.",
                date = now - (20 * 60 * 1000L) + 15000,
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 101L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.SENT,
                category = SmsCategory.DUPLICATE
            ),

            // RCS GROUP CHAT DUPLICATE GROUP 2: Alex Rivera (Thread #102)
            SmsMessageItem(
                id = "demo_rcs_2a",
                address = "+1 (555) 839-2041",
                contactName = "Alex Rivera",
                contactId = "demo_contact_alex",
                body = "Alex: I reserved the rental cabin for next weekend! Check the confirmation link in the group.",
                date = now - (3 * oneHour),
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 102L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.DELIVERED,
                category = SmsCategory.DUPLICATE
            ),
            SmsMessageItem(
                id = "demo_rcs_2b",
                address = "+1 (555) 839-2041",
                contactName = "Alex Rivera",
                contactId = "demo_contact_alex",
                body = "Alex: I reserved the rental cabin for next weekend! Check the confirmation link in the group.",
                date = now - (3 * oneHour) + 30000,
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 102L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.SENT,
                category = SmsCategory.DUPLICATE
            ),

            // STANDARD SMS DUPLICATE GROUP 1: Amazon Delivery (3 copies received)
            SmsMessageItem(
                id = "demo_dup_1a",
                address = "AMAZON",
                contactName = "Amazon Delivery",
                contactId = "demo_contact_amazon",
                body = "Your order #402-991204 has been delivered to your front door. Thank you for shopping with Amazon!",
                date = now - (2 * oneHour),
                protocol = MessageProtocol.SMS,
                threadId = 201L,
                category = SmsCategory.DUPLICATE
            ),
            SmsMessageItem(
                id = "demo_dup_1b",
                address = "AMAZON",
                contactName = "Amazon Delivery",
                contactId = "demo_contact_amazon",
                body = "Your order #402-991204 has been delivered to your front door. Thank you for shopping with Amazon!",
                date = now - (2 * oneHour) + 60000,
                protocol = MessageProtocol.SMS,
                threadId = 201L,
                category = SmsCategory.DUPLICATE
            ),
            SmsMessageItem(
                id = "demo_dup_1c",
                address = "AMAZON",
                contactName = "Amazon Delivery",
                contactId = "demo_contact_amazon",
                body = "Your order #402-991204 has been delivered to your front door. Thank you for shopping with Amazon!",
                date = now - (2 * oneHour) + 120000,
                protocol = MessageProtocol.SMS,
                threadId = 201L,
                category = SmsCategory.DUPLICATE
            ),

            // DUPLICATE GROUP 2: Uber Ride Updates (2 copies)
            SmsMessageItem(
                id = "demo_dup_2a",
                address = "Uber",
                contactName = "Uber Alerts",
                contactId = "demo_contact_uber",
                body = "Your driver Michael in Toyota Prius (ABC-1234) is arriving in 2 minutes. Please be ready at pickup.",
                date = now - (5 * oneHour),
                protocol = MessageProtocol.SMS,
                threadId = 202L,
                category = SmsCategory.DUPLICATE
            ),
            SmsMessageItem(
                id = "demo_dup_2b",
                address = "Uber",
                contactName = "Uber Alerts",
                contactId = "demo_contact_uber",
                body = "Your driver Michael in Toyota Prius (ABC-1234) is arriving in 2 minutes. Please be ready at pickup.",
                date = now - (5 * oneHour) + 45000,
                protocol = MessageProtocol.SMS,
                threadId = 202L,
                category = SmsCategory.DUPLICATE
            ),

            // OTP 1: Google verification code (Expired, 1 day old)
            SmsMessageItem(
                id = "demo_otp_1",
                address = "Google",
                body = "G-584920 is your Google verification code. Never share your security code with anyone.",
                date = now - (1 * oneDay),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.OTP,
                otpCode = "584920",
                isExpiredOtp = true
            ),

            // OTP 2: Bank 2FA code (Expired, 4 hours old)
            SmsMessageItem(
                id = "demo_otp_2",
                address = "ChaseBank",
                contactName = "Chase Bank",
                contactId = "demo_contact_chase",
                body = "940218 is your Chase Online verification code for debit card authorization. Valid for 10 minutes. Do not share.",
                date = now - (4 * oneHour),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.OTP,
                otpCode = "940218",
                isExpiredOtp = true
            ),

            // OTP 3: WhatsApp registration code (Expired, 3 days old)
            SmsMessageItem(
                id = "demo_otp_3",
                address = "WhatsApp",
                body = "Your WhatsApp code is 382-910. Do not share this code with anyone.",
                date = now - (3 * oneDay),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.OTP,
                otpCode = "382910",
                isExpiredOtp = true
            ),

            // OTP 4: Apple ID code (Active, 3 minutes old)
            SmsMessageItem(
                id = "demo_otp_4",
                address = "Apple",
                body = "Your Apple Account verification code is: 736192. Don't share it with anyone.",
                date = now - (3 * 60 * 1000),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.OTP,
                otpCode = "736192",
                isExpiredOtp = false
            ),

            // PROMOTIONS & OFFERS
            SmsMessageItem(
                id = "demo_promo_1",
                address = "AD-DOMINOS",
                body = "Weekend Special! Flat 50% discount on all large pizzas + free delivery. Use promo code YUMMY50 at checkout. Order now: dom.pizza/deal",
                date = now - (6 * oneHour),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.PROMOTION
            ),
            SmsMessageItem(
                id = "demo_promo_2",
                address = "VK-MYNTRA",
                body = "Mega Fashion Sale is LIVE! Up to 80% off on premium apparel, sneakers & watches. Extra 10% coupon: MEGAFASH. Shop now: myntra.com/sale",
                date = now - (12 * oneHour),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.PROMOTION
            ),
            SmsMessageItem(
                id = "demo_promo_3",
                address = "AX-SEPHORA",
                body = "Exclusive VIP Offer: Claim $25 voucher on purchases over $100 this weekend. Use code BEAUTYVIP. Valid till Sunday midnight.",
                date = now - (2 * oneDay),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.PROMOTION
            ),

            // SPAM & PHISHING
            SmsMessageItem(
                id = "demo_spam_1",
                address = "+1800555019",
                body = "URGENT SECURITY ALERT: Your bank account has been suspended due to suspicious unauthorized debit. Verify immediately at http://bit.ly/bank-unlock-verify to avoid permanent lock.",
                date = now - (8 * oneHour),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.SPAM
            ),
            SmsMessageItem(
                id = "demo_spam_2",
                address = "+1202555014",
                body = "CONGRATULATIONS! You won 2nd prize of $250,000 in international mobile lottery. Claim your prize now by clicking http://tinyurl.com/claim-cash-2026",
                date = now - (1 * oneDay),
                protocol = MessageProtocol.SMS,
                category = SmsCategory.SPAM
            ),

            // NORMAL / PERSONAL CONVERSATIONS (RCS, MMS & SMS)
            SmsMessageItem(
                id = "demo_norm_rcs_sent",
                address = "+1 (555) 789-0123",
                contactName = "Jordan Lee",
                contactId = "demo_contact_jordan",
                body = "Heading out now — are we doing weights or cardio first?",
                date = now - (52 * 60 * 1000),
                type = 2,
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 103L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.READ,
                category = SmsCategory.NORMAL,
                isRead = true
            ),
            SmsMessageItem(
                id = "demo_norm_rcs",
                address = "+1 (555) 789-0123",
                contactName = "Jordan Lee",
                contactId = "demo_contact_jordan",
                body = "Awesome, see you at the gym in 15 mins! (RCS Chat)",
                date = now - (45 * 60 * 1000),
                protocol = MessageProtocol.RCS_CHAT,
                threadId = 103L,
                isRcsChat = true,
                rcsDeliveryStatus = RcsDeliveryStatus.DELIVERED,
                category = SmsCategory.NORMAL,
                isRead = false
            ),
            SmsMessageItem(
                id = "demo_norm_mms_1",
                address = "+1 (555) 642-7780",
                contactName = "Maya Lin",
                contactId = "demo_contact_maya",
                body = "Check out this sunset view from the ridge trail! Let's hike here next Sunday.",
                date = now - (90 * 60 * 1000),
                protocol = MessageProtocol.MMS,
                threadId = 104L,
                category = SmsCategory.NORMAL,
                isRead = false,
                attachmentMimeType = "image/jpeg",
                attachmentLabel = "sunset_ridge_trail.jpg",
                mmsSubject = "Weekend Hike Photos"
            ),
            SmsMessageItem(
                id = "demo_norm_1",
                address = "Mom",
                contactName = "Mom",
                contactId = "demo_contact_mom",
                body = "Hi honey, don't forget Dad's birthday dinner on Saturday evening. Love you!",
                date = now - (1 * oneDay),
                protocol = MessageProtocol.SMS,
                threadId = 205L,
                category = SmsCategory.NORMAL
            ),
            SmsMessageItem(
                id = "demo_norm_2",
                address = "Dr. Clinic",
                contactName = "Dr. Patel Clinic",
                contactId = "demo_contact_clinic",
                body = "Appointment reminder: Routine dental checkup scheduled for Thursday at 10:00 AM.",
                date = now - (3 * oneDay),
                protocol = MessageProtocol.SMS,
                threadId = 206L,
                category = SmsCategory.NORMAL
            ),

            // ARCHIVED CONVERSATION (synced from Google Messages / messaging app archive)
            SmsMessageItem(
                id = "demo_archived_1",
                address = "+1 (555) 210-4488",
                contactName = "Oakwood Leasing",
                contactId = "demo_contact_lease",
                body = "Your 2026 lease renewal paperwork is complete. Reach out anytime if you need a copy.",
                date = now - (7 * oneDay),
                protocol = MessageProtocol.SMS,
                threadId = 301L,
                category = SmsCategory.NORMAL,
                isArchived = true,
                folderSource = MessagingHelper.FOLDER_SOURCE_PROVIDER_ARCHIVED
            ),

            // CROSS-APP SPAM THREAD (marked as spam by Google Messages / external SMS app)
            SmsMessageItem(
                id = "demo_spam_crossapp_1",
                address = "+18885559922",
                body = "Final notice from carrier billing desk: Update your payment details immediately or service pauses.",
                date = now - (4 * oneHour),
                protocol = MessageProtocol.SMS,
                threadId = 302L,
                category = SmsCategory.SPAM,
                isSpamMarked = true,
                folderSource = MessagingHelper.FOLDER_SOURCE_PROVIDER_SPAM
            )
        )
    }

    private class MutableContactAccumulator(
        val id: String,
        val displayName: String,
        val numbers: LinkedHashSet<String> = LinkedHashSet()
    )
}
