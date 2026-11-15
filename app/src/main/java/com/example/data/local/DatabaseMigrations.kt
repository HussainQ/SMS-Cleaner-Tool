package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Airtight, idempotent Room database migrations across all CleanSMS schema versions (v1 -> v4).
 * Uses PRAGMA table_info checks before ALTER TABLE statements so upgrades across multiple skipped
 * releases (e.g. v1.0.0 -> v1.6.0+) never drop user quarantine items, rules, backups, or OAuth state.
 */
object DatabaseMigrations {

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun hasColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
        if (!tableExists(db, tableName)) return false
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex < 0) return false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * v1 -> v2:
     * - Adds RCS / thread / contact metadata columns on quarantine_messages if upgrading from v1.0.x.
     * - Ensures custom_rules and backup_records tables exist.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!tableExists(db, "quarantine_messages")) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `quarantine_messages` (
                        `id` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `categoryString` TEXT NOT NULL,
                        `matchReason` TEXT NOT NULL,
                        `otpCode` TEXT,
                        `protocolString` TEXT NOT NULL DEFAULT 'SMS',
                        `threadId` INTEGER NOT NULL DEFAULT 0,
                        `isRcsChat` INTEGER NOT NULL DEFAULT 0,
                        `contactName` TEXT,
                        `type` INTEGER NOT NULL DEFAULT 1,
                        `quarantinedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            } else {
                if (!hasColumn(db, "quarantine_messages", "protocolString")) {
                    db.execSQL("ALTER TABLE `quarantine_messages` ADD COLUMN `protocolString` TEXT NOT NULL DEFAULT 'SMS'")
                }
                if (!hasColumn(db, "quarantine_messages", "threadId")) {
                    db.execSQL("ALTER TABLE `quarantine_messages` ADD COLUMN `threadId` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "quarantine_messages", "isRcsChat")) {
                    db.execSQL("ALTER TABLE `quarantine_messages` ADD COLUMN `isRcsChat` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "quarantine_messages", "contactName")) {
                    db.execSQL("ALTER TABLE `quarantine_messages` ADD COLUMN `contactName` TEXT")
                }
                if (!hasColumn(db, "quarantine_messages", "type")) {
                    db.execSQL("ALTER TABLE `quarantine_messages` ADD COLUMN `type` INTEGER NOT NULL DEFAULT 1")
                }
                if (!hasColumn(db, "quarantine_messages", "quarantinedAt")) {
                    db.execSQL("ALTER TABLE `quarantine_messages` ADD COLUMN `quarantinedAt` INTEGER NOT NULL DEFAULT 0")
                }
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `custom_rules` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `senderFilter` TEXT NOT NULL,
                    `contactFilter` TEXT NOT NULL DEFAULT '',
                    `keywords` TEXT NOT NULL,
                    `matchAnyKeyword` INTEGER NOT NULL,
                    `olderThanDays` INTEGER,
                    `startDateEpoch` INTEGER,
                    `endDateEpoch` INTEGER,
                    `isEnabled` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `backup_records` (
                    `id` TEXT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `backupTimestamp` INTEGER NOT NULL,
                    `messageCount` INTEGER NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `fileContentJson` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v2 -> v3:
     * - Introduces local encrypted-vault OAuth credentials table (v1.4.0+).
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `oauth_credentials` (
                    `serviceId` TEXT NOT NULL,
                    `serviceName` TEXT NOT NULL,
                    `accountName` TEXT NOT NULL,
                    `accessToken` TEXT NOT NULL,
                    `tokenType` TEXT NOT NULL,
                    `scopes` TEXT NOT NULL,
                    `authorizedAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    `isLocallyStored` INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY(`serviceId`)
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v3 -> v4:
     * - Adds per-contact filtering (`contactFilter`) column to custom_rules.
     * - Adds `deleted_test_snapshots` table for the testing delete-recovery toggle (v1.6.0+).
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!tableExists(db, "custom_rules")) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_rules` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `senderFilter` TEXT NOT NULL,
                        `contactFilter` TEXT NOT NULL DEFAULT '',
                        `keywords` TEXT NOT NULL,
                        `matchAnyKeyword` INTEGER NOT NULL,
                        `olderThanDays` INTEGER,
                        `startDateEpoch` INTEGER,
                        `endDateEpoch` INTEGER,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            } else if (!hasColumn(db, "custom_rules", "contactFilter")) {
                db.execSQL(
                    "ALTER TABLE `custom_rules` ADD COLUMN `contactFilter` TEXT NOT NULL DEFAULT ''"
                )
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `deleted_test_snapshots` (
                    `id` TEXT NOT NULL,
                    `address` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `date` INTEGER NOT NULL,
                    `type` INTEGER NOT NULL DEFAULT 1,
                    `categoryString` TEXT NOT NULL,
                    `matchReason` TEXT NOT NULL,
                    `otpCode` TEXT,
                    `protocolString` TEXT NOT NULL DEFAULT 'SMS',
                    `threadId` INTEGER NOT NULL DEFAULT 0,
                    `isRcsChat` INTEGER NOT NULL DEFAULT 0,
                    `contactName` TEXT,
                    `deletedAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4
    )
}
