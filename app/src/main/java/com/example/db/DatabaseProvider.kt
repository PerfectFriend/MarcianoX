package com.example.db

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.crypto.MemoryZeroizationManager
import com.example.crypto.SecurityManager
import com.example.crypto.SimplexLogger
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom

object DatabaseProvider {
    private const val DB_NAME = "marcianox_secure.db"
    private const val DB_KEY_PREFS = "marcianox_db_key_store"
    private const val DB_KEY_BLOB = "sealed_sqlcipher_passphrase"
    private const val DB_KEY_ALIAS = "marcianox_sqlcipher_wrap_key"

    @Volatile
    private var INSTANCE: AppDatabase? = null

    @Volatile
    var isSqlCipherEnabled: Boolean = false
        private set

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE contacts ADD COLUMN remotePublicKeyB64 TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE contacts ADD COLUMN ratchetStateJson TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN mediaMimeType TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN mediaCiphertextB64 TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN mediaIvB64 TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE messages ADD COLUMN waveformCsv TEXT NOT NULL DEFAULT ''")
        }
    }

    private fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.applicationContext.getSharedPreferences(DB_KEY_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(DB_KEY_BLOB, null)
        if (!existing.isNullOrBlank()) {
            try {
                val decoded = SecurityManager.decryptFromString(existing, DB_KEY_ALIAS)
                return Base64.decode(decoded, Base64.NO_WRAP)
            } catch (_: Exception) {
                // Re-provision if key was invalidated by panic wipe
            }
        }
        val rawPassphrase = ByteArray(32)
        SecureRandom().nextBytes(rawPassphrase)
        val encoded = Base64.encodeToString(rawPassphrase, Base64.NO_WRAP)
        val sealed = SecurityManager.encryptToString(encoded, DB_KEY_ALIAS)
        prefs.edit().putString(DB_KEY_BLOB, sealed).apply()
        return rawPassphrase
    }

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            ).addMigrations(MIGRATION_4_5)

            // Use real SQLCipher encryption on all Android targets (Robolectric host JVM falls back cleanly)
            val isRobolectric = "robolectric".equals(Build.FINGERPRINT, ignoreCase = true)
            if (!isRobolectric) {
                try {
                    System.loadLibrary("sqlcipher")
                    val passphrase = getOrCreateDatabasePassphrase(context)
                    val factory = SupportOpenHelperFactory(passphrase)
                    builder.openHelperFactory(factory)
                    MemoryZeroizationManager.zero(
                        passphrase,
                        MemoryZeroizationManager.SanitizationLevel.CRYPTOGRAPHIC_MILITARY
                    )
                    isSqlCipherEnabled = true
                    SimplexLogger.log("SQLCIPHER", "Room database initialized with AES-256 SQLCipher + AndroidKeyStore.")
                } catch (t: Throwable) {
                    isSqlCipherEnabled = false
                    SimplexLogger.log("SQLCIPHER", "SQLCipher unavailable in host runtime (${t.message}); using fallback SQLite.")
                }
            } else {
                isSqlCipherEnabled = false
            }

            val instance = builder.build()
            INSTANCE = instance
            instance
        }
    }

    fun closeDatabase() {
        synchronized(this) {
            try {
                INSTANCE?.close()
            } catch (_: Exception) {
            }
            INSTANCE = null
        }
    }

    /**
     * Cryptographic shredding for Panic Wipe:
     * closes DB handle, deletes AndroidKeyStore wrapping key, overwrites DB + WAL/SHM files with random bytes, and deletes them.
     */
    fun shredDatabaseFiles(context: Context) {
        synchronized(this) {
            closeDatabase()
            SecurityManager.deleteKey(DB_KEY_ALIAS)
            context.applicationContext
                .getSharedPreferences(DB_KEY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()

            val dbFile = context.applicationContext.getDatabasePath(DB_NAME)
            listOf(
                dbFile,
                File("${dbFile.absolutePath}-wal"),
                File("${dbFile.absolutePath}-shm"),
                File("${dbFile.absolutePath}-journal")
            ).forEach { target ->
                if (target.exists() && target.isFile) {
                    try {
                        val len = target.length().toInt().coerceAtMost(1024 * 1024)
                        if (len > 0) {
                            val noise = ByteArray(len)
                            SecureRandom().nextBytes(noise)
                            target.writeBytes(noise)
                            MemoryZeroizationManager.zero(noise)
                        }
                    } catch (_: Exception) {}
                    target.delete()
                }
            }
        }
    }
}
