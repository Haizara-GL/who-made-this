package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.ui.components.StarOfDavidLoader
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.CallSession
import com.example.data.PublicProfile
import com.example.data.UiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVestnikColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    callsState: UiState<List<CallSession>>,
    directoryState: UiState<List<PublicProfile>>,
    currentUserId: String,
    onStartCall: (String, String, String) -> Unit,
    onStartEchoTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extendedColors = LocalVestnikColors.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingCallAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        val action = pendingCallAction
        pendingCallAction = null
        action?.invoke()
    }

    fun launchWithAudioCheck(action: () -> Unit) {
        if (hasAudioPermission) {
            action()
        } else {
            pendingCallAction = action
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Звонки",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Защищённая голосовая и видеосвязь",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Echo Test & Quick Call Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = extendedColors.heroNavy),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Линия связи Вестник",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = if (hasAudioPermission) {
                            "Микрофон активен • Готово к звонкам"
                        } else {
                            "Нажмите для проверки звука и связи"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = { launchWithAudioCheck(onStartEchoTest) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = extendedColors.emeraldLive,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("echo_test_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Эхо-тест")
                }
            }
        }

        // Quick-dial contacts from public directory
        val contacts = (directoryState as? UiState.Success)?.data
            ?.filter { it.userId != currentUserId }
            .orEmpty()

        if (contacts.isNotEmpty()) {
            Text(
                text = "Быстрый вызов",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(contacts, key = { it.userId }) { contact ->
                    QuickContactCard(
                        profile = contact,
                        onAudioCall = {
                            launchWithAudioCheck {
                                onStartCall(contact.userId, contact.displayName, "audio")
                            }
                        },
                        onVideoCall = {
                            launchWithAudioCheck {
                                onStartCall(contact.userId, contact.displayName, "video")
                            }
                        }
                    )
                }
            }
        }

        Text(
            text = "История вызовов",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
        )

        when (callsState) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    StarOfDavidLoader(label = "Загрузка звонков...")
                }
            }
            is UiState.Error -> {
                ErrorPlaceholderCard(
                    message = callsState.message,
                    modifier = Modifier.padding(16.dp)
                )
            }
            is UiState.Success -> {
                if (callsState.data.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneInTalk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Звонков пока нет",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Позвоните собеседнику из чата, профиля или запустите «Эхо-тест» выше.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(callsState.data, key = { it.id }) { call ->
                            CallHistoryRow(
                                call = call,
                                currentUserId = currentUserId,
                                onCallBack = { type ->
                                    val peerId = if (call.callerId == currentUserId) call.calleeId else call.callerId
                                    val peerName = if (call.callerId == currentUserId) call.calleeName else call.callerName
                                    launchWithAudioCheck {
                                        onStartCall(peerId, peerName, type)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickContactCard(
    profile: PublicProfile,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    val extendedColors = LocalVestnikColors.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.width(156.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledIconButton(
                    onClick = onAudioCall,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = extendedColors.emeraldContainer,
                        contentColor = extendedColors.emeraldLive
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.cd_audio_call),
                        modifier = Modifier.size(20.dp)
                    )
                }
                FilledIconButton(
                    onClick = onVideoCall,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = stringResource(R.string.cd_video_call),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(
    call: CallSession,
    currentUserId: String,
    onCallBack: (String) -> Unit
) {
    val extendedColors = LocalVestnikColors.current
    val outgoing = call.callerId == currentUserId
    val peerName = if (outgoing) call.calleeName else call.callerName
    val missedOrDeclined = call.status == "missed" || call.status == "declined"

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (missedOrDeclined) MaterialTheme.colorScheme.errorContainer
                        else extendedColors.emeraldContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        missedOrDeclined -> Icons.AutoMirrored.Rounded.CallMissed
                        outgoing -> Icons.AutoMirrored.Rounded.CallMade
                        else -> Icons.AutoMirrored.Rounded.CallReceived
                    },
                    contentDescription = null,
                    tint = if (missedOrDeclined) MaterialTheme.colorScheme.error else extendedColors.emeraldLive
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusLabel = when (call.status) {
                    "ended" -> "Завершён • ${formatDuration(call.durationSeconds)}"
                    "declined" -> "Отклонён"
                    "missed" -> "Пропущен"
                    "active" -> "В разговоре"
                    else -> "Вызов..."
                }
                Text(
                    text = "${if (call.callType == "video") "Видео" else "Аудио"} • $statusLabel • ${formatShortTime(call.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onCallBack(call.callType) },
                modifier = Modifier.testTag("callback_button_${call.id}")
            ) {
                Icon(
                    imageVector = if (call.callType == "video") Icons.Rounded.Videocam else Icons.Rounded.Call,
                    contentDescription = "Перезвонить",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    call: CallSession,
    currentUserId: String,
    elapsedSeconds: Long,
    muted: Boolean,
    speakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit
) {
    val context = LocalContext.current
    val extendedColors = LocalVestnikColors.current
    val isSelfEcho = call.callerId == currentUserId && call.calleeId == currentUserId
    val isIncomingRinging = call.status == "ringing" && call.calleeId == currentUserId && !isSelfEcho
    val peerName = if (call.callerId == currentUserId) call.calleeName else call.callerName

    // Real AudioManager speakerphone routing
    DisposableEffect(speakerOn) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        runCatching {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = speakerOn
        }
        onDispose {
            runCatching {
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = false
                audioManager?.mode = AudioManager.MODE_NORMAL
            }
        }
    }

    // Real subtle ringback tone while ringing, and auto-answer Echo Test after 1.5s
    LaunchedEffect(call.id, call.status) {
        if (call.status == "ringing") {
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 50) }.getOrNull()
            try {
                if (isSelfEcho) {
                    tone?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 900)
                    delay(1400L)
                    onAnswer()
                } else {
                    while (true) {
                        tone?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1000)
                        delay(3000L)
                    }
                }
            } finally {
                runCatching { tone?.release() }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "call_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        extendedColors.heroNavy,
                        Color(0xFF07172E)
                    )
                )
            )
            .safeDrawingPadding()
            .padding(24.dp)
            .testTag("active_call_overlay")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top badge
            Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = if (call.callType == "video") "ВЕСТНИК • ВИДЕОЗВОНОК" else "ВЕСТНИК • АУДИОЗВОНОК",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Center pulsing avatar & live timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier.size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baseRadius = size.minDimension * 0.30f
                        val maxRadius = size.minDimension * 0.48f
                        val ringColor = if (call.status == "active") {
                            extendedColors.emeraldLive
                        } else {
                            extendedColors.amberAlert
                        }
                        for (i in 0..1) {
                            val p = (pulse + i * 0.5f) % 1f
                            val r = baseRadius + (maxRadius - baseRadius) * p
                            drawCircle(
                                color = ringColor.copy(alpha = (1f - p) * 0.55f),
                                radius = r,
                                style = Stroke(width = 4.dp.toPx())
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Text(
                    text = peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )

                Text(
                    text = when {
                        call.status == "active" -> formatDuration(elapsedSeconds)
                        isIncomingRinging -> "Входящий вызов..."
                        else -> "Соединение..."
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (call.status == "active") extendedColors.emeraldLive else extendedColors.amberAlert
                )
            }

            // Bottom call controls
            if (isIncomingRinging) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onDecline,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("decline_call_button")
                        ) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "Отклонить", modifier = Modifier.size(30.dp))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Отклонить", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onAnswer,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = extendedColors.emeraldLive,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("answer_call_button")
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Принять", modifier = Modifier.size(30.dp))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Принять", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlCircle(
                        icon = if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        label = if (muted) "Вкл. микр." else "Без звука",
                        active = muted,
                        onClick = onToggleMute,
                        tag = "call_mute_button"
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onEnd,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(70.dp)
                                .testTag("end_call_button")
                        ) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "Завершить звонок", modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Завершить", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    CallControlCircle(
                        icon = if (speakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                        label = "Громкая",
                        active = speakerOn,
                        onClick = onToggleSpeaker,
                        tag = "call_speaker_button"
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (active) Color.White else Color.White.copy(alpha = 0.16f),
                contentColor = if (active) Color(0xFF0D2B52) else Color.White
            ),
            modifier = Modifier
                .size(58.dp)
                .testTag(tag)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
    }
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
}
