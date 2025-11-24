package com.example.compassdigitalsensorapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compassdigitalsensorapp.ui.theme.CompassDigitalSensorAppTheme
import kotlin.math.atan2
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var magnetometer: Sensor? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val gyroscopeReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var azimuth by mutableStateOf(0f)
    private var pitch by mutableStateOf(0f)
    private var roll by mutableStateOf(0f)

    // Sensor fusion variables
    private var fusedPitch = 0f
    private var fusedRoll = 0f
    private var lastTimestamp = 0L
    private val alpha = 0.98f // Complementary filter coefficient (0.96-0.98 works well)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        enableEdgeToEdge()
        setContent {
            CompassDigitalSensorAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0E27)
                ) {
                    CompassAndLevelScreen(
                        azimuth = azimuth,
                        pitch = pitch,
                        roll = roll
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        magnetometer?.also { mag ->
            sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
        }
        accelerometer?.also { acc ->
            sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.also { gyro ->
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        lastTimestamp = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, gyroscopeReading, 0, gyroscopeReading.size)
                fusePitchAndRoll(event.timestamp)
            }
        }

        updateOrientationAngles()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun fusePitchAndRoll(timestamp: Long) {
        // Calculate time delta in seconds
        val dt = if (lastTimestamp != 0L) {
            (timestamp - lastTimestamp) * 1e-9f
        } else {
            0f
        }
        lastTimestamp = timestamp

        if (dt == 0f || dt > 1f) return // Skip first iteration or if dt is too large

        // Calculate pitch and roll from accelerometer
        val accX = accelerometerReading[0]
        val accY = accelerometerReading[1]
        val accZ = accelerometerReading[2]

        val accelPitch = Math.toDegrees(
            atan2(-accX.toDouble(), Math.sqrt((accY * accY + accZ * accZ).toDouble()))
        ).toFloat()

        val accelRoll = Math.toDegrees(
            atan2(accY.toDouble(), accZ.toDouble())
        ).toFloat()

        // Get gyroscope angular velocities (in rad/s)
        val gyroX = gyroscopeReading[0]
        val gyroY = gyroscopeReading[1]

        // Integrate gyroscope data to get angle changes
        val gyroPitchDelta = Math.toDegrees(gyroX.toDouble()).toFloat() * dt
        val gyroRollDelta = Math.toDegrees(gyroY.toDouble()).toFloat() * dt

        // Complementary filter: combine gyroscope and accelerometer data
        // High-pass filter on gyroscope (tracks fast changes)
        // Low-pass filter on accelerometer (provides long-term stability)
        fusedPitch = alpha * (fusedPitch + gyroPitchDelta) + (1 - alpha) * accelPitch
        fusedRoll = alpha * (fusedRoll + gyroRollDelta) + (1 - alpha) * accelRoll

        // Update the state
        pitch = fusedPitch
        roll = fusedRoll
    }

    private fun updateOrientationAngles() {
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        if (azimuth < 0) azimuth += 360f
    }
}

@Composable
fun CompassAndLevelScreen(
    azimuth: Float,
    pitch: Float,
    roll: Float
) {
    val isLevel = pitch in -2f..2f && roll in -2f..2f
    val backgroundColor = if (isLevel) Color(0xFF4CAF50) else Color(0xFFFF6363)


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Compass Section
        CompassView(azimuth = azimuth)

        // Level Section
        DigitalLevelView(pitch = pitch, roll = roll)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun CompassView(azimuth: Float) {
    val animatedAzimuth by animateFloatAsState(
        targetValue = azimuth,
        animationSpec = tween(100, easing = LinearEasing),
        label = "azimuth"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Direction text
        Text(
            text = getDirection(azimuth),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF63B6FF)
        )

        Text(
            text = "${azimuth.roundToInt()}°",
            fontSize = 32.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Compass circle
        Box(
            modifier = Modifier.size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            CompassCircle(rotation = -animatedAzimuth)
            CompassNeedle()
        }
    }
}

@Composable
fun CompassCircle(rotation: Float) {
    Canvas(
        modifier = Modifier
            .size(280.dp)
            .rotate(rotation)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1A1F3A), Color(0xFF0A2327))
            ),
            radius = radius,
            center = center
        )

        drawCircle(
            color = Color(0xFF63B6FF),
            radius = radius,
            center = center,
            style = Stroke(width = 4f)
        )

        // Cardinal directions
        val directions = listOf("N", "E", "S", "W")
        val angles = listOf(0f, 90f, 180f, 270f)

        directions.forEachIndexed { index, direction ->
            rotate(angles[index], center) {
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = if (direction == "N")
                            android.graphics.Color.rgb(255, 99, 99)
                        else
                            android.graphics.Color.WHITE
                        textSize = 48f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    drawText(
                        direction,
                        center.x,
                        center.y - radius + 60f,
                        paint
                    )
                }
            }
        }

        for (i in 0 until 360 step 10) {
            rotate(i.toFloat(), center) {
                val markerLength = if (i % 30 == 0) 20f else 10f
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(center.x, center.y - radius + 10f),
                    end = Offset(center.x, center.y - radius + 10f + markerLength),
                    strokeWidth = if (i % 30 == 0) 3f else 1f
                )
            }
        }
    }
}

