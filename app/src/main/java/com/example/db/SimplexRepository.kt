package com.example.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class SimplexRepository(
    private val simplexDao: SimplexDao,
    private val queueDao: UnidirectionalQueueDao? = null
) {

    val allContacts: Flow<List<ChatContact>> = simplexDao.getAllContacts()

    fun getMessagesForContact(contactId: Int): Flow<List<ChatMessage>> {
        return simplexDao.getMessagesForContact(contactId)
    }

    suspend fun getContactById(id: Int): ChatContact? {
        return simplexDao.getContactById(id)
    }

    suspend fun insertContact(contact: ChatContact): Long {
        return simplexDao.insertContact(contact)
    }

    suspend fun deleteContact(contact: ChatContact) {
        simplexDao.deleteContact(contact)
    }

    suspend fun updateContactRatchetState(contactId: Int, stateJson: String) {
        simplexDao.updateContactRatchetState(contactId, stateJson)
    }

    suspend fun insertMessage(message: ChatMessage): Long {
        return simplexDao.insertMessage(message)
    }

    suspend fun deleteMessageById(id: Int) {
        simplexDao.deleteMessageById(id)
    }

    suspend fun getPendingMessages(): List<ChatMessage> {
        return simplexDao.getPendingMessages()
    }

    suspend fun updateDeliveryStatus(id: Int, status: String) {
        simplexDao.updateDeliveryStatus(id, status)
    }

    suspend fun timeWarpPendingMessages(millis: Long) {
        simplexDao.timeWarpPendingMessages(millis)
    }

    // --- Unidirectional Message Queues ---

    val allQueues: Flow<List<UnidirectionalQueue>> =
        queueDao?.getAllQueues() ?: emptyFlow()

    fun getQueuesForContact(contactId: Int): Flow<List<UnidirectionalQueue>> {
        return queueDao?.getQueuesForContact(contactId) ?: emptyFlow()
    }

    fun getInboundQueue(contactId: Int): Flow<UnidirectionalQueue?> {
        return queueDao?.getInboundQueue(contactId) ?: emptyFlow()
    }

    fun getOutboundQueue(contactId: Int): Flow<UnidirectionalQueue?> {
        return queueDao?.getOutboundQueue(contactId) ?: emptyFlow()
    }

    suspend fun insertQueue(queue: UnidirectionalQueue): Long {
        return queueDao?.insertQueue(queue) ?: -1L
    }

    suspend fun updateQueue(queue: UnidirectionalQueue) {
        queueDao?.updateQueue(queue)
    }

    suspend fun updateQueueStatus(queueUri: String, status: String) {
        queueDao?.updateQueueStatus(queueUri, status)
    }

    suspend fun deleteQueue(queue: UnidirectionalQueue) {
        queueDao?.deleteQueue(queue)
    }

    suspend fun deleteQueueByUri(queueUri: String) {
        queueDao?.deleteQueueByUri(queueUri)
    }

    fun getMessagesForQueue(queueUri: String): Flow<List<QueueMessage>> {
        return queueDao?.getMessagesForQueue(queueUri) ?: emptyFlow()
    }

    suspend fun insertQueueMessage(message: QueueMessage): Long {
        return queueDao?.insertMessage(message) ?: -1L
    }

    suspend fun updateQueueMessageDelivery(id: Long, status: String, isAcknowledged: Boolean) {
        queueDao?.updateMessageDeliveryStatus(id, status, isAcknowledged)
    }

    suspend fun purgeExpiredQueueMessages(currentTime: Long = System.currentTimeMillis()): Int {
        return queueDao?.purgeExpiredMessages(currentTime) ?: 0
    }

    suspend fun clearAllData() {
        simplexDao.clearAllMessages()
        simplexDao.clearAllContacts()
        queueDao?.clearAllQueueMessages()
        queueDao?.clearAllQueues()
    }
}
