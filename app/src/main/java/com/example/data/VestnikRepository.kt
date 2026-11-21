package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class VestnikRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Требуется вход через Google перед доступом к данным.")
    }

    private fun sanitizeHandle(raw: String, fallbackUid: String): String {
        val cleaned = raw.lowercase()
            .replace(Regex("[^a-z0-9_а-яё]"), "_")
            .trim('_')
            .take(28)
        return if (cleaned.length >= 2) cleaned else "user_${fallbackUid.take(6).lowercase()}"
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    // --- PROFILE BOOTSTRAP & OBSERVATION ---

    suspend fun ensureUserProfilesInitialized(
        displayNameRaw: String?,
        emailRaw: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Не авторизован"))
        val userPath = "users/$uid"
        val publicPath = "public_profiles/$uid"
        return try {
            val safeName = (displayNameRaw?.trim().takeIf { !it.isNullOrBlank() } ?: "Участник Вестника").take(60)
            val safeEmail = (emailRaw?.trim() ?: "").take(120)
            val defaultHandle = sanitizeHandle(safeEmail.substringBefore('@').ifBlank { safeName }, uid)

            val privateRef = db.collection("users").document(uid)
            val privateSnap = privateRef.get().await()
            if (!privateSnap.exists()) {
                privateRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "handle" to defaultHandle,
                        "email" to safeEmail,
                        "carInfo" to "",
                        "city" to "Москва",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val publicRef = db.collection("public_profiles").document(uid)
            val publicSnap = publicRef.get().await()
            if (!publicSnap.exists()) {
                publicRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "handle" to defaultHandle,
                        "carInfo" to "",
                        "city" to "Москва",
                        "discoverable" to true,
                        "spotsShared" to 0L,
                        "spotsCaught" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$userPath / $publicPath")
            Result.failure(e)
        }
    }

    fun observeOwnPublicProfile(userId: String): Flow<PublicProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Пользователь сессии изменился." }
        val path = "public_profiles/$uid"
        emitAll(
            db.collection("public_profiles").document(uid)
                .snapshots()
                .map { it.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeDirectoryProfiles(): Flow<List<PublicProfile>> = flow {
        val uid = requireUserId()
        val path = "public_profiles"
        emitAll(
            db.collection("public_profiles")
                .whereEqualTo("discoverable", true)
                .limit(50)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun updateProfile(
        displayName: String,
        handle: String,
        carInfo: String,
        city: String,
        discoverable: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Требуется вход через Google."))
        val safeName = displayName.trim().take(60)
        val safeHandle = sanitizeHandle(handle.trim().removePrefix("@"), uid)
        val safeCar = carInfo.trim().take(80)
        val safeCity = city.trim().take(60)
        if (safeName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Имя не может быть пустым."))
        }
        return try {
            val email = (auth.currentUser?.email ?: "").take(120)
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to safeName,
                    "handle" to safeHandle,
                    "email" to email,
                    "carInfo" to safeCar,
                    "city" to safeCity,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("public_profiles").document(uid).update(
                mapOf(
                    "displayName" to safeName,
                    "handle" to safeHandle,
                    "carInfo" to safeCar,
                    "city" to safeCity,
                    "discoverable" to discoverable,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$uid")
            Result.failure(e)
        }
    }

    // --- CHATS & REAL-TIME MESSAGES ---

    fun observeChats(userId: String): Flow<List<Chat>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Пользователь сессии изменился." }
        val path = "chats"
        emitAll(
            db.collection("chats")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeMessages(chat: Chat): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "chats/${chat.id}/messages"
        val query = if (chat.joinable) {
            db.collection("chats").document(chat.id)
                .collection("messages")
                .whereEqualTo("publicChannel", true)
        } else {
            db.collection("chats").document(chat.id)
                .collection("messages")
                .whereArrayContains("memberUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createGroupOrParkingChat(
        title: String,
        chatType: String,
        joinable: Boolean,
        creatorName: String
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Требуется вход через Google."))
        val safeTitle = title.trim().take(80)
        val safeType = if (chatType in listOf("group", "parking")) chatType else "group"
        if (safeTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Укажите название чата."))
        }
        val docRef = db.collection("chats").document()
        return try {
            val code = generateInviteCode()
            val payload = mapOf(
                "title" to safeTitle,
                "chatType" to safeType,
                "creatorId" to uid,
                "memberUids" to listOf(uid),
                "inviteCode" to code,
                "joinable" to joinable,
                "lastMessageText" to "Канал создан • код приглашения: $code",
                "lastMessageSender" to creatorName.trim().ifBlank { "Вестник" }.take(60),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createOrOpenDirectChat(
        peerUserId: String,
        peerDisplayName: String,
        myDisplayName: String,
        existingChats: List<Chat>
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Требуется вход через Google."))
        val existing = existingChats.firstOrNull { chat ->
            chat.chatType == "direct" &&
                ((peerUserId == uid && chat.memberUids == listOf(uid)) ||
                    (peerUserId != uid && chat.memberUids.size == 2 &&
                        chat.memberUids.contains(uid) && chat.memberUids.contains(peerUserId)))
        }
        if (existing != null) {
            return Result.success(existing.id)
        }
        val docRef = db.collection("chats").document()
        return try {
            val members = if (peerUserId == uid) listOf(uid) else listOf(uid, peerUserId)
            val title = if (peerUserId == uid) {
                "Избранное ($myDisplayName)".take(80)
            } else {
                peerDisplayName.trim().ifBlank { "Диалог" }.take(80)
            }
            val payload = mapOf(
                "title" to title,
                "chatType" to "direct",
                "creatorId" to uid,
                "memberUids" to members,
                "inviteCode" to "",
                "joinable" to false,
                "lastMessageText" to "Диалог открыт",
                "lastMessageSender" to myDisplayName.trim().ifBlank { "Вестник" }.take(60),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinChatByInviteCode(rawCode: String): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Требуется вход через Google."))
        val code = rawCode.trim().uppercase().take(16)
        if (code.length < 4) {
            return Result.failure(IllegalArgumentException("Введите код приглашения (минимум 4 символа)."))
        }
        return try {
            val snap = db.collection("chats")
                .whereEqualTo("joinable", true)
                .whereEqualTo("inviteCode", code)
                .limit(1)
                .get()
                .await()
            val doc = snap.documents.firstOrNull()
                ?: return Result.failure(IllegalStateException("Чат с кодом $code не найден."))
            val chat = doc.toObject(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Не удалось прочитать данные чата."))

            if (uid in chat.memberUids) {
                return Result.success(doc.id)
            }
            if (chat.memberUids.size >= 20) {
                return Result.failure(IllegalStateException("В чате достигнут лимит участников (20)."))
            }
            val updatedMembers = chat.memberUids + uid
            doc.reference.update(
                mapOf(
                    "memberUids" to updatedMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(doc.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/join/$code")
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        chat: Chat,
        senderName: String,
        text: String,
        messageType: String = "text",
        spotAddress: String = "",
        spotId: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Требуется вход через Google."))
        val safeText = text.trim().take(1000)
        if (safeText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Сообщение не может быть пустым."))
        }
        val safeSender = senderName.trim().ifBlank { "Участник" }.take(60)
        val safeType = if (messageType in listOf("text", "parking_share", "call_log")) messageType else "text"
        val msgRef = db.collection("chats").document(chat.id).collection("messages").document()
        return try {
            val msgPayload = mapOf(
                "chatId" to chat.id,
                "senderId" to uid,
                "senderName" to safeSender,
                "text" to safeText,
                "messageType" to safeType,
                "spotAddress" to spotAddress.trim().take(160),
                "spotId" to spotId.trim().take(128),
                "memberUids" to chat.memberUids,
                "publicChannel" to chat.joinable,
                "createdAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(msgPayload).await()

            db.collection("chats").document(chat.id).update(
                mapOf(
                    "lastMessageText" to safeText.take(300),
                    "lastMessageSender" to safeSender,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    // --- VOICE & VIDEO CALLS ---

    fun observeCalls(userId: String): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Пользователь сессии изменился." }
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun startCall(
        callerName: String,
        calleeId: String,
        calleeName: String,
        callType: String
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Требуется вход через Google."))
        val safeType = if (callType == "video") "video" else "audio"
        val safeCaller = callerName.trim().ifBlank { "Вы" }.take(60)
        val safeCallee = calleeName.trim().ifBlank { "Абонент" }.take(60)
        val safeCalleeId = calleeId.trim().take(128)
        if (safeCalleeId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Не указан абонент."))
        }
        val docRef = db.collection("calls").document()
        return try {
            val payload = mapOf(
                "callerId" to uid,
                "callerName" to safeCaller,
                "calleeId" to safeCalleeId,
                "calleeName" to safeCallee,
                "participantUids" to listOf(uid, safeCalleeId),
                "callType" to safeType,
                "status" to "ringing",
                "durationSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun answerCall(callId: String): Result<Unit> = updateCallStatus(callId, "active", null)

    suspend fun declineCall(callId: String): Result<Unit> = updateCallStatus(callId, "declined", null)

    suspend fun markCallMissed(callId: String): Result<Unit> = updateCallStatus(callId, "missed", null)

    suspend fun endCall(callId: String, durationSeconds: Long): Result<Unit> =
        updateCallStatus(callId, "ended", durationSeconds.coerceIn(0L, 86400L))

    private suspend fun updateCallStatus(
        callId: String,
        status: String,
        durationSeconds: Long?
    ): Result<Unit> {
        val path = "calls/$callId"
        return try {
            requireUserId()
            require(callId.isNotBlank()) { "Call ID не может быть пустым." }
            val payload = buildMap<String, Any> {
                put("status", status)
                if (durationSeconds != null) {
                    put("durationSeconds", durationSeconds)
                }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("calls").document(callId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- LIVE PARKING CATCHER ("ЛОВЛЯ ПАРКОВКИ") ---

    fun observeParkingSpots(): Flow<List<ParkingSpot>> = flow {
        val uid = requireUserId()
        val path = "parking_spots"
        emitAll(
            db.collection("parking_spots")
                .whereEqualTo("publicFeed", true)
                .limit(100)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ParkingSpot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfActive(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun reportParkingSpot(
        posterName: String,
        posterCar: String,
        address: String,
        district: String,
        parkingKind: ParkingKind,
        note: String,
        leaveInMinutes: Int,
        location: GeoPointData?
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Требуется вход через Google."))
        val safeAddress = address.trim().take(160)
        val safeDistrict = district.trim().ifBlank { "Центр" }.take(60)
        if (safeAddress.length < 2) {
            return Result.failure(IllegalArgumentException("Укажите улицу или ориентир парковки."))
        }
        val clampedMinutes = leaveInMinutes.coerceIn(0, 120).toLong()
        val initialStatus = if (clampedMinutes == 0L) "free" else "leaving_soon"
        val lat = location?.latitude?.coerceIn(-90.0, 90.0) ?: 0.0
        val lng = location?.longitude?.coerceIn(-180.0, 180.0) ?: 0.0

        val docRef = db.collection("parking_spots").document()
        return try {
            val payload = mapOf(
                "posterId" to uid,
                "posterName" to posterName.trim().ifBlank { "Водитель" }.take(60),
                "posterCar" to posterCar.trim().take(80),
                "address" to safeAddress,
                "district" to safeDistrict,
                "parkingKind" to parkingKind.wireValue,
                "note" to note.trim().take(240),
                "latitude" to lat,
                "longitude" to lng,
                "hasCoordinates" to (location != null),
                "leaveInMinutes" to clampedMinutes,
                "status" to initialStatus,
                "publicFeed" to true,
                "claimedByUid" to "",
                "claimedByName" to "",
                "claimedByCar" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            // Increment user's shared spots counter (best-effort)
            runCatching {
                db.collection("public_profiles").document(uid).update(
                    mapOf(
                        "spotsShared" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun catchParkingSpot(
        spotId: String,
        claimerName: String,
        claimerCar: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Требуется вход через Google."))
        val path = "parking_spots/$spotId"
        return try {
            require(spotId.isNotBlank()) { "ID парковки не может быть пустым." }
            db.collection("parking_spots").document(spotId).update(
                mapOf(
                    "status" to "claimed",
                    "claimedByUid" to uid,
                    "claimedByName" to claimerName.trim().ifBlank { "Водитель" }.take(60),
                    "claimedByCar" to claimerCar.trim().take(80),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            runCatching {
                db.collection("public_profiles").document(uid).update(
                    mapOf(
                        "spotsCaught" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun releaseParkingSpot(spotId: String): Result<Unit> {
        val path = "parking_spots/$spotId"
        return try {
            requireUserId()
            require(spotId.isNotBlank()) { "ID парковки не может быть пустым." }
            db.collection("parking_spots").document(spotId).update(
                mapOf(
                    "status" to "free",
                    "claimedByUid" to "",
                    "claimedByName" to "",
                    "claimedByCar" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun completeParkingSpot(spotId: String): Result<Unit> {
        val path = "parking_spots/$spotId"
        return try {
            requireUserId()
            require(spotId.isNotBlank()) { "ID парковки не может быть пустым." }
            db.collection("parking_spots").document(spotId).update(
                mapOf(
                    "status" to "completed",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteParkingSpot(spotId: String): Result<Unit> {
        val path = "parking_spots/$spotId"
        return try {
            requireUserId()
            require(spotId.isNotBlank()) { "ID парковки не может быть пустым." }
            db.collection("parking_spots").document(spotId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
