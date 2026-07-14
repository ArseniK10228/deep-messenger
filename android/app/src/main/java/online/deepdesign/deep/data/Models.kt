package online.deepdesign.deep.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FirebaseAuthRequest(val idToken: String, val displayName: String? = null)

@JsonClass(generateAdapter = true)
data class AuthResponse(val token: String, val user: UserDto)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val phone: String,
    val displayName: String,
    val avatarUrl: String?
)

@JsonClass(generateAdapter = true)
data class ConversationsResponse(val conversations: List<ConversationDto>)

@JsonClass(generateAdapter = true)
data class ConversationDto(
    val id: String,
    @Json(name = "last_message") val lastMessage: LastMessageDto?,
    val peers: List<UserDto>?
)

@JsonClass(generateAdapter = true)
data class LastMessageDto(
    val id: String,
    val kind: String,
    val body: String?,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class MessagesResponse(val messages: List<MessageDto>)

@JsonClass(generateAdapter = true)
data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val kind: String,
    val body: String?,
    val mediaUrl: String?,
    val createdAt: String,
    val replyToId: String? = null
)

@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    val kind: String,
    val body: String?,
    val replyToId: String? = null
)

@JsonClass(generateAdapter = true)
data class SendMessageResponse(val message: MessageDto)

@JsonClass(generateAdapter = true)
data class DirectChatRequest(val userId: String)

@JsonClass(generateAdapter = true)
data class DirectChatResponse(val conversationId: String)

@JsonClass(generateAdapter = true)
data class MeResponse(val user: UserDto)

@JsonClass(generateAdapter = true)
data class UsersSearchResponse(val users: List<UserDto>)

@JsonClass(generateAdapter = true)
data class WsEnvelope(
    val type: String,
    val message: MessageDto? = null,
    val messageId: String? = null,
    val conversationId: String? = null,
    val userId: String? = null
)
