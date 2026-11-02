package com.example.cloud

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.BackupRecordEntity
import com.example.model.BackupHistoryRecord
import com.example.model.BackupMessageDto
import com.example.model.CloudBackupPayload
import com.example.model.CustomFilterRule
import com.example.model.SmsMessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CloudBackupManager(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.cleanSmsDao()

    /**
     * Serializes quarantined or cleaned SMS messages into a structured, easily restorable JSON backup.
     */
    suspend fun createCloudBackup(
        messages: List<SmsMessageItem>,
        rules: List<CustomFilterRule>,
        provider: String = "Google Drive"
    ): Result<BackupHistoryRecord> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
            val fileName = "CleanSMS_Backup_${provider.replace(" ", "")}_$dateStr.json"

            val jsonRoot = JSONObject()
            jsonRoot.put("version", 1)
            jsonRoot.put("app", "CleanSMS")
            jsonRoot.put("provider", provider)
            jsonRoot.put("backupDate", timestamp)
            jsonRoot.put("totalCount", messages.size)

            val messagesArray = JSONArray()
            for (m in messages) {
                val mObj = JSONObject()
                mObj.put("id", m.id)
                mObj.put("address", m.address)
                mObj.put("body", m.body)
                mObj.put("date", m.date)
                mObj.put("category", m.category.name)
                mObj.put("protocol", m.protocol.name)
                mObj.put("threadId", m.threadId)
                mObj.put("isRcsChat", m.isRcsChat)
                mObj.put("matchReason", m.matchReason)
                mObj.put("otpCode", m.otpCode ?: "")
                messagesArray.put(mObj)
            }
            jsonRoot.put("messages", messagesArray)

            val rulesArray = JSONArray()
            for (r in rules) {
                val rObj = JSONObject()
                rObj.put("id", r.id)
                rObj.put("name", r.name)
                rObj.put("senderFilter", r.senderFilter)
                rObj.put("keywords", r.keywords)
                rObj.put("matchAnyKeyword", r.matchAnyKeyword)
                if (r.olderThanDays != null) rObj.put("olderThanDays", r.olderThanDays)
                if (r.startDateEpoch != null) rObj.put("startDateEpoch", r.startDateEpoch)
                if (r.endDateEpoch != null) rObj.put("endDateEpoch", r.endDateEpoch)
                rObj.put("isEnabled", r.isEnabled)
                rObj.put("createdAt", r.createdAt)
                rulesArray.put(rObj)
            }
            jsonRoot.put("rules", rulesArray)

            val jsonString = jsonRoot.toString(2)
            val sizeBytes = jsonString.toByteArray(Charsets.UTF_8).size.toLong()

            val record = BackupRecordEntity(
                id = UUID.randomUUID().toString(),
                provider = provider,
                backupTimestamp = timestamp,
                messageCount = messages.size,
                fileName = fileName,
                sizeBytes = sizeBytes,
                fileContentJson = jsonString
            )

            dao.insertBackup(record)

            Result.success(
                BackupHistoryRecord(
                    id = record.id,
                    provider = record.provider,
                    backupTimestamp = record.backupTimestamp,
                    messageCount = record.messageCount,
                    fileName = record.fileName,
                    sizeBytes = record.sizeBytes,
                    fileContentJson = record.fileContentJson
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses a cloud backup JSON and restores the message items and rules.
     */
    fun parseBackupJson(jsonString: String): Result<CloudBackupPayload> {
        return try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            val app = root.optString("app", "CleanSMS")
            val provider = root.optString("provider", "Google Drive")
            val backupDate = root.optLong("backupDate", System.currentTimeMillis())
            val totalCount = root.optInt("totalCount", 0)

            val messagesList = mutableListOf<BackupMessageDto>()
            val msgArray = root.optJSONArray("messages")
            if (msgArray != null) {
                for (i in 0 until msgArray.length()) {
                    val item = msgArray.getJSONObject(i)
                    messagesList.add(
                        BackupMessageDto(
                            id = item.optString("id", UUID.randomUUID().toString()),
                            address = item.optString("address", "Unknown"),
                            body = item.optString("body", ""),
                            date = item.optLong("date", System.currentTimeMillis()),
                            category = item.optString("category", "NORMAL"),
                            matchReason = item.optString("matchReason", ""),
                            otpCode = item.optString("otpCode").takeIf { it.isNotEmpty() },
                            protocol = item.optString("protocol", "SMS"),
                            threadId = item.optLong("threadId", 0L),
                            isRcsChat = item.optBoolean("isRcsChat", false)
                        )
                    )
                }
            }

            val rulesList = mutableListOf<CustomFilterRule>()
            val rulesArray = root.optJSONArray("rules")
            if (rulesArray != null) {
                for (i in 0 until rulesArray.length()) {
                    val r = rulesArray.getJSONObject(i)
                    rulesList.add(
                        CustomFilterRule(
                            id = r.optString("id", UUID.randomUUID().toString()),
                            name = r.optString("name", "Restored Rule"),
                            senderFilter = r.optString("senderFilter", ""),
                            keywords = r.optString("keywords", ""),
                            matchAnyKeyword = r.optBoolean("matchAnyKeyword", true),
                            olderThanDays = if (r.has("olderThanDays")) r.optInt("olderThanDays") else null,
                            startDateEpoch = if (r.has("startDateEpoch")) r.optLong("startDateEpoch") else null,
                            endDateEpoch = if (r.has("endDateEpoch")) r.optLong("endDateEpoch") else null,
                            isEnabled = r.optBoolean("isEnabled", true),
                            createdAt = r.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            Result.success(
                CloudBackupPayload(
                    version = version,
                    app = app,
                    backupDate = backupDate,
                    provider = provider,
                    totalCount = totalCount,
                    messages = messagesList,
                    rules = rulesList
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
