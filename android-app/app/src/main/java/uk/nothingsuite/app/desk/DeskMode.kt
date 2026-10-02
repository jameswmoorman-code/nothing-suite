package uk.nothingsuite.app.desk

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desk mode: the phone lying face down means "I'm busy".
 *
 * While it's face down (and the setting is on) every incoming call goes straight
 * to the concierge without ringing, and the Concierge Glyph face shows the time
 * and how many calls it has taken for you. Flip it over and calls ring as normal.
 *
 * Detection is the gravity sensor: z strongly negative = screen facing the desk.
 * Two seconds of debounce so picking the phone up and putting it down doesn't flap.
 */
object DeskMode : SensorEventListener {
    private const val TAG = "DeskMode"
    private const val FACE_DOWN_Z = -8.0f
    private const val FACE_UP_Z = -6.0f
    private const val SETTLE_MS = 2000L

    private val _faceDown = MutableStateFlow(false)
    /** True once the phone has been lying face down for a couple of seconds. */
    val faceDown: StateFlow<Boolean> = _faceDown.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var candidate = false
    private var sensors: SensorManager? = null

    fun start(context: Context) {
        if (sensors != null) return
        val sm = context.applicationContext.getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensors = sm
    }

    fun stop() { sensors?.unregisterListener(this); sensors = null; pending?.let(handler::removeCallbacks); _faceDown.value = false }

    override fun onSensorChanged(event: SensorEvent) {
        val z = event.values.getOrNull(2) ?: return
        val now = if (_faceDown.value) z < FACE_UP_Z else z < FACE_DOWN_Z   // hysteresis
        if (now == candidate) return
        candidate = now
        pending?.let(handler::removeCallbacks)
        pending = Runnable {
            if (_faceDown.value != candidate) { _faceDown.value = candidate; Log.i(TAG, if (candidate) "face down → desk mode" else "picked up → normal") }
        }.also { handler.postDelayed(it, SETTLE_MS) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
