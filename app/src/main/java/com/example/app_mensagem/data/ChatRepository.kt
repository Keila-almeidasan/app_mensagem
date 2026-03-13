package com.example.app_mensagem.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.example.app_mensagem.data.local.ConversationDao
import com.example.app_mensagem.data.local.MessageDao
import com.example.app_mensagem.data.model.Conversation
import com.example.app_mensagem.data.model.Group
import com.example.app_mensagem.data.model.Message
import com.example.app_mensagem.data.model.User
import com.example.app_mensagem.services.EncryptionUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.getValue
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val context: Context
) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var conversationsListener: ValueEventListener? = null
    private var conversationsRef: DatabaseReference? = null

    fun startConversationListener() {
        val userId = auth.currentUser?.uid ?: return
        if (conversationsListener != null) return

        conversationsRef = database.getReference("user-conversations").child(userId)
        conversationsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val conversations = snapshot.children.mapNotNull { it.getValue(Conversation::class.java) }
                CoroutineScope(Dispatchers.IO).launch {
                    conversations.forEach { conversationDao.insertOrUpdate(it) }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatRepository", "Ouvinte de conversas cancelado: ${error.message}")
            }
        }
        conversationsRef?.addValueEventListener(conversationsListener!!)
    }

    fun stopConversationListener() {
        conversationsListener?.let { conversationsRef?.removeEventListener(it) }
        conversationsListener = null
    }

    fun getConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations()
    }

    suspend fun syncUserConversations() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val conversationsRef = database.getReference("user-conversations").child(userId)
            val snapshot = conversationsRef.get().await()
            val conversations = snapshot.children.mapNotNull { it.getValue(Conversation::class.java) }
            conversations.forEach { conversationDao.insertOrUpdate(it) }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Falha na sincronização inicial: ${e.message}")
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
        return snapshot.children.mapNotNull {
            it.getValue(User::class.java)
        }.filter { it.uid != currentUserId }
    }

    suspend fun createGroup(name: String, memberIds: List<String>) {
        val currentUserId = auth.currentUser?.uid ?: return
        val currentUser = database.getReference("users").child(currentUserId).get().await().getValue(User::class.java)

        val groupsRef = database.getReference("groups")
        val groupId = groupsRef.push().key ?: return

        val allMemberIds = (memberIds + currentUserId).distinct()
        val membersMap = allMemberIds.associateWith { true }

        val group = Group(
            id = groupId,
            name = name,
            creatorId = currentUserId,
            members = membersMap
        )

        groupsRef.child(groupId).setValue(group).await()

        val groupConversation = Conversation(
            id = groupId,
            name = name,
            lastMessage = "Grupo criado por ${currentUser?.name ?: "alguém"}!",
            timestamp = System.currentTimeMillis(),
            isGroup = true
        )

        allMemberIds.forEach { memberId ->
            database.getReference("user-conversations")
                .child(memberId)
                .child(groupId)
                .setValue(groupConversation)
                .await()
        }
    }

    suspend fun getGroupMembers(groupId: String): List<User> = coroutineScope {
        val groupSnapshot = database.getReference("groups").child(groupId).child("members").get().await()
        val memberIds = groupSnapshot.children.mapNotNull { it.key }

        memberIds.map { userId ->
            async(Dispatchers.IO) {
                database.getReference("users").child(userId).get().await().getValue(User::class.java)
            }
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
                        // Descriptografar se for texto
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
        database.getReference("messages/$conversationId/$messageId")
            .child("deliveredTimestamp").setValue(System.currentTimeMillis())
    }

    fun markMessagesAsRead(conversationId: String, messages: List<Message>, isGroup: Boolean) {
        if (isGroup) return
        val currentUserId = auth.currentUser?.uid
        val path = "messages"
        messages.forEach { message ->
            if (message.senderId != currentUserId && message.readTimestamp == 0L) {
                database.getReference("$path/$conversationId/${message.id}")
                    .child("readTimestamp").setValue(System.currentTimeMillis())
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
            val currentConversation = conversationRef.get().await().getValue(Conversation::class.java)
            if (currentConversation != null) {
                val updatedConversation = currentConversation.copy(
                    lastMessage = lastMessage,
                    timestamp = timestamp
                )
                conversationRef.setValue(updatedConversation).await()
            }
        }
    }

    suspend fun sendMessage(conversationId: String, content: String, isGroup: Boolean, type: String = "TEXT") {
        val currentUserId = auth.currentUser?.uid ?: return
        val path = if (isGroup) "group-messages" else "messages"
        val messagesRef = database.getReference(path).child(conversationId)
        val messageId = messagesRef.push().key ?: return

        // Criptografar conteúdo se for texto ou localização
        val encryptedContent = EncryptionUtils.encrypt(content)

        val message = Message(
            id = messageId,
            conversationId = conversationId,
            senderId = currentUserId,
            content = content, // Localmente salvamos legível
            type = type,
            timestamp = System.currentTimeMillis(),
            status = "SENDING"
        )
        messageDao.insertOrUpdate(message)

        val messageForFirebase = message.copy(status = "SENT", content = encryptedContent)

        try {
            messagesRef.child(messageId).setValue(messageForFirebase).await()
            messageDao.insertOrUpdate(message.copy(status = "SENT"))
            val displayLastMsg = if (type == "LOCATION") "📍 Localização" else content
            updateLastMessageForConversation(conversationId, displayLastMsg, message.timestamp, isGroup)
        } catch (e: Exception) {
            messageDao.insertOrUpdate(message.copy(status = "FAILED"))
        }
    }

    suspend fun sendStickerMessage(conversationId: String, stickerId: String, isGroup: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        val path = if (isGroup) "group-messages" else "messages"
        val messagesRef = database.getReference(path).child(conversationId)
        val messageId = messagesRef.push().key ?: return

        val message = Message(
            id = messageId,
            conversationId = conversationId,
            senderId = currentUserId,
            content = stickerId,
            type = "STICKER",
            timestamp = System.currentTimeMillis(),
            status = "SENT"
        )

        messagesRef.child(messageId).setValue(message).await()
        messageDao.insertOrUpdate(message)
        updateLastMessageForConversation(conversationId, "Figurinha", message.timestamp, isGroup)
    }

    suspend fun sendMediaMessage(conversationId: String, uri: Uri, type: String, isGroup: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        val path = if (isGroup) "group-messages" else "messages"
        val messagesRef = database.getReference(path).child(conversationId)
        val messageId = messagesRef.push().key ?: return

        val fileExtension = when(type) {
            "IMAGE" -> "jpg"
            "VIDEO" -> "mp4"
            "AUDIO" -> "m4a"
            else -> "dat"
        }
        val storagePath = when(type) {
            "IMAGE" -> "images"
            "VIDEO" -> "videos"
            "AUDIO" -> "audios"
            else -> "files"
        }
        
        val fileName = "${UUID.randomUUID()}.$fileExtension"
        val storageRef = storage.getReference("$storagePath/$conversationId/$fileName")
        storageRef.putFile(uri).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()

        var thumbnailUrl: String? = null
        if (type == "VIDEO") {
            thumbnailUrl = generateAndUploadThumbnail(conversationId, uri)
        }

        val message = Message(
            id = messageId,
            conversationId = conversationId,
            senderId = currentUserId,
            content = downloadUrl,
            type = type,
            thumbnailUrl = thumbnailUrl,
            timestamp = System.currentTimeMillis(),
            status = "SENT"
        )

        messagesRef.child(messageId).setValue(message).await()
        messageDao.insertOrUpdate(message)

        val lastMessageText = when(type) {
            "IMAGE" -> "📷 Imagem"
            "VIDEO" -> "🎥 Vídeo"
            "AUDIO" -> "🎤 Áudio"
            else -> "📎 Arquivo"
        }
        updateLastMessageForConversation(conversationId, lastMessageText, message.timestamp, isGroup)
    }

    private suspend fun generateAndUploadThumbnail(conversationId: String, videoUri: Uri): String? {
        val thumbnailBitmap: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(videoUri, Size(240, 240), null)
            } else {
                @Suppress("DEPRECATION")
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri)
                val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                bitmap
            }
        } catch (e: Exception) {
            null
        }

        if (thumbnailBitmap == null) return null

        val baos = ByteArrayOutputStream()
        thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val thumbnailData = baos.toByteArray()
        val thumbnailFileName = "${UUID.randomUUID()}.jpg"
        val thumbnailStorageRef = storage.getReference("thumbnails/$conversationId/$thumbnailFileName")
        thumbnailStorageRef.putBytes(thumbnailData).await()
        return thumbnailStorageRef.downloadUrl.await().toString()
    }

    suspend fun createOrGetConversation(targetUser: User): String {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("Usuário não autenticado")
        val conversationId = getConversationId(currentUserId, targetUser.uid)

        val existingConversation = conversationDao.getConversationById(conversationId)
        if (existingConversation != null) {
            return conversationId
        }

        val currentUserSnapshot = database.getReference("users").child(currentUserId).get().await()
        val currentUser = currentUserSnapshot.getValue(User::class.java)

        val conversationForCurrentUser = Conversation(
            id = conversationId,
            name = targetUser.name,
            profilePictureUrl = targetUser.profilePictureUrl,
            lastMessage = "Inicie a conversa!",
            timestamp = System.currentTimeMillis(),
            isGroup = false
        )

        val conversationForTargetUser = Conversation(
            id = conversationId,
            name = currentUser?.name ?: "Usuário",
            profilePictureUrl = currentUser?.profilePictureUrl,
            lastMessage = "Inicie a conversa!",
            timestamp = System.currentTimeMillis(),
            isGroup = false
        )

        database.getReference("user-conversations/$currentUserId/$conversationId").setValue(conversationForCurrentUser).await()
        database.getReference("user-conversations/${targetUser.uid}/$conversationId").setValue(conversationForTargetUser).await()

        conversationDao.insertOrUpdate(conversationForCurrentUser)

        return conversationId
    }

    private fun getConversationId(userId1: String, userId2: String): String {
        return if (userId1 > userId2) "$userId1-$userId2" else "$userId2-$userId1"
    }

    suspend fun getConversationDetails(conversationId: String): Conversation? {
        return conversationDao.getConversationById(conversationId)
    }

    suspend fun getMessageById(conversationId: String, messageId: String, isGroup: Boolean): Message? {
        val path = if (isGroup) "group-messages" else "messages"
        val snapshot = database.getReference("$path/$conversationId/$messageId").get().await()
        return snapshot.getValue(Message::class.java)
    }

    suspend fun togglePinMessage(conversationId: String, message: Message, isGroup: Boolean) {
        val conversation = conversationDao.getConversationById(conversationId)
        val newPinnedId = if (conversation?.pinnedMessageId == message.id) null else message.id
        val pinUpdate = mapOf("pinnedMessageId" to newPinnedId)

        if (isGroup) {
            val groupSnapshot = database.getReference("groups").child(conversationId).child("members").get().await()
            val memberIds = groupSnapshot.children.mapNotNull { it.key }
            memberIds.forEach { memberId ->
                database.getReference("user-conversations/$memberId/$conversationId").updateChildren(pinUpdate)
            }
        } else {
            val userIds = conversationId.split("-")
            if (userIds.size == 2) {
                database.getReference("user-conversations/${userIds[0]}/$conversationId").updateChildren(pinUpdate)
                database.getReference("user-conversations/${userIds[1]}/$conversationId").updateChildren(pinUpdate)
            }
        }
    }

    suspend fun toggleReaction(conversationId: String, messageId: String, emoji: String, isGroup: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        val path = if (isGroup) "group-messages" else "messages"
        val messageRef = database.getReference("$path/$conversationId/$messageId")

        val snapshot = messageRef.child("reactions").get().await()
        val reactions = snapshot.getValue<MutableMap<String, String>>() ?: mutableMapOf()

        if (reactions[currentUserId] == emoji) {
            reactions.remove(currentUserId)
        } else {
            reactions[currentUserId] = emoji
        }

        messageRef.child("reactions").setValue(reactions).await()
    }

    suspend fun getGroupDetails(groupId: String): Group? {
        val snapshot = database.getReference("groups").child(groupId).get().await()
        return snapshot.getValue(Group::class.java)
    }

    suspend fun updateGroupName(groupId: String, newName: String) {
        database.getReference("groups").child(groupId).child("name").setValue(newName).await()

        val groupSnapshot = database.getReference("groups").child(groupId).child("members").get().await()
        val memberIds = groupSnapshot.children.mapNotNull { it.key }
        memberIds.forEach { memberId ->
            database.getReference("user-conversations/$memberId/$groupId").child("name").setValue(newName)
        }
    }

    suspend fun addMemberToGroup(groupId: String, userId: String) {
        database.getReference("groups/$groupId/members").child(userId).setValue(true).await()

        val conversationSnapshot = database.getReference("user-conversations")
            .child(auth.currentUser?.uid ?: "")
            .child(groupId).get().await()
        val groupConversation = conversationSnapshot.getValue(Conversation::class.java)

        if (groupConversation != null) {
            database.getReference("user-conversations/$userId/$groupId").setValue(groupConversation).await()
        }
    }

    suspend fun removeMemberFromGroup(groupId: String, userId: String) {
        database.getReference("groups/$groupId/members").child(userId).removeValue().await()
        database.getReference("user-conversations/$userId/$groupId").removeValue().await()
    }

    suspend fun uploadGroupProfilePicture(groupId: String, imageUri: Uri): String {
        val storageRef = storage.getReference("group_profile_pictures/$groupId/${UUID.randomUUID()}.jpg")
        storageRef.putFile(imageUri).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()

        database.getReference("groups/$groupId").child("profilePictureUrl").setValue(downloadUrl).await()

        val groupSnapshot = database.getReference("groups").child(groupId).child("members").get().await()
        val memberIds = groupSnapshot.children.mapNotNull { it.key }
        memberIds.forEach { memberId ->
            database.getReference("user-conversations/$memberId/$groupId").child("profilePictureUrl").setValue(downloadUrl)
        }
        return downloadUrl
    }

    suspend fun importDeviceContacts(): List<Pair<String, String>> {
        val contacts = mutableListOf<Pair<String, String>>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null
        )
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val number = it.getString(numberIndex)
                contacts.add(name to number)
            }
        }
        return contacts
    }
}