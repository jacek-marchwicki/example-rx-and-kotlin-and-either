package com.example.sleep.rules

import android.support.test.espresso.idling.CountingIdlingResource
import android.support.test.filters.SmallTest
import android.support.test.runner.AndroidJUnit4
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.AssertionError
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SmallTest
class RxIdlingResourcesRuleTest {

    @Rule @JvmField val rule = RxIdlingResourcesRule()

    @Test
    fun wrappedScheduler_isIdleAfterTriggeringActions() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource)

        scheduler.scheduleDirect { }

        assertFalse(countingIdlingResource.isIdleNow)

        testScheduler.triggerActions()

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedScheduler_isIdleAfterDisposingActions() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource)

        val disposable = scheduler.scheduleDirect { }

        assertFalse(countingIdlingResource.isIdleNow)

        disposable.dispose()

        testScheduler.triggerActions()

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedScheduler_isIdleAfterTriggering0TimeActions() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource)

        scheduler.scheduleDirect({ }, 0L, TimeUnit.SECONDS)

        assertFalse(countingIdlingResource.isIdleNow)

        testScheduler.triggerActions()

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedScheduler_isIdleAfterDisposing0TimeActions() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource)

        val disposable = scheduler.scheduleDirect({ }, 0L, TimeUnit.SECONDS)

        assertFalse(countingIdlingResource.isIdleNow)

        disposable.dispose()

        testScheduler.triggerActions()

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedScheduler_isNotIdleWhenActionsAreDelayed() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource)

        scheduler.scheduleDirect({ }, 10L, TimeUnit.SECONDS)

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedScheduler_isCalledWhenActionsAreExecuted() {
        val countingIdlingResource = CountingIdlingResource("Test")
        val execution = TriggerPassRunnable()
        val scheduler = IdlingSchedulerWrapper(Schedulers.computation(), countingIdlingResource)

        scheduler.scheduleDirect(execution, 10L, TimeUnit.MILLISECONDS)

        execution.waitForStart()
        assertFalse(countingIdlingResource.isIdleNow)

        execution.waitForFinished()

        tryUntilSuccess {
            assertTrue(countingIdlingResource.isIdleNow)
        }
    }

    @Test
    fun wrappedScheduler_isNotCalledBeforeExecuted() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        var becomeIdle = 0
        countingIdlingResource.registerIdleTransitionCallback {
            becomeIdle += 1
        }
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource)

        scheduler.scheduleDirect({ }, 10L, TimeUnit.SECONDS)

        assertTrue(countingIdlingResource.isIdleNow)
        assertEquals(0, becomeIdle)
    }

    @Test
    fun wrappedScheduler_isNotIdleWhenActionsAreDelayedPeriodically() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource)

        scheduler.schedulePeriodicallyDirect({ }, 10L, 10L, TimeUnit.SECONDS)

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedScheduler_isCalledWhenActionsAreExecutedPeriodically() {
        val countingIdlingResource = CountingIdlingResource("Test")
        val execution = TriggerPassRunnable()
        val scheduler = IdlingSchedulerWrapper(Schedulers.computation(), countingIdlingResource)

        scheduler.schedulePeriodicallyDirect(execution, 10L, 10L, TimeUnit.MILLISECONDS)

        execution.waitForStart()
        assertFalse(countingIdlingResource.isIdleNow)

        execution.waitForFinished()

        tryUntilSuccess {
            assertTrue(countingIdlingResource.isIdleNow)
        }
    }

    @Test
    fun wrappedSchedulerWorker_isIdleAfterTriggeringActions() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource).createWorker()

        scheduler.schedule { }

        assertFalse(countingIdlingResource.isIdleNow)

        testScheduler.triggerActions()

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedSchedulerWorker_isIdleAfterDisposingActions() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource).createWorker()

        val disposable = scheduler.schedule { }

        assertFalse(countingIdlingResource.isIdleNow)

        disposable.dispose()

        testScheduler.triggerActions()

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedSchedulerWorker_isIdleAfterTriggering0TimeActions() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource).createWorker()

        scheduler.schedule({ }, 0L, TimeUnit.SECONDS)

        assertFalse(countingIdlingResource.isIdleNow)

        testScheduler.triggerActions()

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedSchedulerWorker_isIdleAfterDisposing0TimeActions() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource).createWorker()

        val disposable = scheduler.schedule({ }, 0L, TimeUnit.SECONDS)

        assertFalse(countingIdlingResource.isIdleNow)

        disposable.dispose()

        testScheduler.triggerActions()

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedSchedulerWorker_isNotIdleWhenActionsAreDelayed() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource).createWorker()

        scheduler.schedule({ }, 10L, TimeUnit.SECONDS)

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedSchedulerWorker_isCalledWhenActionsAreExecuted() {
        val countingIdlingResource = CountingIdlingResource("Test")
        val execution = TriggerPassRunnable()
        val scheduler = IdlingSchedulerWrapper(Schedulers.computation(), countingIdlingResource)

        scheduler.createWorker().schedule(execution, 10L, TimeUnit.MILLISECONDS)

        execution.waitForStart()
        assertFalse(countingIdlingResource.isIdleNow)

        execution.waitForFinished()

        tryUntilSuccess {
            assertTrue(countingIdlingResource.isIdleNow)
        }
    }

    @Test
    fun wrappedSchedulerWorker_isNotIdleWhenActionsAreDelayedPeriodically() {
        val testScheduler = TestScheduler()
        val countingIdlingResource = CountingIdlingResource("Test")
        val scheduler = IdlingSchedulerWrapper(testScheduler, countingIdlingResource).createWorker()

        scheduler.schedulePeriodically({ }, 10L, 10L, TimeUnit.SECONDS)

        assertTrue(countingIdlingResource.isIdleNow)
    }

    @Test
    fun wrappedSchedulerWorker_isCalledWhenActionsAreExecutedPeriodically() {
        val countingIdlingResource = CountingIdlingResource("Test")
        val execution = TriggerPassRunnable()
        val scheduler = IdlingSchedulerWrapper(Schedulers.computation(), countingIdlingResource)

        scheduler.createWorker().schedulePeriodically(execution, 10L, 10L, TimeUnit.MILLISECONDS)

        execution.waitForStart()
        assertFalse(countingIdlingResource.isIdleNow)

        execution.waitForFinished()

        tryUntilSuccess {
            assertTrue(countingIdlingResource.isIdleNow)
        }
    }

    fun <T> tryUntilSuccess(assertion: () -> T): T {
        var repeat = 0
        while (true) {
            try {
                return assertion()
            } catch (e: AssertionError) {
                if (repeat < 10) {
                    Thread.sleep(100)
                }
                repeat += 1
            }
        }
    }

    class TriggerPassRunnable : Runnable {
        @Volatile private var finish = false
        @Volatile private var started = false
        @Volatile private var finished = false
        override fun run() {
            if (finished) {
                return
            }
            started = true
            while (!finish) {
                Thread.sleep(10)
            }
            finished = true
        }
        fun waitForStart() {
            while (!started) {
                Thread.sleep(10)
            }
        }
        fun waitForFinished() {
            finish = true
            while (!finished) {
                Thread.sleep(10)
            }
        }
    }
}
