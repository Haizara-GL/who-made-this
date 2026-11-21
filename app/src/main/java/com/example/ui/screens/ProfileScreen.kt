package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PublicProfile
import com.example.data.UiState
import com.example.ui.components.StarOfDavidLoader
import com.example.ui.theme.LocalVestnikColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    ownProfileState: UiState<PublicProfile?>,
    directoryState: UiState<List<PublicProfile>>,
    currentUserId: String,
    actionInProgress: Boolean,
    onSaveProfile: (String, String, String, String, Boolean) -> Unit,
    onMessageUser: (String, String) -> Unit,
    onCallUser: (String, String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalVestnikColors.current
    val profile = (ownProfileState as? UiState.Success)?.data

    var displayName by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var carInfo by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("Москва") }
    var discoverable by remember { mutableStateOf(true) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        if (profile != null && !initialized) {
            displayName = profile.displayName
            handle = profile.handle
            carInfo = profile.carInfo
            city = profile.city
            discoverable = profile.discoverable
            initialized = true
        }
    }

    val otherUsers = (directoryState as? UiState.Success)?.data
        ?.filter { it.userId != currentUserId }
        .orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            TopAppBar(
                title = {
                    Column {
                        Text("Профиль", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Визитка водителя и каталог Вестника",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }

        // Stats Banner Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = extendedColors.heroNavy),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(extendedColors.amberAlert),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ownProfileState is UiState.Loading) {
                                StarOfDavidLoader(
                                    size = 32.dp,
                                    color = Color.White,
                                    accentColor = Color.White.copy(alpha = 0.85f),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = (profile?.displayName?.take(1) ?: "В").uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                            }
                        }
                        Column {
                            Text(
                                text = profile?.displayName?.ifBlank { "Участник Вестника" } ?: "Загрузка...",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = "@${profile?.handle.orEmpty().ifBlank { "vestnik" }} • ${profile?.city.orEmpty().ifBlank { "Москва" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileStatTile(
                            value = (profile?.spotsShared ?: 0L).toString(),
                            label = "Отдано мест",
                            tint = extendedColors.amberAlert,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileStatTile(
                            value = (profile?.spotsCaught ?: 0L).toString(),
                            label = "Поймано мест",
                            tint = extendedColors.emeraldLive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Editable Profile Form
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Данные профиля и автомобиля",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(60) },
                        label = { Text("Имя в мессенджере") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input")
                    )

                    OutlinedTextField(
                        value = handle,
                        onValueChange = { handle = it.take(32) },
                        label = { Text("Никнейм (без @)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_handle_input")
                    )

                    OutlinedTextField(
                        value = carInfo,
                        onValueChange = { carInfo = it.take(80) },
                        label = { Text("Автомобиль для передачи парковки") },
                        placeholder = { Text("Напр. Белый Haval • А123ВС") },
                        leadingIcon = { Icon(Icons.Rounded.DirectionsCar, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_car_input")
                    )

                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it.take(60) },
                        label = { Text("Город") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_city_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Показывать в каталоге Вестника", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Другие участники смогут найти вас для чата или звонка",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = discoverable,
                            onCheckedChange = { discoverable = it },
                            modifier = Modifier.testTag("profile_discoverable_switch")
                        )
                    }

                    Button(
                        onClick = {
                            onSaveProfile(displayName, handle, carInfo, city, discoverable)
                        },
                        enabled = displayName.trim().isNotEmpty() && !actionInProgress,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_profile_button")
                    ) {
                        if (actionInProgress) {
                            StarOfDavidLoader(
                                size = 22.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                accentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Сохранить профиль")
                        }
                    }
                }
            }
        }

        // Registered Users Directory
        item {
            Text(
                text = "Участники Вестника (${otherUsers.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (otherUsers.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Как только другие пользователи войдут в Вестник с открытым профилем, они появятся здесь для быстрого чата и звонков.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(otherUsers, key = { it.userId }) { user ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                            val meta = buildString {
                                append("@${user.handle}")
                                if (user.carInfo.isNotBlank()) append(" • ${user.carInfo}")
                            }
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { onMessageUser(user.userId, user.displayName) }
                        ) {
                            Icon(Icons.Rounded.ChatBubble, contentDescription = "Написать")
                        }
                        FilledTonalIconButton(
                            onClick = { onCallUser(user.userId, user.displayName) }
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Позвонить")
                        }
                    }
                }
            }
        }

        // Sign Out
        item {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sign_out), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProfileStatTile(
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalParking,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }
        }
    }
}
