package com.jinatra.wend

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jinatra.wend.designsystem.WendTheme
import com.jinatra.wend.today.MinuteClock
import com.jinatra.wend.today.SampleDay
import com.jinatra.wend.today.TodayController
import com.jinatra.wend.today.TodayRoute
import com.jinatra.wend.today.TodayViewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WendTheme {
                TodayRoute(viewModel(factory = SampleTodayFactory))
            }
        }
    }
}

/**
 * Prototype wiring: an in-memory sample day and a clock that starts mid-morning and
 * then advances in real time. Replaced by `:core:data` and the system clock in Phase 1.
 */
private object SampleTodayFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val started = SystemClock.elapsedRealtime()
        val clock = MinuteClock { SampleDay.START_MINUTE + ((SystemClock.elapsedRealtime() - started) / 60_000L).toInt() }
        return TodayViewModel(TodayController(SampleDay.create(LocalDate.now())), clock, isSample = true) as T
    }
}
