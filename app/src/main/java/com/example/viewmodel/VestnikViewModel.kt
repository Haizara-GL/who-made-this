package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CallSession
import com.example.data.Chat
import com.example.data.ChatMessage
import com.example.data.GeoPointData
import com.example.data.ParkingFilter
import com.example.data.ParkingKind
import com.example.data.ParkingSpot
import com.example.data.PublicProfile
import com.example.data.UiState
import com.example.data.VestnikRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class VestnikViewModel(
    private val repository: VestnikRepository,
    val currentUserId: String,
    initialDisplayName: String?,
    initialEmail: String?
) : ViewModel() {

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _parkingFilter = MutableStateFlow(ParkingFilter.ALL_ACTIVE)
    val parkingFilter: StateFlow<ParkingFilter> = _parkingFilter.asStateFlow()

    private val _userLocation = MutableStateFlow<GeoPointData?>(null)
    val userLocation: StateFlow<GeoPointData?> = _userLocation.asStateFlow()

    private val _callMuted = MutableStateFlow(false)
    val callMuted: StateFlow<Boolean> = _callMuted.asStateFlow()

    private val _callSpeaker = MutableStateFlow(false)
    val callSpeaker: StateFlow<Boolean> = _callSpeaker.asStateFlow()

    private val _activeCallElapsedSeconds = MutableStateFlow(0L)
    val activeCallElapsedSeconds: StateFlow<Long> = _activeCallElapsedSeconds.asStateFlow()

    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress.asStateFlow()

    val ownProfile: StateFlow<UiState<PublicProfile?>> =
        repository.observeOwnPublicProfile(currentUserId)
            .toUiStateFlow("Не удалось загрузить профиль")

    val directoryProfiles: StateFlow<UiState<List<PublicProfile>>> =
        repository.observeDirectoryProfiles()
            .toUiStateFlow("Не удалось загрузить список пользователей")

    val chats: StateFlow<UiState<List<Chat>>> =
        repository.observeChats(currentUserId)
            .toUiStateFlow("Не удалось загрузить чаты")

    val selectedChat: StateFlow<Chat?> = combine(chats, _selectedChatId) { chatsState, chatId ->
        if (chatId == null) null
        else (chatsState as? UiState.Success)?.data?.firstOrNull { it.id == chatId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val messages: StateFlow<UiState<List<ChatMessage>>> = selectedChat
        .flatMapLatest { chat ->
            if (chat == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(chat)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing messages", rootError)
                        emit(UiState.Error(rootError.message ?: "Ошибка загрузки сообщений"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val calls: StateFlow<UiState<List<CallSession>>> =
        repository.observeCalls(currentUserId)
            .toUiStateFlow("Не удалось загрузить историю звонков")

    val liveCallSession: StateFlow<CallSession?> = calls.map { state ->
        (state as? UiState.Success)?.data?.firstOrNull {
            it.status == "active" || it.status == "ringing"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val parkingSpots: StateFlow<UiState<List<ParkingSpot>>> =
        repository.observeParkingSpots()
            .toUiStateFlow("Не удалось загрузить карту парковок")

    init {
        viewModelScope.launch {
            repository.ensureUserProfilesInitialized(initialDisplayName, initialEmail)
                .onFailure {
                    Log.w(TAG, "Profile bootstrap failed", it)
                }
        }

        // Live ticker for active call elapsed timer
        viewModelScope.launch {
            liveCallSession.collect { session ->
                if (session == null || session.status != "active") {
                    _activeCallElapsedSeconds.value = 0L
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val live = liveCallSession.value
                if (live != null && live.status == "active") {
                    _activeCallElapsedSeconds.value += 1L
                }
            }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackMessage: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackMessage, rootError)
                emit(UiState.Error(rootError.message ?: fallbackMessage))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    fun currentDisplayName(): String {
        val loaded = (ownProfile.value as? UiState.Success)?.data?.displayName
        return loaded?.takeIf { it.isNotBlank() } ?: "Участник Вестника"
    }

    fun currentCarInfo(): String =
        (ownProfile.value as? UiState.Success)?.data?.carInfo ?: ""

    fun updateUserLocation(location: GeoPointData) {
        _userLocation.value = location
    }

    fun setParkingFilter(filter: ParkingFilter) {
        _parkingFilter.value = filter
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
    }

    fun toggleCallMute() {
        _callMuted.value = !_callMuted.value
    }

    fun toggleCallSpeaker() {
        _callSpeaker.value = !_callSpeaker.value
    }

    // --- PROFILE ACTIONS ---

    fun saveProfile(
        displayName: String,
        handle: String,
        carInfo: String,
        city: String,
        discoverable: Boolean
    ) {
        viewModelScope.launch {
            _actionInProgress.value = true
            repository.updateProfile(displayName, handle, carInfo, city, discoverable)
                .onSuccess { notify("Профиль обновлён") }
                .onFailure { notify(it.message ?: "Ошибка сохранения профиля") }
            _actionInProgress.value = false
        }
    }

    // --- CHAT ACTIONS ---

    fun createGroupChat(title: String, chatType: String, joinable: Boolean, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _actionInProgress.value = true
            repository.createGroupOrParkingChat(title, chatType, joinable, currentDisplayName())
                .onSuccess { chatId ->
                    _selectedChatId.value = chatId
                    notify("Чат «$title» создан")
                    onCreated(chatId)
                }
                .onFailure { notify(it.message ?: "Не удалось создать чат") }
            _actionInProgress.value = false
        }
    }

    fun openDirectChat(peerUserId: String, peerDisplayName: String, onOpened: (String) -> Unit) {
        val existingChats = (chats.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            _actionInProgress.value = true
            repository.createOrOpenDirectChat(
                peerUserId = peerUserId,
                peerDisplayName = peerDisplayName,
                myDisplayName = currentDisplayName(),
                existingChats = existingChats
            ).onSuccess { chatId ->
                _selectedChatId.value = chatId
                onOpened(chatId)
            }.onFailure {
                notify(it.message ?: "Не удалось открыть диалог")
            }
            _actionInProgress.value = false
        }
    }

    fun joinChatByCode(code: String, onJoined: (String) -> Unit) {
        viewModelScope.launch {
            _actionInProgress.value = true
            repository.joinChatByInviteCode(code)
                .onSuccess { chatId ->
                    _selectedChatId.value = chatId
                    notify("Вы присоединились к чату")
                    onJoined(chatId)
                }
                .onFailure { notify(it.message ?: "Не удалось войти по коду") }
            _actionInProgress.value = false
        }
    }

    fun sendTextMessage(text: String) {
        val chat = selectedChat.value ?: return
        viewModelScope.launch {
            repository.sendMessage(
                chat = chat,
                senderName = currentDisplayName(),
                text = text,
                messageType = "text"
            ).onFailure {
                notify(it.message ?: "Не удалось отправить сообщение")
            }
        }
    }

    fun shareSpotToSelectedChat(spot: ParkingSpot) {
        val chat = selectedChat.value ?: return
        val statusText = if (spot.leaveInMinutes > 0L) {
            "Освобождается через ${spot.leaveInMinutes} мин"
        } else {
            "Свободно прямо сейчас"
        }
        viewModelScope.launch {
            repository.sendMessage(
                chat = chat,
                senderName = currentDisplayName(),
                text = "Парковочное место: ${spot.address} (${spot.district}) — $statusText",
                messageType = "parking_share",
                spotAddress = spot.address,
                spotId = spot.id
            ).onSuccess {
                notify("Место отправлено в чат")
            }.onFailure {
                notify(it.message ?: "Не удалось отправить парковку в чат")
            }
        }
    }

    // --- CALL ACTIONS ---

    fun startCall(calleeId: String, calleeName: String, callType: String) {
        _callMuted.value = false
        _callSpeaker.value = (callType == "video")
        _activeCallElapsedSeconds.value = 0L
        viewModelScope.launch {
            repository.startCall(
                callerName = currentDisplayName(),
                calleeId = calleeId,
                calleeName = calleeName,
                callType = callType
            ).onFailure {
                notify(it.message ?: "Не удалось начать звонок")
            }
        }
    }

    fun startEchoTestCall(callType: String = "audio") {
        startCall(
            calleeId = currentUserId,
            calleeName = "Эхо-тест Вестника",
            callType = callType
        )
    }

    fun answerCall(callId: String) {
        _activeCallElapsedSeconds.value = 0L
        viewModelScope.launch {
            repository.answerCall(callId)
                .onFailure { notify(it.message ?: "Не удалось принять вызов") }
        }
    }

    fun declineCall(callId: String) {
        viewModelScope.launch {
            repository.declineCall(callId)
                .onFailure { notify(it.message ?: "Не удалось отклонить вызов") }
        }
    }

    fun endOrCancelCall(call: CallSession) {
        val elapsed = _activeCallElapsedSeconds.value
        viewModelScope.launch {
            val result = if (call.status == "ringing" && call.callerId == currentUserId && call.calleeId != currentUserId) {
                repository.markCallMissed(call.id)
            } else {
                repository.endCall(call.id, elapsed)
            }
            result.onFailure { notify(it.message ?: "Не удалось завершить вызов") }
        }
    }

    // --- PARKING CATCHER ACTIONS ---

    fun reportParkingSpot(
        address: String,
        district: String,
        parkingKind: ParkingKind,
        note: String,
        leaveInMinutes: Int,
        attachGps: Boolean,
        onReported: () -> Unit
    ) {
        viewModelScope.launch {
            _actionInProgress.value = true
            repository.reportParkingSpot(
                posterName = currentDisplayName(),
                posterCar = currentCarInfo(),
                address = address,
                district = district,
                parkingKind = parkingKind,
                note = note,
                leaveInMinutes = leaveInMinutes,
                location = if (attachGps) _userLocation.value else null
            ).onSuccess {
                notify("Место опубликовано на радаре!")
                onReported()
            }.onFailure {
                notify(it.message ?: "Не удалось опубликовать место")
            }
            _actionInProgress.value = false
        }
    }

    fun catchSpot(spot: ParkingSpot) {
        viewModelScope.launch {
            repository.catchParkingSpot(
                spotId = spot.id,
                claimerName = currentDisplayName(),
                claimerCar = currentCarInfo()
            ).onSuccess {
                notify("Вы поймали место: ${spot.address}")
            }.onFailure {
                notify(it.message ?: "Место уже занято или недоступно")
            }
        }
    }

    fun releaseSpot(spotId: String) {
        viewModelScope.launch {
            repository.releaseParkingSpot(spotId)
                .onSuccess { notify("Место снова открыто для других водителей") }
                .onFailure { notify(it.message ?: "Не удалось освободить бронь") }
        }
    }

    fun completeSpot(spotId: String) {
        viewModelScope.launch {
            repository.completeParkingSpot(spotId)
                .onSuccess { notify("Отлично! Парковка завершена") }
                .onFailure { notify(it.message ?: "Не удалось завершить парковку") }
        }
    }

    fun deleteSpot(spotId: String) {
        viewModelScope.launch {
            repository.deleteParkingSpot(spotId)
                .onSuccess { notify("Объявление удалено") }
                .onFailure { notify(it.message ?: "Не удалось удалить объявление") }
        }
    }

    private fun notify(message: String) {
        _snackbarMessages.tryEmit(message)
    }

    private companion object {
        const val TAG = "VestnikVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
