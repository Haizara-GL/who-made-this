package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class PrivateUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handle: String = "",
    val email: String = "",
    val carInfo: String = "",
    val city: String = "Москва",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handle: String = "",
    val carInfo: String = "",
    val city: String = "Москва",
    val discoverable: Boolean = true,
    val spotsShared: Long = 0L,
    val spotsCaught: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Chat(
    @DocumentId val id: String = "",
    val title: String = "",
    val chatType: String = "group",
    val creatorId: String = "",
    val memberUids: List<String> = emptyList(),
    val inviteCode: String = "",
    val joinable: Boolean = false,
    val lastMessageText: String = "",
    val lastMessageSender: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val messageType: String = "text",
    val spotAddress: String = "",
    val spotId: String = "",
    val memberUids: List<String> = emptyList(),
    val publicChannel: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = "audio",
    val status: String = "ringing",
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ParkingSpot(
    @DocumentId val id: String = "",
    val posterId: String = "",
    val posterName: String = "",
    val posterCar: String = "",
    val address: String = "",
    val district: String = "Центр",
    val parkingKind: String = "free",
    val note: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val hasCoordinates: Boolean = false,
    val leaveInMinutes: Long = 0L,
    val status: String = "free",
    val publicFeed: Boolean = true,
    val claimedByUid: String = "",
    val claimedByName: String = "",
    val claimedByCar: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ParkingKind(val wireValue: String, val labelRu: String) {
    FREE("free", "Бесплатная"),
    PAID("paid", "Городская"),
    YARD("yard", "Дворовая"),
    COVERED("covered", "Крытая");

    companion object {
        fun fromWire(raw: String): ParkingKind =
            entries.firstOrNull { it.wireValue == raw } ?: FREE
    }
}

enum class ParkingStatus(val wireValue: String, val labelRu: String) {
    FREE("free", "Свободно"),
    LEAVING_SOON("leaving_soon", "Освобождается"),
    CLAIMED("claimed", "Поймано"),
    COMPLETED("completed", "Завершено");

    companion object {
        fun fromWire(raw: String): ParkingStatus =
            entries.firstOrNull { it.wireValue == raw } ?: FREE
    }
}

enum class ParkingFilter(val labelRu: String) {
    ALL_ACTIVE("Все активные"),
    FREE_NOW("Свободные"),
    LEAVING_SOON("Освобождаются"),
    MY_SPOTS("Мои")
}

data class GeoPointData(
    val latitude: Double,
    val longitude: Double,
    val label: String = ""
)

fun calculateDistanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Int {
    val earthRadiusMeters = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadiusMeters * c).toInt()
}

fun formatDistanceRu(meters: Int): String = when {
    meters < 1000 -> "$meters м"
    else -> String.format(java.util.Locale.US, "%.1f км", meters / 1000.0)
}
