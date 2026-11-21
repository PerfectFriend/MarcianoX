package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Represents a single unidirectional (simplex) message queue in the decentralized architecture.
 * In this decentralized protocol, bidirectional communication is decoupled into two independent,
 * single-direction queues:
 * - INBOUND: Receives incoming encrypted packets from a peer relay without exposing recipient identity.
 * - OUTBOUND: Transmits outgoing encrypted packets to a peer relay without linking to the sender's identity.
 */
@Entity(
    tableName = "unidirectional_queues",
    indices = [
        Index(value = ["queueUri"], unique = true),
        Index(value = ["contactId", "direction"]),
        Index(value = ["status"])
    ]
)
data class UnidirectionalQueue(
    @PrimaryKey val queueUri: String, // Unique decentralized queue address, e.g. "simplex://onion_host/q-inbound-1234"
    val contactId: Int, // Associated peer/contact identifier
    val direction: String, // "INBOUND" or "OUTBOUND"
    val serverAddress: String, // Onion or relay host address (e.g. "simplex.onion")
    val queueToken: String, // Authorization token: read token for INBOUND, write token for OUTBOUND
    val sharedSecretHex: String = "", // Ratchet/channel encryption key associated with this queue
    val status: String = "ACTIVE", // "ACTIVE", "HANDSHAKE_PENDING", "SUSPENDED", "DRAINED", "EXPIRED"
    val lastSequenceNumber: Long = 0L, // Monotonic packet sequence counter
    val lastActiveTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Represents a single encrypted payload or packet buffered inside a unidirectional queue.
 */
@Entity(
    tableName = "queue_messages",
    indices = [
        Index(value = ["queueUri"]),
        Index(value = ["deliveryStatus"]),
        Index(value = ["sequenceNumber"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = UnidirectionalQueue::class,
            parentColumns = ["queueUri"],
            childColumns = ["queueUri"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class QueueMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val queueUri: String, // Target unidirectional queue URI
    val sequenceNumber: Long, // Monotonic packet sequence number in queue
    val encryptedPayload: String, // AES-GCM encrypted ciphertext
    val iv: String, // Initialization vector
    val senderDigest: String = "", // Cryptographic sender authentication digest
    val deliveryStatus: String = "QUEUED", // "QUEUED", "TRANSMITTED", "ACKNOWLEDGED", "FAILED", "BURNED"
    val isAcknowledged: Boolean = false,
    val expiryTimestamp: Long = 0L, // Expiration timestamp for automatic undelivered shredding (0 = disabled)
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Data Access Object (DAO) for managing unidirectional message queues and buffered queue packets
 * in the Room database, adhering to decentralized unidirectional messaging constraints.
 */
@Dao
interface UnidirectionalQueueDao {

    // --- Unidirectional Queue Management ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(queue: UnidirectionalQueue): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueues(queues: List<UnidirectionalQueue>)

    @Update
    suspend fun updateQueue(queue: UnidirectionalQueue)

    @Delete
    suspend fun deleteQueue(queue: UnidirectionalQueue)

    @Query("SELECT * FROM unidirectional_queues ORDER BY createdAt DESC")
    fun getAllQueues(): Flow<List<UnidirectionalQueue>>

    @Query("SELECT * FROM unidirectional_queues WHERE queueUri = :queueUri LIMIT 1")
    fun getQueueByUri(queueUri: String): Flow<UnidirectionalQueue?>

    @Query("SELECT * FROM unidirectional_queues WHERE queueUri = :queueUri LIMIT 1")
    suspend fun getQueueByUriOnce(queueUri: String): UnidirectionalQueue?

    @Query("SELECT * FROM unidirectional_queues WHERE contactId = :contactId ORDER BY direction ASC")
    fun getQueuesForContact(contactId: Int): Flow<List<UnidirectionalQueue>>

    @Query("SELECT * FROM unidirectional_queues WHERE contactId = :contactId ORDER BY direction ASC")
    suspend fun getQueuesForContactOnce(contactId: Int): List<UnidirectionalQueue>

    @Query("SELECT * FROM unidirectional_queues WHERE contactId = :contactId AND direction = 'INBOUND' LIMIT 1")
    fun getInboundQueue(contactId: Int): Flow<UnidirectionalQueue?>

    @Query("SELECT * FROM unidirectional_queues WHERE contactId = :contactId AND direction = 'OUTBOUND' LIMIT 1")
    fun getOutboundQueue(contactId: Int): Flow<UnidirectionalQueue?>

    @Query("SELECT * FROM unidirectional_queues WHERE contactId = :contactId AND direction = 'INBOUND' LIMIT 1")
    suspend fun getInboundQueueOnce(contactId: Int): UnidirectionalQueue?

    @Query("SELECT * FROM unidirectional_queues WHERE contactId = :contactId AND direction = 'OUTBOUND' LIMIT 1")
    suspend fun getOutboundQueueOnce(contactId: Int): UnidirectionalQueue?

    @Query("SELECT * FROM unidirectional_queues WHERE direction = :direction ORDER BY lastActiveTimestamp DESC")
    fun getQueuesByDirection(direction: String): Flow<List<UnidirectionalQueue>>

    @Query("SELECT * FROM unidirectional_queues WHERE status = :status ORDER BY lastActiveTimestamp DESC")
    fun getQueuesByStatus(status: String): Flow<List<UnidirectionalQueue>>

    @Query("UPDATE unidirectional_queues SET status = :status WHERE queueUri = :queueUri")
    suspend fun updateQueueStatus(queueUri: String, status: String)

    @Query("UPDATE unidirectional_queues SET lastSequenceNumber = :seq, lastActiveTimestamp = :timestamp WHERE queueUri = :queueUri")
    suspend fun updateQueueProgress(queueUri: String, seq: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM unidirectional_queues WHERE queueUri = :queueUri")
    suspend fun deleteQueueByUri(queueUri: String)

    @Query("DELETE FROM unidirectional_queues WHERE contactId = :contactId")
    suspend fun deleteQueuesForContact(contactId: Int)

    @Query("DELETE FROM unidirectional_queues")
    suspend fun clearAllQueues()

    // --- Queue Message Packets Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: QueueMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<QueueMessage>)

    @Update
    suspend fun updateMessage(message: QueueMessage)

    @Query("SELECT * FROM queue_messages WHERE queueUri = :queueUri ORDER BY sequenceNumber ASC")
    fun getMessagesForQueue(queueUri: String): Flow<List<QueueMessage>>

    @Query("SELECT * FROM queue_messages WHERE queueUri = :queueUri ORDER BY sequenceNumber ASC")
    suspend fun getMessagesForQueueOnce(queueUri: String): List<QueueMessage>

    @Query("SELECT * FROM queue_messages WHERE queueUri = :queueUri AND deliveryStatus = 'QUEUED' ORDER BY sequenceNumber ASC")
    suspend fun getPendingOutboundMessages(queueUri: String): List<QueueMessage>

    @Query("SELECT * FROM queue_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): QueueMessage?

    @Query("SELECT MAX(sequenceNumber) FROM queue_messages WHERE queueUri = :queueUri")
    suspend fun getHighestSequenceNumber(queueUri: String): Long?

    @Query("UPDATE queue_messages SET deliveryStatus = :status, isAcknowledged = :acknowledged WHERE id = :id")
    suspend fun updateMessageDeliveryStatus(id: Long, status: String, acknowledged: Boolean)

    @Query("UPDATE queue_messages SET isAcknowledged = 1, deliveryStatus = 'ACKNOWLEDGED' WHERE id = :id")
    suspend fun markMessageAcknowledged(id: Long)

    @Query("DELETE FROM queue_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM queue_messages WHERE queueUri = :queueUri")
    suspend fun deleteMessagesForQueue(queueUri: String)

    @Query("DELETE FROM queue_messages WHERE expiryTimestamp > 0 AND expiryTimestamp <= :currentTimestamp AND isAcknowledged = 0")
    suspend fun purgeExpiredMessages(currentTimestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM queue_messages")
    suspend fun clearAllQueueMessages()
}
