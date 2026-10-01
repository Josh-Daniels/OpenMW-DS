package org.openmw

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.openmw.utils.DisplayRoles
import java.util.concurrent.ConcurrentHashMap

/**
 * "Identify screens" for the Custom display profile: a full-screen label shown briefly on ONE
 * display, so the player can match the names in the pickers to the physical panels.
 *
 * It doubles as a TEST. `startActivity` onto another display neither throws nor reports a refusal
 * (see CompanionActivity's platform rules), so success is recorded from INSIDE: [onResume] notes
 * the display this instance actually landed on. A screen that never reports cannot host the
 * companion as an Activity either, which is the most useful single fact a report from unfamiliar
 * hardware can carry; the results go into the display report.
 *
 * Own task per instance (NEW_TASK | MULTIPLE_TASK, empty taskAffinity in the manifest), so one can
 * be up on every display at once, and it finishes itself after [SHOW_MS] or on a tap.
 */
class IdentifyScreenActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requested = intent.getIntExtra(EXTRA_REQUESTED, -1)
        val actual = currentDisplayId()
        val info = DisplayRoles.screens(this).firstOrNull { it.id == actual }
        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1B1712))
                    .clickable { finish() },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Screen $actual", color = Color(0xFFE0B870), fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text(
                    info?.let { "${it.name} (${it.width} x ${it.height})" } ?: "",
                    color = Color(0xFFD8CCB4),
                    fontSize = 24.sp,
                )
                if (requested != -1 && requested != actual) {
                    Text(
                        "Asked for screen $requested, but it opened here.",
                        color = Color(0xFFC75C5C),
                        fontSize = 18.sp,
                    )
                }
            }
        }
        handler.postDelayed({ finish() }, SHOW_MS)
    }

    override fun onResume() {
        super.onResume()
        val requested = intent.getIntExtra(EXTRA_REQUESTED, -1)
        val actual = currentDisplayId()
        if (requested != -1) results[requested] = actual
        Log.d(TAG, "identify: requested=$requested shown on=$actual")
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun currentDisplayId(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.displayId ?: -1
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.displayId
        }

    companion object {
        private const val TAG = "IdentifyScreen"
        private const val EXTRA_REQUESTED = "requested_display"
        const val SHOW_MS = 4000L

        /** requested display id -> the display it actually appeared on. Absent = never appeared. */
        private val results = ConcurrentHashMap<Int, Int>()

        @Volatile
        private var lastRequested: List<Int> = emptyList()

        /** Show the label on every display that can be offered in the pickers. */
        fun identifyAll(context: Context) {
            results.clear()
            val ids = DisplayRoles.screens(context).map { it.id }
            lastRequested = ids
            for (id in ids) {
                val intent = Intent(context, IdentifyScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    putExtra(EXTRA_REQUESTED, id)
                }
                val options = ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle()
                runCatching { context.startActivity(intent, options) }
                    .onFailure { Log.w(TAG, "identify: launch on $id threw", it) }
            }
        }

        /**
         * One line per display from the last [identifyAll], or null if it has not been run in this
         * process. Read after [SHOW_MS]; a display with no entry never showed the label.
         */
        fun lastResultText(): String? {
            if (lastRequested.isEmpty()) return null
            return lastRequested.joinToString("\n") { id ->
                when (val shown = results[id]) {
                    null -> "Screen $id: could NOT show a window"
                    id -> "Screen $id: shown"
                    else -> "Screen $id: opened on screen $shown instead"
                }
            }
        }
    }
}
