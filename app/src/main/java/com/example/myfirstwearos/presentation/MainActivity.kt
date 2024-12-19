/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.myfirstwearos.presentation

import android.hardware.Sensor
import android.hardware.Sensor.TYPE_ACCELEROMETER
import android.hardware.Sensor.TYPE_ALL
import android.hardware.Sensor.TYPE_MAGNETIC_FIELD
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_FASTEST
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.CumulativeDataPoint
import androidx.health.services.client.data.DataPoint
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.data.IntervalDataPoint
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.SampleDataPoint
import androidx.health.services.client.data.StatisticalDataPoint
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.myfirstwearos.R
import com.example.myfirstwearos.presentation.theme.MyFirstWearOSTheme
import com.google.android.horologist.compose.layout.fillMaxRectangle
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.TimeSource


private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.all { it.value }) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    backgroundSensorLauncher.launch(android.Manifest.permission.BODY_SENSORS_BACKGROUND)
                }
            }
        }
    val backgroundSensorLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            Log.e(TAG, "background sesnro %$it: ")
        }

    private var orientation: Any? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.BODY_SENSORS,
                android.Manifest.permission.ACTIVITY_RECOGNITION,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )


        val sensorValues = getSensorValues()
        var passiveValues: Map<DataType<*, *>, MutableState<DataPoint<*>?>>? by mutableStateOf(
            null
        )
        var activeValues: Map<DeltaDataType<*, *>, MutableState<List<DataPoint<*>>?>>? by mutableStateOf(
            null
        )
        val addresses = Geocoder(this).getFromLocation(
            27.708317, 85.3205817, 1
        )
        addresses?.forEach { address ->
            Log.e(TAG, "onGeocode: $address")

        }
        var activities: ExerciseCapabilities? by mutableStateOf(null)
        lifecycleScope.launch {
            launch {
                passiveValues = passiveData()

            }
            launch {
                activeValues = getActiveValues()
            }
            launch {
                activities = getActivities()
            }
        }
        setContent {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxRectangle(),
                state = rememberScalingLazyListState()
            ) {
                item {
                    var color by mutableStateOf(generateRandomColor())
                    Button(
                        onClick = {
                            lifecycleScope.launch {
                                sensorValues.forEach {
                                    val sensorValue = it
                                    if (it.timeTaken.isNotEmpty()) {
                                        sensorValue.samplingRate.value =
                                            1_000_000.0 / (sensorValue.timeTaken.sumOf { it.inWholeMicroseconds } / sensorValue.timeTaken.size)
                                    }

                                }
                                color = generateRandomColor()

                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = color
                        )
                    ) {
                        Text("Calculate")
                    }
                }

                item {
                    Text(
                        text = orientation.toString(),
                        color = generateRandomColor()
                    )
                }
                sensorsDate(sensorValues)

                val b = activeValues
                if (b != null) {
                    item {
                        Text("Active Data")
                    }
                    activeData(b)
                }

                val a = passiveValues
                if (a != null) {
                    item {
                        Text("Passive Data")
                    }
                    PassiveValues(
                        passiveValues = a
                    )
                }


                val c = activities
                if (c != null) {
                    item {
                        Text("Exercises")
                    }
                    exerciseCapabilities(
                        exerciseCapabilities = c
                    )
                }
            }
        }
    }

    fun ScalingLazyListScope.exerciseCapabilities(
        modifier: Modifier = Modifier,
        exerciseCapabilities: ExerciseCapabilities
    ) {
        exerciseCapabilities.typeToCapabilities.forEach { (it, capabilities) ->
            item {
                Column {
                    Text(it.name)
                    capabilities.supportedDataTypes.forEach {
                        Text(it.toString())
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        val a = exerciseCapabilities.supportedExerciseTypes.joinToString(separator = ", ")

        Log.e(TAG, "exerciseCapabilities: $a")
    }

    private suspend fun getActivities(): ExerciseCapabilities {
        val client = HealthServices.getClient(this)
        return client.exerciseClient.getCapabilitiesAsync().await()
    }

    fun ScalingLazyListScope.activeData(
        values: Map<DeltaDataType<*, *>, MutableState<List<DataPoint<*>>?>>
    ) {
        for ((dataType, points) in values) {
            item {
                Column {

                    Text(dataType.toString())
                    points.value?.forEach {
                        Text(it.getValue())
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

    }

    private suspend fun getActiveValues(): Map<DeltaDataType<*, *>, MutableState<List<DataPoint<*>>?>> {
        val measureClient =
            HealthServices.getClient(this@MainActivity).measureClient
        val capabilities = measureClient.getCapabilitiesAsync().await()

        val activeDataTypes = capabilities.supportedDataTypesMeasure


        val a = activeDataTypes.associateWith {
            val value: MutableState<List<DataPoint<*>>?> = mutableStateOf(null)
            measureClient.registerMeasureCallback(it, callback = object : MeasureCallback {
                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability
                ) {

                }

                override fun onDataReceived(data: DataPointContainer) {
                    value.value = data.getData(it)
                }
            })
            value
        }

        return a
    }

    class SensorValues(
        val first: MutableState<String>,
        val values: MutableState<FloatArray>,
        val timeTaken: MutableList<Duration>,
        val samplingRate: MutableState<Double>
    )

    fun ScalingLazyListScope.sensorsDate(
        values: List<SensorValues?>
    ) {
        items(values) { event ->
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                text = event.let {
                    "(${it?.first?.value} =${it?.samplingRate?.value}\n ${
                        it?.values?.value?.joinToString(
                            ", ",
                            transform = ::round
                        )
                    })"
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    private fun ScalingLazyListScope.PassiveValues(
        modifier: Modifier = Modifier,
        passiveValues: Map<DataType<*, *>, MutableState<DataPoint<*>?>>
    ) {
        for ((dataType, pointState) in passiveValues) {
            item {
                Column {
                    Text(dataType.toString())
                    Text(pointState.value?.getValue() ?: "null")
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    private suspend fun passiveData(): Map<DataType<*, *>, MutableState<DataPoint<*>?>> {
        val passiveClient =
            HealthServices.getClient(this@MainActivity).passiveMonitoringClient
        val capabilities = passiveClient.getCapabilitiesAsync().await()

        val passiveDataTypes = capabilities.supportedDataTypesPassiveMonitoring
        val values: Map<DataType<*, *>, MutableState<DataPoint<*>?>> =
            passiveDataTypes.associateWith {
                mutableStateOf(null)
            }

        passiveClient.setPassiveListenerCallback(
            PassiveListenerConfig.builder()
                .setDataTypes(
                    passiveDataTypes
                ).build(),
            callback = object : PassiveListenerCallback {

                override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
                    super.onNewDataPointsReceived(dataPoints)
                    dataPoints.sampleDataPoints.forEach { sampleDataPoint ->
                        val state: MutableState<DataPoint<*>?>? =
                            values[sampleDataPoint.dataType]
                        if (state != null) {
                            state.value = sampleDataPoint
                        }
                    }
                    dataPoints.cumulativeDataPoints.forEach { sampleDataPoint ->
                        val state: MutableState<DataPoint<*>?>? =
                            values[sampleDataPoint.dataType]
                        if (state != null) {
                            state.value = sampleDataPoint
                        }
                    }
                    dataPoints.statisticalDataPoints.forEach { sampleDataPoint ->
                        val state: MutableState<DataPoint<*>?>? =
                            values[sampleDataPoint.dataType]
                        if (state != null) {
                            state.value = sampleDataPoint
                        }
                    }
                    dataPoints.intervalDataPoints.forEach { sampleDataPoint ->
                        val state: MutableState<DataPoint<*>?>? =
                            values[sampleDataPoint.dataType]
                        if (state != null) {
                            state.value = sampleDataPoint
                        }
                    }
                }
            }

        )
        return values
    }

    private fun getSensorValues(): List<SensorValues> {

        val sensorManager = this.getSystemService<SensorManager>()!!
        val sensors = sensorManager.getSensorList(TYPE_ALL).distinctBy { it.type }
        sensors.forEach { sensor ->
            Log.e(TAG, "getSensorValues: $sensor")
        }
        return sensors.map { sensor ->
            var time = TimeSource.Monotonic.markNow()
            val first = "${sensor.name}-${sensor.type}"
            val timeTakenList = mutableListOf<Duration>()

            val name = mutableStateOf(first)
            val values = mutableStateOf(floatArrayOf())
            sensorManager.registerListener(object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type == TYPE_ACCELEROMETER) {
                        System.arraycopy(
                            event.values,
                            0,
                            accelerometerReading,
                            0,
                            accelerometerReading.size
                        )
                        updateOrientationAngles()
                    } else if (event?.sensor?.type == TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(
                            event.values,
                            0,
                            magnetometerReading,
                            0,
                            magnetometerReading.size
                        )
                        updateOrientationAngles()
                    }
                    val new = TimeSource.Monotonic.markNow()
                    val timeTaken = new - time
                    timeTakenList.add(timeTaken)
                    time = new
                    values.value = event?.values?.clone() ?: floatArrayOf()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

                }
            }, sensor, SensorManager.SENSOR_DELAY_FASTEST)

            SensorValues(
                first = name,
                values = values,
                timeTaken = timeTakenList,
                samplingRate = mutableStateOf(-1.0)
            )
        }


    }

    fun updateOrientationAngles() {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)


        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )


        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        orientation = orientationAngles.map {
            Math.toDegrees(it.toDouble()).toFloat().roundToInt()
        }

        // "orientationAngles" now has up-to-date information.
    }
}


fun generateRandomColor(): Color {
    return Color(
        red = (0..255).random(),
        green = (0..255).random(),
        blue = (0..255).random()
    )
}

fun DataPoint<*>.getValue(): String {
    val dataPoint = this
    return StringBuilder().apply {
        when (dataPoint) {
            is StatisticalDataPoint<*> -> {
                appendLine("Average = ${dataPoint.average}")
                appendLine("Max = ${dataPoint.max}")
                appendLine("Min = ${dataPoint.min}")
                appendLine("Start Time = ${dataPoint.start}")
                appendLine("End Time = ${dataPoint.end}")
            }

            is SampleDataPoint<*> -> {
                appendLine("Value = ${dataPoint.value}")
            }

            is CumulativeDataPoint<*> -> {
                appendLine("Start = ${dataPoint.start}")
                appendLine("End = ${dataPoint.end}")
                appendLine("Total = ${dataPoint.total}")
            }

            is IntervalDataPoint<*> -> {
                appendLine("Start = ${dataPoint.startDurationFromBoot}")
                appendLine("End = ${dataPoint.endDurationFromBoot}")
                appendLine("Value = ${dataPoint.value}")
            }

            else -> {
                appendLine("Testing")
            }
        }
    }.toString()
}

fun round(value: Float): CharSequence {
    return "%.2f".format(value)
}

@Composable
fun WearApp(greetingName: String) {
    MyFirstWearOSTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            Greeting(greetingName = greetingName)
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Android")
}