package com.example.app_mensagem.data

import android.net.Uri
import com.example.app_mensagem.data.model.Conversation
import com.example.app_mensagem.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class ProfileRepository {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun getUserProfile(): User? {
        val userId = auth.currentUser?.uid ?: return null
        val snapshot = database.getReference("users").child(userId).get().await()
        return snapshot.getValue(User::class.java)
    }

    suspend fun updateProfile(name: String, status: String, imageUri: Uri?) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.getReference("users").child(userId)
        var imageUrl: String? = null

        if (imageUri != null) {
            val storageRef = storage.getReference("profile_pictures/$userId")
            storageRef.putFile(imageUri).await()
            imageUrl = storageRef.downloadUrl.await().toString()
        }

        val updates = mutableMapOf<String, Any?>()
        updates["name"] = name
        updates["status"] = status
        if (imageUrl != null) {
            updates["profilePictureUrl"] = imageUrl
        }

        userRef.updateChildren(updates).await()

        val updatedUserSnapshot = userRef.get().await()
        val updatedUser = updatedUserSnapshot.getValue(User::class.java) ?: return

        propagateProfileUpdates(updatedUser)
    }

    private suspend fun propagateProfileUpdates(updatedUser: User) {
        val currentUserConversationsRef = database.getReference("user-conversations").child(updatedUser.uid)
        val snapshot = currentUserConversationsRef.get().await()

        snapshot.children.forEach { conversationSnapshot ->
            val conversation = conversationSnapshot.getValue(Conversation::class.java)
            if (conversation != null && !conversation.isGroup) {
                val otherUserId = conversation.id.replace(updatedUser.uid, "").replace("-", "")

                val otherUserConversationRef = database.getReference("user-conversations")
                    .child(otherUserId)
                    .child(conversation.id)

                val updates = mapOf(
                    "name" to updatedUser.name,
                    "profilePictureUrl" to updatedUser.profilePictureUrl
                )
                otherUserConversationRef.updateChildren(updates).await()
            }
        }
    }
}