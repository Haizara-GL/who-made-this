package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.GeoPointData
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalVestnikColors
import java.util.Locale

private data class CityPreset(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

private val RussianCityPresets = listOf(
    CityPreset("Москва", 55.7558, 37.6173),
    CityPreset("Санкт-Петербург", 59.9343, 30.3351),
    CityPreset("Казань", 55.7961, 49.1064),
    CityPreset("Екатеринбург", 56.8389, 60.6057),
    CityPreset("Новосибирск", 55.0084, 82.9357),
    CityPreset("Краснодар", 45.0355, 38.9753),
    CityPreset("Нижний Новгород", 56.2965, 43.9361)
)

private const val MAP_SPAN_DEGREES = 0.04

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManualLocationPickerSheet(
    initialLocation: GeoPointData?,
    isLocatingDeviceGps: Boolean,
    onDismiss: () -> Unit,
    onRequestDeviceGps: () -> Unit,
    onConfirmLocation: (GeoPointData) -> Unit
) {
    val extendedColors = LocalVestnikColors.current

    val startLat = initialLocation?.latitude ?: 55.7558
    val startLng = initialLocation?.longitude ?: 37.6173

    var anchorLat by remember { mutableDoubleStateOf(startLat) }
    var anchorLng by remember { mutableDoubleStateOf(startLng) }

    var latText by remember { mutableStateOf(formatCoord(startLat)) }
    var lngText by remember { mutableStateOf(formatCoord(startLng)) }
    var labelText by remember { mutableStateOf(initialLocation?.label.orEmpty()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val parsedLat = parseCoord(latText)
    val parsedLng = parseCoord(lngText)
    val validCoords = parsedLat != null && parsedLat in -90.0..90.0 &&
        parsedLng != null && parsedLng in -180.0..180.0

    fun applyTouchOffset(xFraction: Float, yFraction: Float) {
        val clampedX = xFraction.coerceIn(0f, 1f)
        val clampedY = yFraction.coerceIn(0f, 1f)
        val newLat = (anchorLat + (0.5 - clampedY) * MAP_SPAN_DEGREES).coerceIn(-90.0, 90.0)
        val newLng = (anchorLng + (clampedX - 0.5) * MAP_SPAN_DEGREES).coerceIn(-180.0, 180.0)
        latText = formatCoord(newLat)
        lngText = formatCoord(newLng)
        validationError = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = extendedColors.emeraldLive,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Моё местоположение",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Поставьте точку на карте, выберите город или введите координаты",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Interactive Tap & Drag Map Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(extendedColors.heroNavy)
                    .testTag("manual_location_map_canvas")
                    .pointerInput(anchorLat, anchorLng) {
                        detectTapGestures { offset ->
                            if (size.width > 0 && size.height > 0) {
                                applyTouchOffset(
                                    offset.x / size.width.toFloat(),
                                    offset.y / size.height.toFloat()
                                )
                            }
                        }
                    }
                    .pointerInput(anchorLat, anchorLng) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val pos = change.position
                            if (size.width > 0 && size.height > 0) {
                                applyTouchOffset(
                                    pos.x / size.width.toFloat(),
                                    pos.y / size.height.toFloat()
                                )
                            }
                        }
                    }
            ) {
                val pinXFraction = if (parsedLng != null) {
                    (0.5 + ((parsedLng - anchorLng) / MAP_SPAN_DEGREES)).toFloat().coerceIn(0.05f, 0.95f)
                } else 0.5f
                val pinYFraction = if (parsedLat != null) {
                    (0.5 - ((parsedLat - anchorLat) / MAP_SPAN_DEGREES)).toFloat().coerceIn(0.05f, 0.95f)
                } else 0.5f

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Stylized street grid
                    val gridColor = Color.White.copy(alpha = 0.12f)
                    val dashed = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    for (i in 1..5) {
                        val x = w * (i / 6f)
                        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
                    }
                    for (j in 1..3) {
                        val y = h * (j / 4f)
                        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                    }

                    // Stylized main avenue & crossroad
                    val avenueColor = Color(0xFF3B82F6).copy(alpha = 0.25f)
                    drawLine(avenueColor, Offset(0f, h * 0.62f), Offset(w, h * 0.38f), strokeWidth = 6.dp.toPx())
                    drawLine(avenueColor, Offset(w * 0.34f, 0f), Offset(w * 0.58f, h), strokeWidth = 5.dp.toPx())

                    // Pin position
                    val px = w * pinXFraction
                    val py = h * pinYFraction

                    // Radar rings around selected pin
                    drawCircle(
                        color = extendedColors.emeraldLive.copy(alpha = 0.22f),
                        radius = 34.dp.toPx(),
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = extendedColors.emeraldLive.copy(alpha = 0.55f),
                        radius = 22.dp.toPx(),
                        center = Offset(px, py),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashed)
                    )
                    // Pin outer white border + amber/emerald core
                    drawCircle(
                        color = Color.White,
                        radius = 10.dp.toPx(),
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = extendedColors.amberAlert,
                        radius = 7.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                Surface(
                    color = Color(0xFF07162B).copy(alpha = 0.78f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Нажмите или перетяните метку",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (validCoords) {
                    Surface(
                        color = Color(0xFF07162B).copy(alpha = 0.82f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "${formatCoord(parsedLat!!)}, ${formatCoord(parsedLng!!)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = extendedColors.emeraldLive,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Quick City Presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Быстрый выбор города", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RussianCityPresets.forEach { preset ->
                        val isSelected = labelText == preset.name
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                anchorLat = preset.latitude
                                anchorLng = preset.longitude
                                latText = formatCoord(preset.latitude)
                                lngText = formatCoord(preset.longitude)
                                labelText = preset.name
                                validationError = null
                            },
                            label = { Text(preset.name) },
                            modifier = Modifier.testTag("city_preset_${preset.name}")
                        )
                    }
                }
            }

            // Custom place label
            OutlinedTextField(
                value = labelText,
                onValueChange = { labelText = it.take(60) },
                label = { Text("Название места (необязательно)") },
                placeholder = { Text("Напр. ул. Арбат, 24 или Дом") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_location_label_input")
            )

            // Exact coordinates row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = {
                        latText = it.take(14)
                        validationError = null
                    },
                    label = { Text("Широта") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_location_lat_input")
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = {
                        lngText = it.take(14)
                        validationError = null
                    },
                    label = { Text("Долгота") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_location_lng_input")
                )
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Secondary button to optionally fill from real device GPS
            OutlinedButton(
                onClick = {
                    onRequestDeviceGps()
                    onDismiss()
                },
                enabled = !isLocatingDeviceGps,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("use_device_gps_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLocatingDeviceGps) "Определяем GPS..." else "Определить по GPS устройства"
                )
            }

            // Primary Save Manual Location button
            Button(
                onClick = {
                    val lat = parseCoord(latText)
                    val lng = parseCoord(lngText)
                    if (lat == null || lat !in -90.0..90.0 || lng == null || lng !in -180.0..180.0) {
                        validationError = "Введите корректные координаты (широта -90..90, долгота -180..180)"
                    } else {
                        onConfirmLocation(
                            GeoPointData(
                                latitude = lat,
                                longitude = lng,
                                label = labelText.trim()
                            )
                        )
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_manual_location_button")
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Установить местоположение", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun formatCoord(value: Double): String =
    String.format(Locale.US, "%.5f", value)

private fun parseCoord(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()
