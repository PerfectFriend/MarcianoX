package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "contacts")
data class ChatContact(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val aliasName: String,
    val inboundQueueUri: String,
    val outboundQueueUri: String,
    val sharedSecretHex: String,
    val onionAddress: String,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(),
    val profileAlias: String = "",
    val remotePublicKeyB64: String = "",
    val ratchetStateJson: String = ""
)

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactId: Int,
    val sender: String, // "ME" or "PEER" or "SYSTEM"
    val encryptedBody: String,
    val iv: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val ratchetIndex: Int = 0,
    val deliveryStatus: String = "DELIVERED", // "PENDING", "DELIVERED", "FAILED"
    val expiryMinutes: Int = 0, // Undelivered burn expiry in minutes (0 means disabled)
    val mediaMimeType: String = "",
    val mediaCiphertextB64: String = "",
    val mediaIvB64: String = "",
    val waveformCsv: String = ""
)

@Dao
interface SimplexDao {
    @Query("SELECT * FROM contacts ORDER BY aliasName ASC")
    fun getAllContacts(): Flow<List<ChatContact>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: Int): ChatContact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ChatContact): Long

    @Delete
    suspend fun deleteContact(contact: ChatContact)

    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp ASC")
    fun getMessagesForContact(contactId: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Int)

    @Query("DELETE FROM messages WHERE contactId = :contactId")
    suspend fun deleteMessagesForContact(contactId: Int)

    @Query("DELETE FROM contacts")
    suspend fun clearAllContacts()

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("SELECT * FROM messages WHERE deliveryStatus = 'PENDING'")
    suspend fun getPendingMessages(): List<ChatMessage>

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :id")
    suspend fun updateDeliveryStatus(id: Int, status: String)

    @Query("UPDATE messages SET timestamp = timestamp - :millis WHERE deliveryStatus = 'PENDING'")
    suspend fun timeWarpPendingMessages(millis: Long)

    @Query("UPDATE contacts SET ratchetStateJson = :stateJson WHERE id = :contactId")
    suspend fun updateContactRatchetState(contactId: Int, stateJson: String)
}

@Database(
    entities = [
        ChatContact::class,
        ChatMessage::class,
        UnidirectionalQueue::class,
        QueueMessage::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun simplexDao(): SimplexDao
    abstract fun unidirectionalQueueDao(): UnidirectionalQueueDao
}
