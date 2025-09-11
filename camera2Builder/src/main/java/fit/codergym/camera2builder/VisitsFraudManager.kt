package fit.codergym.camera2builder

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class VisitsFraudManager(
    private var interval: Duration,
    lifecycle: Lifecycle? = null
) : LifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val visits = ConcurrentHashMap<String, Long>()
    private var purgeJob: Job? = null

    init {
        lifecycle?.addObserver(this)
        startPurgeTimer()
    }

    fun recordVisit(userId: String) {
        visits[userId] = System.currentTimeMillis()
    }

    fun hasRecentVisit(userId: String): Boolean {
        val lastVisit = visits[userId] ?: return false
        val difference = (System.currentTimeMillis() - lastVisit).milliseconds
        return difference <= interval
    }

    fun startPurgeTimer() {
        purgeJob?.cancel()
        purgeJob = scope.launch {
            while (isActive) {
                delay(interval)
                val now = System.currentTimeMillis()
                visits.entries.removeAll { entry ->
                    val difference = (now - entry.value).milliseconds
                    difference > interval
                }
            }
        }
    }

    fun setInterval(newInterval: Duration) {
        if (interval != newInterval) {
            interval = newInterval
            purgeJob?.cancel()
            startPurgeTimer()
        }
    }

    fun dispose() {
        visits.clear()
        purgeJob?.cancel()
        scope.cancel()
    }
}