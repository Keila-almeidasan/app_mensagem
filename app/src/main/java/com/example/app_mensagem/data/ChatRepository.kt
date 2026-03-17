package com.example.app_mensagem.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
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
import com.google.firebase.database.*
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

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val context: Context
) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    private var conversationsListener: ValueEventListener? = null
    private var conversationsRef: DatabaseReference? = null

    // --- PRESENÇA E STATUS ---

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

    suspend fun setLastSeenVisibility(isVisible: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        database.getReference("users/$uid/lastSeenVisible").setValue(isVisible).await()
    }

    suspend fun getLastSeenVisibility(): Boolean {
        val uid = auth.currentUser?.uid ?: return true
        val snapshot = database.getReference("users/$uid/lastSeenVisible").get().await()
        return snapshot.getValue(Boolean::class.java) ?: true
    }

    fun observeUserPresence(userId: String): Flow<Triple<Boolean, Long, Boolean>> = callbackFlow {
        val ref = database.getReference("users/$userId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                val lastSeenVisible = snapshot.child("lastSeenVisible").getValue(Boolean::class.java) ?: true
                val lastSeenVal = snapshot.child("lastSeen").value
                val lastSeen = if (lastSeenVal is Long) lastSeenVal else 0L
                trySend(Triple(isOnline, lastSeen, lastSeenVisible))
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun setTypingStatus(conversationId: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        database.getReference("typing/$conversationId/$uid").setValue(if (isTyping) true else null)
    }

    fun observeTypingStatus(conversationId: String): Flow<List<String>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: ""
        val ref = database.getReference("typing/$conversationId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val typingUsers = snapshot.children.filter { it.key != uid }.mapNotNull { it.key }
                trySend(typingUsers)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // --- CONVERSAS ---

    fun startConversationListener() {
        val userId = auth.currentUser?.uid ?: return
        if (conversationsRef != null) return

        conversationsRef = database.getReference("user-conversations").child(userId)
        conversationsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val conversations = snapshot.children.mapNotNull { it.getValue(Conversation::class.java) }
                CoroutineScope(Dispatchers.IO).launch {
                    conversations.forEach { conv -> 
                        val existing = try { conversationDao.getConversationById(conv.id) } catch (e: Exception) { null }
                        if (existing != null && conv.timestamp > existing.timestamp) {
                            if (conv.lastSenderId.isNotBlank() && conv.lastSenderId != userId && !conv.isMuted) {
                                NotificationHelper.showNotification(context, conv.name, conv.lastMessage, conv.id)
                            }
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

    fun getConversations(): Flow<List<Conversation>> = conversationDao.getAllConversations()

    suspend fun syncUserConversations() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val ref = database.getReference("user-conversations").child(userId)
            val snapshot = ref.get().await()
            val conversations = snapshot.children.mapNotNull { it.getValue(Conversation::class.java) }
            conversations.forEach { conversationDao.insertOrUpdate(it) }
        } catch (e: Exception) { }
    }

    suspend fun clearLocalCache() {
        conversationDao.clearAll()
        messageDao.clearAll()
    }

    suspend fun toggleMuteConversation(conversationId: String, isMuted: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        database.getReference("user-conversations/$uid/$conversationId/isMuted").setValue(isMuted).await()
        val conv = conversationDao.getConversationById(conversationId)
        conv?.let { conversationDao.insertOrUpdate(it.copy(isMuted = isMuted)) }
    }

    suspend fun getConversationDetails(id: String) = conversationDao.getConversationById(id)

    suspend fun createOrGetConversation(targetUser: User): String {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Não autenticado")
        val conversationId = if (currentUserId > targetUser.uid) "${currentUserId}-${targetUser.uid}" else "${targetUser.uid}-${currentUserId}"
        val existing = conversationDao.getConversationById(conversationId)
        if (existing != null) return conversationId
        val currentUser = database.getReference("users/$currentUserId").get().await().getValue(User::class.java)
        val conv1 = Conversation(conversationId, targetUser.name, targetUser.profilePictureUrl, "Olá!", currentUserId, System.currentTimeMillis(), isGroup = false)
        val conv2 = Conversation(conversationId, currentUser?.name ?: "Usuário", currentUser?.profilePictureUrl, "Olá!", currentUserId, System.currentTimeMillis(), isGroup = false)
        database.getReference("user-conversations/$currentUserId/$conversationId").setValue(conv1)
        database.getReference("user-conversations/${targetUser.uid}/$conversationId").setValue(conv2)
        conversationDao.insertOrUpdate(conv1)
        return conversationId
    }

    // --- MENSAGENS ---

    fun getMessagesForConversation(conversationId: String, isGroup: Boolean): Flow<List<Message>> {
        val path = if (isGroup) "group-messages" else "messages"
        val ref = database.getReference(path).child(conversationId)
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull {
                    val msg = it.getValue(Message::class.java)
                    msg?.let { m ->
                        if (m.type == "TEXT" || m.type == "LOCATION") m.copy(conversationId = conversationId, content = EncryptionUtils.decrypt(m.content))
                        else m.copy(conversationId = conversationId)
                    }
                }
                CoroutineScope(Dispatchers.IO).launch {
                    messages.forEach { message ->
                        messageDao.insertOrUpdate(message)
                        if (!isGroup && message.senderId != auth.currentUser?.uid && message.deliveredTimestamp == 0L) {
                            database.getReference("messages/$conversationId/${message.id}/deliveredTimestamp").setValue(ServerValue.TIMESTAMP)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { }
        })
        return messageDao.getMessagesForConversation(conversationId)
    }

    suspend fun getMessageById(cId: String, mId: String, isG: Boolean) = 
        database.getReference(if (isG) "group-messages/$cId/$mId" else "messages/$cId/$mId").get().await().getValue(Message::class.java)

    fun searchMessages(conversationId: String, query: String): Flow<List<Message>> = messageDao.searchMessages(conversationId, query)

    fun markMessagesAsRead(conversationId: String, messages: List<Message>, isGroup: Boolean) {
        if (isGroup) return
        val uid = auth.currentUser?.uid ?: return
        messages.forEach { 
            if (it.senderId != uid && it.readTimestamp == 0L) {
                database.getReference("messages/$conversationId/${it.id}/readTimestamp").setValue(ServerValue.TIMESTAMP)
            }
        }
    }

    suspend fun sendMessage(conversationId: String, content: String, isGroup: Boolean, type: String = "TEXT", fileName: String? = null) {
        val currentUserId = auth.currentUser?.uid ?: return
        val path = if (isGroup) "group-messages" else "messages"
        val ref = database.getReference(path).child(conversationId)
        val messageId = ref.push().key ?: return
        val encrypted = if (type == "TEXT" || type == "LOCATION") EncryptionUtils.encrypt(content) else content
        val message = Message(
            id = messageId, 
            conversationId = conversationId, 
            senderId = currentUserId, 
            content = encrypted, 
            fileName = fileName,
            type = type, 
            timestamp = System.currentTimeMillis(), 
            status = "SENT"
        )
        ref.child(messageId).setValue(message).await()
        messageDao.insertOrUpdate(message.copy(content = content))
        
        val lastMsg = when(type) { 
            "LOCATION" -> "📍 Localização"
            "IMAGE" -> "📷 Foto"
            "VIDEO" -> "🎥 Vídeo"
            "DOCUMENT" -> "📄 ${fileName ?: "Documento"}"
            "STICKER" -> "Figurinha"
            "AUDIO" -> "🎤 Áudio"
            else -> content 
        }
        updateLastMessageForConversation(conversationId, lastMsg, message.timestamp, isGroup)
    }

    suspend fun sendMediaMessage(conversationId: String, uri: Uri, type: String, isGroup: Boolean) {
        try { 
            val fileName = getFileName(uri)
            val url = CloudinaryHelper.uploadFile(uri, type)
            sendMessage(conversationId, url, isGroup, type, fileName) 
        } catch (e: Exception) {
            Log.e("ChatRepository", "Erro no upload: ${e.message}")
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    suspend fun sendStickerMessage(conversationId: String, url: String, isGroup: Boolean) {
        sendMessage(conversationId, url, isGroup, "STICKER")
    }

    suspend fun togglePinMessage(conversationId: String, message: Message, isGroup: Boolean) {
        val conversation = conversationDao.getConversationById(conversationId)
        val newPinnedId = if (conversation?.pinnedMessageId == message.id) null else message.id
        val update = mapOf("pinnedMessageId" to newPinnedId)
        val members = if (isGroup) database.getReference("groups/$conversationId/members").get().await().children.mapNotNull { it.key } else conversationId.split("-")
        members.forEach { database.getReference("user-conversations/$it/$conversationId").updateChildren(update) }
    }

    private suspend fun updateLastMessageForConversation(conversationId: String, lastMessage: String, timestamp: Long, isGroup: Boolean) {
        val currentUid = auth.currentUser?.uid ?: return
        val membersToUpdate = if (isGroup) database.getReference("groups/$conversationId/members").get().await().children.mapNotNull { it.key } else conversationId.split("-")

        membersToUpdate.forEach { memberId ->
            val ref = database.getReference("user-conversations/$memberId/$conversationId")
            val current = try { ref.get().await().getValue(Conversation::class.java) } catch (e: Exception) { null }
            if (current != null) ref.setValue(current.copy(lastMessage = lastMessage, timestamp = timestamp, lastSenderId = currentUid))
        }
    }

    // --- GRUPOS ---

    suspend fun createGroup(name: String, memberIds: List<String>, imageUri: Uri? = null) {
        val currentUserId = auth.currentUser?.uid ?: return
        var imageUrl: String? = null
        if (imageUri != null) {
            try { imageUrl = CloudinaryHelper.uploadFile(imageUri, "IMAGE") } catch (e: Exception) { }
        }
        val groupId = database.getReference("groups").push().key ?: return
        val allMemberIds = (memberIds + currentUserId).distinct()
        val group = Group(id = groupId, name = name, creatorId = currentUserId, members = allMemberIds.associateWith { true }, profilePictureUrl = imageUrl)
        database.getReference("groups").child(groupId).setValue(group).await()
        val conv = Conversation(id = groupId, name = name, profilePictureUrl = imageUrl, lastMessage = "Grupo criado!", lastSenderId = currentUserId, timestamp = System.currentTimeMillis(), isGroup = true)
        allMemberIds.forEach { database.getReference("user-conversations/$it").child(groupId).setValue(conv) }
    }

    suspend fun getGroupMembers(groupId: String): List<User> = coroutineScope {
        val snapshot = database.getReference("groups/$groupId/members").get().await()
        snapshot.children.mapNotNull { it.key }.map { userId ->
            async(Dispatchers.IO) { database.getReference("users").child(userId).get().await().getValue(User::class.java) }
        }.awaitAll().filterNotNull()
    }

    suspend fun getGroupDetails(id: String) = database.getReference("groups/$id").get().await().getValue(Group::class.java)

    suspend fun updateGroupName(groupId: String, newName: String) {
        database.getReference("groups/$groupId/name").setValue(newName).await()
        database.getReference("groups/$groupId/members").get().await().children.forEach { database.getReference("user-conversations/${it.key}/$groupId/name").setValue(newName) }
    }

    suspend fun addMemberToGroup(groupId: String, userId: String) {
        database.getReference("groups/$groupId/members/$userId").setValue(true).await()
        val details = getGroupDetails(groupId) ?: return
        val conv = Conversation(groupId, details.name, details.profilePictureUrl, "Você foi adicionado!", auth.currentUser?.uid ?: "", System.currentTimeMillis(), isGroup = true)
        database.getReference("user-conversations/$userId/$groupId").setValue(conv)
    }

    suspend fun removeMemberFromGroup(groupId: String, userId: String) {
        database.getReference("groups/$groupId/members/$userId").removeValue().await()
        database.getReference("user-conversations/$userId/$groupId").removeValue()
    }

    suspend fun uploadGroupProfilePicture(groupId: String, imageUri: Uri): String {
        val url = CloudinaryHelper.uploadFile(imageUri, "IMAGE")
        database.getReference("groups/$groupId/profilePictureUrl").setValue(url).await()
        database.getReference("groups/$groupId/members").get().await().children.forEach { database.getReference("user-conversations/${it.key}/$groupId/profilePictureUrl").setValue(url) }
        return url
    }

    // --- CONTATOS E BLOQUEIO ---

    suspend fun getUsers(): List<User> {
        val currentUserId = auth.currentUser?.uid
        val snapshot = database.getReference("users").get().await()
        return snapshot.children.mapNotNull { it.getValue(User::class.java) }.filter { it.uid != currentUserId }
    }

    suspend fun toggleBlockUser(targetId: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.getReference("users/$uid/blockedUsers")
        val list = ref.get().await().getValue(object : GenericTypeIndicator<MutableList<String>>() {}) ?: mutableListOf()
        if (list.contains(targetId)) list.remove(targetId) else list.add(targetId)
        ref.setValue(list)
    }

    suspend fun isUserBlocked(uid: String, targetId: String): Boolean {
        val list = database.getReference("users/$uid/blockedUsers").get().await().getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
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
}