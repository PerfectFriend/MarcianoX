package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UnidirectionalQueueDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var queueDao: UnidirectionalQueueDao
    private lateinit var simplexDao: SimplexDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        queueDao = database.unidirectionalQueueDao()
        simplexDao = database.simplexDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveUnidirectionalQueues() = runBlocking {
        val inboundUri = "simplex://onion1.onion/q-inbound-101"
        val outboundUri = "simplex://onion1.onion/q-outbound-102"
        val contactId = 42

        val inboundQueue = UnidirectionalQueue(
            queueUri = inboundUri,
            contactId = contactId,
            direction = "INBOUND",
            serverAddress = "onion1.onion",
            queueToken = "read_token_xyz",
            sharedSecretHex = "secret_aabbcc",
            status = "ACTIVE"
        )

        val outboundQueue = UnidirectionalQueue(
            queueUri = outboundUri,
            contactId = contactId,
            direction = "OUTBOUND",
            serverAddress = "onion1.onion",
            queueToken = "write_token_123",
            sharedSecretHex = "secret_aabbcc",
            status = "ACTIVE"
        )

        queueDao.insertQueue(inboundQueue)
        queueDao.insertQueue(outboundQueue)

        // Verify retrieval by URI
        val retrievedInbound = queueDao.getQueueByUriOnce(inboundUri)
        assertNotNull(retrievedInbound)
        assertEquals("INBOUND", retrievedInbound?.direction)
        assertEquals("read_token_xyz", retrievedInbound?.queueToken)

        // Verify contact queues retrieval
        val contactQueues = queueDao.getQueuesForContactOnce(contactId)
        assertEquals(2, contactQueues.size)

        // Verify Inbound & Outbound specific queries
        val inQueue = queueDao.getInboundQueueOnce(contactId)
        val outQueue = queueDao.getOutboundQueueOnce(contactId)
        assertEquals(inboundUri, inQueue?.queueUri)
        assertEquals(outboundUri, outQueue?.queueUri)
    }

    @Test
    fun testUpdateQueueStatusAndProgress() = runBlocking {
        val queueUri = "simplex://onion2.onion/q-progress-202"
        val queue = UnidirectionalQueue(
            queueUri = queueUri,
            contactId = 7,
            direction = "OUTBOUND",
            serverAddress = "onion2.onion",
            queueToken = "token_abc",
            status = "HANDSHAKE_PENDING",
            lastSequenceNumber = 0L
        )
        queueDao.insertQueue(queue)

        // Update status
        queueDao.updateQueueStatus(queueUri, "ACTIVE")
        var updated = queueDao.getQueueByUriOnce(queueUri)
        assertEquals("ACTIVE", updated?.status)

        // Update sequence progress
        queueDao.updateQueueProgress(queueUri, seq = 5L, timestamp = 1700000000L)
        updated = queueDao.getQueueByUriOnce(queueUri)
        assertEquals(5L, updated?.lastSequenceNumber)
        assertEquals(1700000000L, updated?.lastActiveTimestamp)
    }

    @Test
    fun testQueueMessageEnqueueAndDeliveryFlow() = runBlocking {
        val queueUri = "simplex://onion3.onion/q-stream-303"
        val queue = UnidirectionalQueue(
            queueUri = queueUri,
            contactId = 99,
            direction = "OUTBOUND",
            serverAddress = "onion3.onion",
            queueToken = "token_303"
        )
        queueDao.insertQueue(queue)

        val packet1 = QueueMessage(
            queueUri = queueUri,
            sequenceNumber = 1L,
            encryptedPayload = "CIPHERTEXT_PACKET_1",
            iv = "IV_1",
            deliveryStatus = "QUEUED",
            isAcknowledged = false
        )
        val packet2 = QueueMessage(
            queueUri = queueUri,
            sequenceNumber = 2L,
            encryptedPayload = "CIPHERTEXT_PACKET_2",
            iv = "IV_2",
            deliveryStatus = "QUEUED",
            isAcknowledged = false
        )

        val id1 = queueDao.insertMessage(packet1)
        val id2 = queueDao.insertMessage(packet2)

        val messages = queueDao.getMessagesForQueueOnce(queueUri)
        assertEquals(2, messages.size)

        val pending = queueDao.getPendingOutboundMessages(queueUri)
        assertEquals(2, pending.size)

        // Acknowledge packet 1
        queueDao.markMessageAcknowledged(id1)
        val afterAck = queueDao.getMessageById(id1)
        assertTrue(afterAck?.isAcknowledged == true)
        assertEquals("ACKNOWLEDGED", afterAck?.deliveryStatus)

        // Check highest sequence number
        val highestSeq = queueDao.getHighestSequenceNumber(queueUri)
        assertEquals(2L, highestSeq)
    }

    @Test
    fun testPurgeExpiredMessages() = runBlocking {
        val queueUri = "simplex://onion4.onion/q-expiry-404"
        val queue = UnidirectionalQueue(
            queueUri = queueUri,
            contactId = 11,
            direction = "OUTBOUND",
            serverAddress = "onion4.onion",
            queueToken = "token_404"
        )
        queueDao.insertQueue(queue)

        val now = 10000L
        val expiredPacket = QueueMessage(
            queueUri = queueUri,
            sequenceNumber = 1L,
            encryptedPayload = "OLD_PACKET",
            iv = "IV_EXPIRED",
            deliveryStatus = "QUEUED",
            isAcknowledged = false,
            expiryTimestamp = 5000L // in the past
        )
        val activePacket = QueueMessage(
            queueUri = queueUri,
            sequenceNumber = 2L,
            encryptedPayload = "FRESH_PACKET",
            iv = "IV_FRESH",
            deliveryStatus = "QUEUED",
            isAcknowledged = false,
            expiryTimestamp = 20000L // in the future
        )

        queueDao.insertMessage(expiredPacket)
        queueDao.insertMessage(activePacket)

        val purgedCount = queueDao.purgeExpiredMessages(currentTimestamp = now)
        assertEquals(1, purgedCount)

        val remaining = queueDao.getMessagesForQueueOnce(queueUri)
        assertEquals(1, remaining.size)
        assertEquals(2L, remaining[0].sequenceNumber)
    }

    @Test
    fun testDeleteQueueAndCascadeMessages() = runBlocking {
        val queueUri = "simplex://onion5.onion/q-delete-505"
        val queue = UnidirectionalQueue(
            queueUri = queueUri,
            contactId = 15,
            direction = "INBOUND",
            serverAddress = "onion5.onion",
            queueToken = "token_505"
        )
        queueDao.insertQueue(queue)

        queueDao.insertMessage(
            QueueMessage(
                queueUri = queueUri,
                sequenceNumber = 1L,
                encryptedPayload = "CASCADE_TEST",
                iv = "IV_CASCADE"
            )
        )

        // Delete queue
        queueDao.deleteQueueByUri(queueUri)

        val queues = queueDao.getAllQueues().first()
        assertTrue(queues.none { it.queueUri == queueUri })

        val messages = queueDao.getMessagesForQueueOnce(queueUri)
        assertTrue(messages.isEmpty())
    }
}
