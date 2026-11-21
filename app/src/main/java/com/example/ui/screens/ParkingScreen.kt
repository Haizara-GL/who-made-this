package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.GeoPointData
import com.example.data.ParkingFilter
import com.example.data.ParkingKind
import com.example.data.ParkingSpot
import com.example.data.ParkingStatus
import com.example.data.UiState
import com.example.data.calculateDistanceMeters
import com.example.data.formatDistanceRu
import com.example.ui.components.ManualLocationPickerSheet
import com.example.ui.components.StarOfDavidLoader
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVestnikColors
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingScreen(
    spotsState: UiState<List<ParkingSpot>>,
    selectedFilter: ParkingFilter,
    userLocation: GeoPointData?,
    currentUserId: String,
    actionInProgress: Boolean,
    onFilterSelected: (ParkingFilter) -> Unit,
    onLocationUpdated: (GeoPointData) -> Unit,
    onReportSpot: (String, String, ParkingKind, String, Int, Boolean, () -> Unit) -> Unit,
    onCatchSpot: (ParkingSpot) -> Unit,
    onReleaseSpot: (String) -> Unit,
    onCompleteSpot: (String) -> Unit,
    onDeleteSpot: (String) -> Unit,
    onMessageDriver: (String, String) -> Unit,
    onCallDriver: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extendedColors = LocalVestnikColors.current
    var showReportSheet by remember { mutableStateOf(false) }
    var showLocationPickerSheet by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(hasAnyLocationPermission(context))
    }

    fun fetchRealGpsLocation() {
        if (!hasAnyLocationPermission(context)) return
        isLocating = true
        requestFreshLocation(
            context = context,
            onResult = { point ->
                isLocating = false
                if (point != null) onLocationUpdated(point)
            }
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            fetchRealGpsLocation()
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && userLocation == null) {
            fetchRealGpsLocation()
        }
    }

    val allSpots = (spotsState as? UiState.Success)?.data.orEmpty()
    val freeCount = remember(allSpots) { allSpots.count { it.status == "free" } }
    val leavingCount = remember(allSpots) { allSpots.count { it.status == "leaving_soon" } }
    val activeSpotsForRadar = remember(allSpots) {
        allSpots.filter { it.status == "free" || it.status == "leaving_soon" || it.status == "claimed" }
    }

    val filteredSpots = remember(allSpots, selectedFilter, currentUserId, userLocation) {
        val base = when (selectedFilter) {
            ParkingFilter.ALL_ACTIVE -> allSpots.filter { it.status != "completed" }
            ParkingFilter.FREE_NOW -> allSpots.filter { it.status == "free" }
            ParkingFilter.LEAVING_SOON -> allSpots.filter { it.status == "leaving_soon" }
            ParkingFilter.MY_SPOTS -> allSpots.filter {
                it.posterId == currentUserId || it.claimedByUid == currentUserId
            }
        }
        if (userLocation != null) {
            base.sortedBy { spot ->
                if (spot.hasCoordinates) {
                    calculateDistanceMeters(
                        userLocation.latitude, userLocation.longitude,
                        spot.latitude, spot.longitude
                    )
                } else {
                    Int.MAX_VALUE
                }
            }
        } else {
            base
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Ловля парковки",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = when {
                                    userLocation != null && userLocation.label.isNotBlank() ->
                                        "${userLocation.label} • Сортировка по близости"
                                    userLocation != null ->
                                        "Точка задана • Сортировка по близости"
                                    else ->
                                        "Живая лента свободных мест"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (userLocation != null) extendedColors.emeraldLive else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showLocationPickerSheet = true },
                            modifier = Modifier.testTag("parking_gps_button")
                        ) {
                            Icon(
                                imageVector = if (userLocation != null) Icons.Rounded.GpsFixed else Icons.Rounded.GpsNotFixed,
                                contentDescription = stringResource(R.string.cd_locate_me),
                                tint = if (userLocation != null) extendedColors.emeraldLive else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }

            // Hero Parking Radar Card
            item {
                ParkingHeroRadarCard(
                    freeCount = freeCount,
                    leavingCount = leavingCount,
                    radarSpots = activeSpotsForRadar,
                    userLocation = userLocation,
                    isLocating = isLocating,
                    hasPermission = hasLocationPermission,
                    onRequestGps = { showLocationPickerSheet = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Filter Chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ParkingFilter.entries) { filter ->
                        FilterChip(
                            selected = filter == selectedFilter,
                            onClick = { onFilterSelected(filter) },
                            label = { Text(filter.labelRu) },
                            modifier = Modifier.testTag("parking_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            // Spots List
            when (spotsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            StarOfDavidLoader(label = "Поиск свободных мест...")
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        ErrorPlaceholderCard(
                            message = spotsState.message,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    if (filteredSpots.isEmpty()) {
                        item {
                            EmptyParkingState(
                                onReportClick = { showReportSheet = true },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    } else {
                        items(filteredSpots, key = { it.id }) { spot ->
                            ParkingSpotCard(
                                spot = spot,
                                userLocation = userLocation,
                                currentUserId = currentUserId,
                                onCatch = { onCatchSpot(spot) },
                                onRelease = { onReleaseSpot(spot.id) },
                                onComplete = { onCompleteSpot(spot.id) },
                                onDelete = { onDeleteSpot(spot.id) },
                                onMessageDriver = onMessageDriver,
                                onCallDriver = onCallDriver,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showReportSheet = true },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.AddLocationAlt,
                    contentDescription = stringResource(R.string.cd_report_spot)
                )
            },
            text = { Text("Сдать место") },
            containerColor = extendedColors.amberAlert,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("report_parking_fab")
        )
    }

    if (showReportSheet) {
        ReportParkingSpotSheet(
            userLocation = userLocation,
            actionInProgress = actionInProgress,
            onDismiss = { showReportSheet = false },
            onSubmit = { address, district, kind, note, minutes, attachGps ->
                onReportSpot(address, district, kind, note, minutes, attachGps) {
                    showReportSheet = false
                }
            }
        )
    }

    if (showLocationPickerSheet) {
        ManualLocationPickerSheet(
            initialLocation = userLocation,
            isLocatingDeviceGps = isLocating,
            onDismiss = { showLocationPickerSheet = false },
            onRequestDeviceGps = {
                if (hasLocationPermission) {
                    fetchRealGpsLocation()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onConfirmLocation = { chosenLocation ->
                onLocationUpdated(chosenLocation)
            }
        )
    }
}

@Composable
private fun ParkingHeroRadarCard(
    freeCount: Int,
    leavingCount: Int,
    radarSpots: List<ParkingSpot>,
    userLocation: GeoPointData?,
    isLocating: Boolean,
    hasPermission: Boolean,
    onRequestGps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalVestnikColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_ring"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = extendedColors.heroNavy),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_parking_banner_1795282342772),
                    contentDescription = stringResource(R.string.cd_parking_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    extendedColors.heroNavy.copy(alpha = 0.90f),
                                    extendedColors.heroNavy.copy(alpha = 0.55f)
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Радар парковки",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RadarStatChip(
                                count = freeCount,
                                label = "свободно",
                                color = extendedColors.emeraldLive
                            )
                            RadarStatChip(
                                count = leavingCount,
                                label = "скоро",
                                color = extendedColors.amberAlert
                            )
                        }
                        FilledTonalButton(
                            onClick = onRequestGps,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("radar_gps_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isLocating -> "Определяем GPS..."
                                    userLocation != null && userLocation.label.isNotBlank() ->
                                        userLocation.label.take(22)
                                    userLocation != null -> String.format(
                                        java.util.Locale.US,
                                        "%.4f, %.4f",
                                        userLocation.latitude,
                                        userLocation.longitude
                                    )
                                    else -> "Указать позицию"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // Live Canvas Radar Scope
                    Canvas(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF07162B).copy(alpha = 0.85f))
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val maxR = size.minDimension * 0.45f

                        // Concentric grid rings
                        listOf(0.35f, 0.7f, 1f).forEach { factor ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.16f),
                                radius = maxR * factor,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        // Animated pulse ring
                        drawCircle(
                            color = extendedColors.emeraldLive.copy(alpha = (1f - sweepProgress) * 0.65f),
                            radius = maxR * sweepProgress,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Center user dot
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx(),
                            center = Offset(cx, cy)
                        )
                        // Live parking spot blips
                        radarSpots.take(10).forEachIndexed { idx, spot ->
                            val angleRad = Math.toRadians((idx * 53.0) + 25.0)
                            val radiusFactor = when {
                                userLocation != null && spot.hasCoordinates -> {
                                    val meters = calculateDistanceMeters(
                                        userLocation.latitude, userLocation.longitude,
                                        spot.latitude, spot.longitude
                                    )
                                    (meters / 2000f).coerceIn(0.22f, 0.90f)
                                }
                                else -> 0.35f + ((idx % 3) * 0.22f)
                            }
                            val px = cx + (maxR * radiusFactor * cos(angleRad)).toFloat()
                            val py = cy + (maxR * radiusFactor * sin(angleRad)).toFloat()
                            val blipColor = when (spot.status) {
                                "free" -> extendedColors.emeraldLive
                                "leaving_soon" -> extendedColors.amberAlert
                                else -> Color(0xFF60A5FA)
                            }
                            drawCircle(
                                color = blipColor.copy(alpha = 0.32f),
                                radius = 7.dp.toPx(),
                                center = Offset(px, py)
                            )
                            drawCircle(
                                color = blipColor,
                                radius = 3.5.dp.toPx(),
                                center = Offset(px, py)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarStatChip(count: Int, label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.22f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = "$count $label",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ParkingSpotCard(
    spot: ParkingSpot,
    userLocation: GeoPointData?,
    currentUserId: String,
    onCatch: () -> Unit,
    onRelease: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onMessageDriver: (String, String) -> Unit,
    onCallDriver: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalVestnikColors.current
    val statusEnum = ParkingStatus.fromWire(spot.status)
    val kindEnum = ParkingKind.fromWire(spot.parkingKind)
    val isPoster = spot.posterId == currentUserId
    val isClaimer = spot.claimedByUid == currentUserId

    val badgeBg = when (statusEnum) {
        ParkingStatus.FREE -> extendedColors.emeraldContainer
        ParkingStatus.LEAVING_SOON -> extendedColors.amberContainer
        ParkingStatus.CLAIMED -> MaterialTheme.colorScheme.primaryContainer
        ParkingStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeFg = when (statusEnum) {
        ParkingStatus.FREE -> extendedColors.onEmeraldContainer
        ParkingStatus.LEAVING_SOON -> extendedColors.onAmberContainer
        ParkingStatus.CLAIMED -> MaterialTheme.colorScheme.onPrimaryContainer
        ParkingStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val distanceText = remember(userLocation, spot.latitude, spot.longitude, spot.hasCoordinates) {
        if (userLocation != null && spot.hasCoordinates) {
            formatDistanceRu(
                calculateDistanceMeters(
                    userLocation.latitude, userLocation.longitude,
                    spot.latitude, spot.longitude
                )
            )
        } else null
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("parking_spot_card_${spot.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = badgeBg, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            text = if (statusEnum == ParkingStatus.LEAVING_SOON && spot.leaveInMinutes > 0L) {
                                "Через ${spot.leaveInMinutes} мин"
                            } else {
                                statusEnum.labelRu
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = kindEnum.labelRu,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (distanceText != null) {
                        Text(
                            text = distanceText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isPoster) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("delete_spot_${spot.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Удалить место",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Address & district
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = spot.address,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    val carSuffix = if (spot.posterCar.isNotBlank()) " • ${spot.posterCar}" else ""
                    Text(
                        text = "${spot.district} • ${spot.posterName}$carSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (spot.note.isNotBlank()) {
                Text(
                    text = spot.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (statusEnum == ParkingStatus.CLAIMED && spot.claimedByName.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val claimerCar = if (spot.claimedByCar.isNotBlank()) " (${spot.claimedByCar})" else ""
                    Text(
                        text = "Едет занять: ${spot.claimedByName}$claimerCar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Actions row
            when (statusEnum) {
                ParkingStatus.FREE, ParkingStatus.LEAVING_SOON -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCatch,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extendedColors.emeraldLive,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("catch_spot_button_${spot.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalParking,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Поймать место", fontWeight = FontWeight.Bold)
                        }

                        if (!isPoster) {
                            FilledTonalButton(
                                onClick = { onMessageDriver(spot.posterId, spot.posterName) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ChatBubble,
                                    contentDescription = "Написать водителю",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                ParkingStatus.CLAIMED -> {
                    if (isPoster || isClaimer) {
                        val otherId = if (isPoster) spot.claimedByUid else spot.posterId
                        val otherName = if (isPoster) spot.claimedByName else spot.posterName
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onComplete,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("complete_spot_button_${spot.id}")
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Припарковался")
                                }
                                OutlinedButton(
                                    onClick = onRelease,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("release_spot_button_${spot.id}")
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Освободить")
                                }
                            }
                            if (otherId.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { onMessageDriver(otherId, otherName) },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.ChatBubble, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Написать")
                                    }
                                    FilledTonalButton(
                                        onClick = { onCallDriver(otherId, otherName) },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Позвонить")
                                    }
                                }
                            }
                        }
                    }
                }

                ParkingStatus.COMPLETED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Парковка успешно передана",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyParkingState(
    onReportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalParking,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = "Свободных мест пока нет",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Выезжаете с парковки? Нажмите «Сдать место», чтобы помочь водителям рядом поймать его без кругов по двору.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onReportClick,
                modifier = Modifier.testTag("empty_report_spot_button")
            ) {
                Text("Сдать парковочное место")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReportParkingSpotSheet(
    userLocation: GeoPointData?,
    actionInProgress: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, ParkingKind, String, Int, Boolean) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("Центр") }
    var kind by remember { mutableStateOf(ParkingKind.FREE) }
    var leaveInMinutes by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    var attachGps by remember { mutableStateOf(userLocation != null) }

    val districts = listOf("Центр", "Север", "Юг", "Запад", "Восток", "Двор")
    val minuteChoices = listOf(0, 5, 10, 15, 30)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Сдать парковочное место",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it.take(160) },
                label = { Text("Адрес или ориентир *") },
                placeholder = { Text("Напр. ул. Тверская, 14, у арки") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("spot_address_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Район", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    districts.forEach { d ->
                        FilterChip(
                            selected = district == d,
                            onClick = { district = d },
                            label = { Text(d) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Когда освободится?", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    minuteChoices.forEach { mins ->
                        FilterChip(
                            selected = leaveInMinutes == mins,
                            onClick = { leaveInMinutes = mins },
                            leadingIcon = if (mins > 0) {
                                { Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            label = { Text(if (mins == 0) "Свободно сейчас" else "Через $mins мин") }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Тип парковки", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ParkingKind.entries.forEach { item ->
                        FilterChip(
                            selected = kind == item,
                            onClick = { kind = item },
                            label = { Text(item.labelRu) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(240) },
                label = { Text("Комментарий для водителя") },
                placeholder = { Text("Влезет кроссовер, заезд со двора...") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("spot_note_input")
            )

            if (userLocation != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Прикрепить мои GPS-координаты", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = String.format(
                                java.util.Locale.US,
                                "%.4f, %.4f",
                                userLocation.latitude,
                                userLocation.longitude
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = attachGps, onCheckedChange = { attachGps = it })
                }
            }

            Button(
                onClick = {
                    onSubmit(address, district, kind, note, leaveInMinutes, attachGps)
                },
                enabled = address.trim().length >= 2 && !actionInProgress,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_parking_spot_button")
            ) {
                if (actionInProgress) {
                    StarOfDavidLoader(
                        size = 22.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        accentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Опубликовать на радаре", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun hasAnyLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

@SuppressLint("MissingPermission")
private fun requestFreshLocation(
    context: Context,
    onResult: (GeoPointData?) -> Unit
) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cts = CancellationTokenSource()
    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
        .addOnSuccessListener { loc ->
            if (loc != null) {
                onResult(GeoPointData(loc.latitude, loc.longitude))
            } else {
                client.lastLocation
                    .addOnSuccessListener { last ->
                        onResult(last?.let { GeoPointData(it.latitude, it.longitude) })
                    }
                    .addOnFailureListener { onResult(null) }
            }
        }
        .addOnFailureListener {
            onResult(null)
        }
}
