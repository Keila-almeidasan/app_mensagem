package com.example.app_mensagem.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.example.app_mensagem.data.local.ConversationDao
import com.example.app_mensagem.data.local.MessageDao
import com.example.app_mensagem.data.model.Conversation
import com.example.app_mensagem.data.model.Group
import com.example.app_mensagem.data.model.Message
import com.example.app_mensagem.data.model.User
import com.example.app_mensagem.services.EncryptionUtils
import com.example.app_mensagem.services.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val context: Context
) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    private var conversationsListener: ValueEventListener? = null
    private var conversationsRef: DatabaseReference? = null

    // --- PRESENÇA ---
    fun setUserPresence() {
        val uid = auth.currentUser?.uid ?: return
        val userRef = database.getReference("users/$uid")
        val connectedRef = database.getReference(".info/connected")

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    userRef.child("isOnline").setValue(true)
                    userRef.child("isOnline").onDisconnect().setValue(false)
                    userRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun observeUserPresence(userId: String): Flow<Pair<Boolean, Long>> = callbackFlow {
        val ref = database.getReference("users/$userId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                trySend(isOnline to lastSeen)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // --- TYPING STATUS ---
    fun setTypingStatus(conversationId: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        database.getReference("typing/$conversationId/$uid").setValue(if (isTyping) true else null)
    }

    fun observeTypingStatus(conversationId: String): Flow<List<String>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: ""
        val ref = database.getReference("typing/$conversationId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val typingUsers = snapshot.children
                    .filter { it.key != uid }
                    .mapNotNull { it.key }
                trySend(typingUsers)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun startConversationListener() {
        val userId = auth.currentUser?.uid ?: return
        if (conversationsRef != null) return

        conversationsRef = database.getReference("user-conversations").child(userId)
        conversationsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val conversations = snapshot.children.mapNotNull { it.getValue(Conversation::class.java) }
                CoroutineScope(Dispatchers.IO).launch {
                    conversations.forEach { conv -> 
                        val existing = conversationDao.getConversationById(conv.id)
                        // Se a mensagem for nova e o timestamp mudou, dispara notificação
                        if (existing != null && conv.timestamp > existing.timestamp) {
                            NotificationHelper.showNotification(context, conv.name, conv.lastMessage, conv.id)
                        }
                        conversationDao.insertOrUpdate(conv) 
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        conversationsRef?.addValueEventListener(conversationsListener!!)
    }

    fun stopConversationListener() {
        conversationsListener?.let { conversationsRef?.removeEventListener(it) }
        conversationsRef = null
        conversationsListener = null
    }

    fun getConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations()
    }

    suspend fun syncUserConversations() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val ref = database.getReference("user-conversations").child(userId)
            val snapshot = ref.get().await()
            val conversations = snapshot.children.mapNotNull { it.getValue(Conversation::class.java) }
            conversations.forEach { conversationDao.insertOrUpdate(it) }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Falha na sincronização: ${e.message}")
        }
    }

    suspend fun clearLocalCache() {
        conversationDao.clearAll()
        messageDao.clearAll()
    }

    suspend fun getUsers(): List<User> {
        val currentUserId = auth.currentUser?.uid
        val usersRef = database.getReference("users")
        val snapshot = usersRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(User::class.java) }.filter { it.uid != currentUserId }
    }

    suspend fun createGroup(name: String, memberIds: List<String>, imageUri: Uri? = null) {
        val currentUserId = auth.currentUser?.uid ?: return
        val currentUser = database.getReference("users").child(currentUserId).get().await().getValue(User::class.java)

        var imageUrl: String? = null
        if (imageUri != null) {
            try { imageUrl = CloudinaryHelper.uploadFile(imageUri, "IMAGE") } 
            catch (e: Exception) { Log.e("ChatRepository", "Erro upload grupo: ${e.message}") }
        }

        val groupsRef = database.getReference("groups")
        val groupId = groupsRef.push().key ?: return
        val allMemberIds = (memberIds + currentUserId).distinct()
        val membersMap = allMemberIds.associateWith { true }

        val group = Group(id = groupId, name = name, creatorId = currentUserId, members = membersMap, profilePictureUrl = imageUrl)
        groupsRef.child(groupId).setValue(group).await()

        val groupConversation = Conversation(id = groupId, name = name, profilePictureUrl = imageUrl, lastMessage = "Grupo criado por ${currentUser?.name ?: "alguém"}!", timestamp = System.currentTimeMillis(), isGroup = true)

        allMemberIds.forEach { memberId ->
            database.getReference("user-conversations").child(memberId).child(groupId).setValue(groupConversation)
        }
    }

    suspend fun getGroupMembers(groupId: String): List<User> = coroutineScope {
        val groupSnapshot = database.getReference("groups").child(groupId).child("members").get().await()
        val memberIds = groupSnapshot.children.mapNotNull { it.key }
        memberIds.map { userId ->
            async(Dispatchers.IO) { database.getReference("users").child(userId).get().await().getValue(User::class.java) }
        }.awaitAll().filterNotNull()
    }

    fun getMessagesForConversation(conversationId: String, isGroup: Boolean): Flow<List<Message>> {
        val currentUserId = auth.currentUser?.uid
        val path = if (isGroup) "group-messages" else "messages"
        val messagesRef = database.getReference(path).child(conversationId)

        messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull {
                    val msg = it.getValue(Message::class.java)
                    msg?.let { m ->
                        if (m.type == "TEXT" || m.type == "LOCATION") {
                             m.copy(conversationId = conversationId, content = EncryptionUtils.decrypt(m.content))
                        } else {
                             m.copy(conversationId = conversationId)
                        }
                    }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    messages.forEach { message ->
                        messageDao.insertOrUpdate(message)
                        if (!isGroup && message.senderId != currentUserId && message.deliveredTimestamp == 0L) {
                            confirmDelivery(conversationId, message.id)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { }
        })
        return messageDao.getMessagesForConversation(conversationId)
    }

    private fun confirmDelivery(conversationId: String, messageId: String) {
        database.getReference("messages/$conversationId/$messageId").child("deliveredTimestamp").setValue(System.currentTimeMillis())
    }

    fun markMessagesAsRead(conversationId: String, messages: List<Message>, isGroup: Boolean) {
        if (isGroup) return
        val currentUserId = auth.currentUser?.uid
        messages.forEach { message ->
            if (message.senderId != currentUserId && message.readTimestamp == 0L) {
                database.getReference("messages/$conversationId/${message.id}").child("readTimestamp").setValue(System.currentTimeMillis())
            }
        }
    }

    private suspend fun updateLastMessageForConversation(conversationId: String, lastMessage: String, timestamp: Long, isGroup: Boolean) {
        val membersToUpdate = if (isGroup) {
            database.getReference("groups").child(conversationId).child("members").get().await().children.mapNotNull { it.key }
        } else {
            conversationId.split("-")
        }
        if (membersToUpdate.isEmpty()) return
        membersToUpdate.forEach { memberId ->
            val conversationRef = database.getReference("user-conversations/$memberId/$conversationId")
            val currentConversation = try { conversationRef.get().await().getValue(Conversation::class.java) } catch (e: Exception) { null }
            if (currentConversation != null) {
                val updatedConversation = currentConversation.copy(lastMessage = lastMessage, timestamp = timestamp)
                conversationRef.setValue(updatedConversation)
            }
        }
    }

    suspend fun sendMessage(conversationId: String, content: String, isGroup: Boolean, type: String = "TEXT") {
        val currentUserId = auth.currentUser?.uid ?: return
        if (!isGroup) {
            val userIds = conversationId.split("-")
            if (userIds.size == 2) {
                val otherUserId = if (userIds[0] == currentUserId) userIds[1] else userIds[0]
                if (isUserBlocked(currentUserId, otherUserId) || isUserBlocked(otherUserId, currentUserId)) throw Exception("Bloqueado")
            }
        }
        val path = if (isGroup) "group-messages" else "messages"
        val messagesRef = database.getReference(path).child(conversationId)
        val messageId = messagesRef.push().key ?: return
        val encryptedContent = if (type == "TEXT" || type == "LOCATION") EncryptionUtils.encrypt(content) else content
        val message = Message(id = messageId, conversationId = conversationId, senderId = currentUserId, content = encryptedContent, type = type, timestamp = System.currentTimeMillis(), status = "SENT")
        messagesRef.child(messageId).setValue(message).await()
        messageDao.insertOrUpdate(message.copy(content = content))
        val lastMsg = when(type) { "LOCATION" -> "📍 Localização"; "IMAGE" -> "📷 Foto"; "STICKER" -> "Figurinha"; else -> content }
        updateLastMessageForConversation(conversationId, lastMsg, message.timestamp, isGroup)
    }

    suspend fun sendMediaMessage(conversationId: String, uri: Uri, type: String, isGroup: Boolean) {
        try {
            val downloadUrl = CloudinaryHelper.uploadFile(uri, type)
            sendMessage(conversationId, downloadUrl, isGroup, type)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Erro mídia: ${e.message}")
            throw e
        }
    }

    suspend fun sendStickerMessage(conversationId: String, stickerUrl: String, isGroup: Boolean) {
        sendMessage(conversationId, stickerUrl, isGroup, "STICKER")
    }

    suspend fun createOrGetConversation(targetUser: User): String {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Não autenticado")
        val conversationId = getConversationId(currentUserId, targetUser.uid)
        val existingConversation = conversationDao.getConversationById(conversationId)
        if (existingConversation != null) return conversationId
        val currentUserSnapshot = database.getReference("users").child(currentUserId).get().await()
        val currentUser = currentUserSnapshot.getValue(User::class.java)
        val conv1 = Conversation(conversationId, targetUser.name, targetUser.profilePictureUrl, "Olá!", System.currentTimeMillis(), isGroup = false)
        val conv2 = Conversation(conversationId, currentUser?.name ?: "Usuário", currentUser?.profilePictureUrl, "Olá!", System.currentTimeMillis(), isGroup = false)
        database.getReference("user-conversations/$currentUserId/$conversationId").setValue(conv1)
        database.getReference("user-conversations/${targetUser.uid}/$conversationId").setValue(conv2)
        conversationDao.insertOrUpdate(conv1)
        return conversationId
    }

    private fun getConversationId(u1: String, u2: String) = if (u1 > u2) "$u1-$u2" else "$u2-$u1"
    suspend fun getConversationDetails(id: String) = conversationDao.getConversationById(id)
    suspend fun getMessageById(cId: String, mId: String, isG: Boolean): Message? {
        val path = if (isG) "group-messages" else "messages"
        return database.getReference("$path/$cId/$mId").get().await().getValue(Message::class.java)
    }

    suspend fun togglePinMessage(conversationId: String, message: Message, isGroup: Boolean) {
        val conversation = conversationDao.getConversationById(conversationId)
        val newPinnedId = if (conversation?.pinnedMessageId == message.id) null else message.id
        val update = mapOf("pinnedMessageId" to newPinnedId)
        if (isGroup) {
            database.getReference("groups/$conversationId/members").get().await().children.forEach {
                database.getReference("user-conversations/${it.key}/$conversationId").updateChildren(update)
            }
        } else {
            conversationId.split("-").forEach {
                database.getReference("user-conversations/$it/$conversationId").updateChildren(update)
            }
        }
    }

    suspend fun toggleReaction(cId: String, mId: String, emoji: String, isG: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val path = if (isG) "group-messages" else "messages"
        val ref = database.getReference("$path/$cId/$mId/reactions")
        val reactions = ref.get().await().getValue<MutableMap<String, String>>() ?: mutableMapOf()
        if (reactions[uid] == emoji) reactions.remove(uid) else reactions[uid] = emoji
        ref.setValue(reactions)
    }

    suspend fun toggleBlockUser(targetId: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.getReference("users/$uid/blockedUsers")
        val list = ref.get().await().getValue(object : com.google.firebase.database.GenericTypeIndicator<MutableList<String>>() {}) ?: mutableListOf()
        if (list.contains(targetId)) list.remove(targetId) else list.add(targetId)
        ref.setValue(list)
    }

    suspend fun isUserBlocked(uid: String, targetId: String): Boolean {
        val list = database.getReference("users/$uid/blockedUsers").get().await().getValue(object : com.google.firebase.database.GenericTypeIndicator<List<String>>() {}) ?: emptyList()
        return list.contains(targetId)
    }

    suspend fun importDeviceContacts(): List<Pair<String, String>> {
        val contacts = mutableListOf<Pair<String, String>>()
        val cursor = context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null)
        cursor?.use {
            val nI = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val pI = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) contacts.add(it.getString(nI) to it.getString(pI))
        }
        return contacts
    }
    
    suspend fun getGroupDetails(id: String) = database.getReference("groups/$id").get().await().getValue(Group::class.java)

    suspend fun updateGroupName(groupId: String, newName: String) {
        database.getReference("groups").child(groupId).child("name").setValue(newName).await()
        database.getReference("groups").child(groupId).child("members").get().await().children.forEach { member ->
            database.getReference("user-conversations/${member.key}/$groupId/name").setValue(newName)
        }
    }

    suspend fun addMemberToGroup(groupId: String, userId: String) {
        database.getReference("groups/$groupId/members").child(userId).setValue(true).await()
        val groupDetails = getGroupDetails(groupId) ?: return
        val conv = Conversation(groupId, groupDetails.name, groupDetails.profilePictureUrl, "Você foi adicionado!", System.currentTimeMillis(), isGroup = true)
        database.getReference("user-conversations/$userId/$groupId").setValue(conv).await()
    }

    suspend fun removeMemberFromGroup(groupId: String, userId: String) {
        database.getReference("groups/$groupId/members").child(userId).removeValue().await()
        database.getReference("user-conversations/$userId/$groupId").removeValue().await()
    }

    suspend fun uploadGroupProfilePicture(groupId: String, imageUri: Uri): String {
        val downloadUrl = CloudinaryHelper.uploadFile(imageUri, "IMAGE")
        database.getReference("groups/$groupId/profilePictureUrl").setValue(downloadUrl).await()
        database.getReference("groups/$groupId/members").get().await().children.forEach { member ->
            database.getReference("user-conversations/${member.key}/$groupId/profilePictureUrl").setValue(downloadUrl)
        }
        return downloadUrl
    }
}