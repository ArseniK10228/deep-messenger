package online.deepdesign.deep.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EmailSendRequest(val email: String)

@JsonClass(generateAdapter = true)
data class EmailSendResponse(
    val requestId: String,
    val email: String,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class EmailVerifyRequest(
    val requestId: String,
    val code: String
)

@JsonClass(generateAdapter = true)
data class TelegramSendRequest(val phone: String)

@JsonClass(generateAdapter = true)
data class TelegramSendResponse(
    val requestId: String,
    val phone: String,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class TelegramVerifyRequest(
    val requestId: String,
    val code: String
)

@JsonClass(generateAdapter = true)
data class FirebaseAuthRequest(val idToken: String, val displayName: String? = null)

@JsonClass(generateAdapter = true)
data class AuthResponse(val token: String, val user: UserDto)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val email: String? = null,
    val phone: String,
    val username: String? = null,
    val displayName: String,
    val avatarUrl: String?,
    val online: Boolean? = null,
    @Json(name = "lastSeenAt") val lastSeenAt: String? = null,
    @Json(name = "appVersionCode") val appVersionCode: Int? = null,
    @Json(name = "appVersionName") val appVersionName: String? = null,
    @Json(name = "canViewPresence") val canViewPresence: Boolean? = null,
    @Json(name = "clientState") val clientState: ClientStateDto? = null,
    @Json(name = "clientStateAt") val clientStateAt: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "activeCall") val activeCall: ActiveCallDto? = null
)

@JsonClass(generateAdapter = true)
data class ActiveCallDto(
    val callId: String,
    val state: String,
    val peerId: String,
    val peerName: String? = null,
    @Json(name = "ringingSince") val ringingSince: Long,
    @Json(name = "activeSince") val activeSince: Long? = null,
    @Json(name = "durationMs") val durationMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class ClientStateDto(
    val foreground: Boolean? = null,
    val batteryPct: Int? = null,
    val charging: Boolean? = null,
    val network: String? = null,
    val inCall: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class MeResponse(val user: UserDto)

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    val displayName: String? = null,
    val username: String? = null
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
    val mediaMime: String? = null,
    val mediaSize: Long? = null,
    val mediaDurationMs: Long? = null,
    val createdAt: String,
    val replyToId: String? = null,
    val peerDelivered: Boolean? = null,
    val peerRead: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class DeleteMessageRequest(val scope: String)

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
data class UsersSearchResponse(val users: List<UserDto>)

@JsonClass(generateAdapter = true)
data class IceServerDto(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

@JsonClass(generateAdapter = true)
data class IceServersResponse(val iceServers: List<IceServerDto>)

@JsonClass(generateAdapter = true)
data class StartCallRequest(
    val conversationId: String,
    val video: Boolean = false
)

@JsonClass(generateAdapter = true)
data class StartCallResponse(
    val callId: String,
    val iceServers: List<IceServerDto>
)

@JsonClass(generateAdapter = true)
data class AcceptCallResponse(
    val ok: Boolean,
    val iceServers: List<IceServerDto>
)

@JsonClass(generateAdapter = true)
data class FcmRegisterRequest(
    val token: String,
    val versionCode: Int? = null,
    val versionName: String? = null
)

@JsonClass(generateAdapter = true)
data class ClientReportRequest(
    val versionCode: Int,
    val versionName: String,
    val foreground: Boolean? = null,
    val batteryPct: Int? = null,
    val charging: Boolean? = null,
    val network: String? = null,
    val inCall: Boolean? = null
)

data class ClientStatePayload(
    val foreground: Boolean,
    val batteryPct: Int?,
    val charging: Boolean?,
    val network: String?,
    val inCall: Boolean
)

@JsonClass(generateAdapter = true)
data class AdminUsersResponse(val users: List<UserDto>)

@JsonClass(generateAdapter = true)
data class AdminUserResponse(val user: UserDto)

@JsonClass(generateAdapter = true)
data class DiagRequest(val action: String = "snapshot")

@JsonClass(generateAdapter = true)
data class CallRecordingDto(
    val id: String,
    val callId: String,
    val conversationId: String? = null,
    val callerId: String? = null,
    val calleeId: String? = null,
    val callerName: String? = null,
    val calleeName: String? = null,
    val callerUsername: String? = null,
    val calleeUsername: String? = null,
    val uploadedByName: String? = null,
    val title: String? = null,
    @Json(name = "startedAt") val startedAt: String? = null,
    @Json(name = "endedAt") val endedAt: String? = null,
    @Json(name = "durationMs") val durationMs: Long? = null,
    @Json(name = "mediaUrl") val mediaUrl: String? = null,
    val video: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class CallRecordingsResponse(val recordings: List<CallRecordingDto>)

@JsonClass(generateAdapter = true)
data class CallRecordingUploadResponse(val recording: CallRecordingDto)

data class AppReleaseDto(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String? = null,
    val forceUpdate: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class PeerResponse(val peer: UserDto)

@JsonClass(generateAdapter = true)
data class PresenceUserDto(
    val userId: String,
    val online: Boolean,
    @Json(name = "lastSeenAt") val lastSeenAt: String? = null
)

@JsonClass(generateAdapter = true)
data class WsEnvelope(
    val type: String,
    val message: MessageDto? = null,
    val messageId: String? = null,
    val conversationId: String? = null,
    val userId: String? = null,
    val callId: String? = null,
    val callerId: String? = null,
    val callerName: String? = null,
    val sdp: String? = null,
    val sdpType: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val fromUserId: String? = null,
    val reason: String? = null,
    val video: String? = null,
    val muted: Boolean? = null,
    val online: Boolean? = null,
    @Json(name = "lastSeenAt") val lastSeenAt: String? = null,
    val users: List<PresenceUserDto>? = null,
    val action: String? = null,
    @Json(name = "operatorId") val operatorId: String? = null,
    val user: UserDto? = null
)