@Composable
fun CompassNeedle() {
    Canvas(modifier = Modifier.size(280.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val needleLength = size.minDimension / 2.5f

        // North pointer
        val path = Path().apply {
            moveTo(center.x, center.y - needleLength)
            lineTo(center.x - 15f, center.y + 20f)
            lineTo(center.x + 15f, center.y + 20f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFF6363), Color(0xFFFF3333))
            )
        )

        // South pointer
        val pathSouth = Path().apply {
            moveTo(center.x, center.y + needleLength)
            lineTo(center.x - 15f, center.y - 20f)
            lineTo(center.x + 15f, center.y - 20f)
            close()
        }
        drawPath(
            path = pathSouth,
            color = Color.White
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF63AEFF), Color(0xFF42CCAC))
            ),
            radius = 20f,
            center = center
        )
    }
}

@Composable
fun DigitalLevelView(pitch: Float, roll: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Roll indicator
        LevelIndicator(
            label = "Roll",
            angle = roll,
            isHorizontal = true
        )

        // Pitch indicator
        LevelIndicator(
            label = "Pitch",
            angle = pitch,
            isHorizontal = false
        )

        // Level status
        LevelStatus(pitch = pitch, roll = roll)
    }
}

@Composable
fun LevelIndicator(label: String, angle: Float, isHorizontal: Boolean) {
    val clampedAngle = angle.coerceIn(-45f, 45f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFAFF63)
        )

        Text(
            text = "${clampedAngle.roundToInt()}°",
            fontSize = 20.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .width(if (isHorizontal) 280.dp else 80.dp)
                .height(if (isHorizontal) 80.dp else 280.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(Color(0xFF1A1F3A)),
            contentAlignment = Alignment.Center
        ) {

            Canvas(modifier = Modifier.fillMaxSize()) {
                val barWidth = if (isHorizontal) size.width * 0.8f else size.width * 0.3f
                val barHeight = if (isHorizontal) size.height * 0.3f else size.height * 0.8f

                drawRoundRect(
                    color = Color(0xFF2A2F4A),
                    topLeft = Offset(
                        (size.width - barWidth) / 2f,
                        (size.height - barHeight) / 2f
                    ),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )

                if (isHorizontal) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(size.width / 2f, size.height * 0.2f),
                        end = Offset(size.width / 2f, size.height * 0.8f),
                        strokeWidth = 2f
                    )
                } else {
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(size.width * 0.2f, size.height / 2f),
                        end = Offset(size.width * 0.8f, size.height / 2f),
                        strokeWidth = 2f
                    )
                }
            }

            val offset = (clampedAngle / 45f) * 100f
            Box(
                modifier = Modifier
                    .offset(
                        x = if (isHorizontal) offset.dp else 0.dp,
                        y = if (isHorizontal) 0.dp else offset.dp
                    )
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF63FFDB),
                                Color(0xFF42C0CC)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun LevelStatus(pitch: Float, roll: Float) {
    val isLevel = pitch in -2f..2f && roll in -2f..2f

    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(
                if (isLevel) Color(0xFF4CAF50) else Color(0xFFFF6363)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text(
                text = if (isLevel) "LEVEL" else "TILTED",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

fun getDirection(azimuth: Float): String {
    return when {
        azimuth < 22.5 || azimuth >= 337.5 -> "N"
        azimuth < 67.5 -> "NE"
        azimuth < 112.5 -> "E"
        azimuth < 157.5 -> "SE"
        azimuth < 202.5 -> "S"
        azimuth < 247.5 -> "SW"
        azimuth < 292.5 -> "W"
        azimuth < 337.5 -> "NW"
        else -> "N"
    }
}
