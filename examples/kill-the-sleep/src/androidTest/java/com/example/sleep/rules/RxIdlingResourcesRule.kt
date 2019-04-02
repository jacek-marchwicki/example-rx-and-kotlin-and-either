package com.example.sleep.rules

import android.support.test.espresso.IdlingRegistry
import android.support.test.espresso.idling.CountingIdlingResource
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function
import io.reactivex.plugins.RxJavaPlugins
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.concurrent.TimeUnit

class RxIdlingResourcesRule : TestWatcher() {

    private val idlingIo = CountingIdlingResource("io", true)
    private val idlingComputation = CountingIdlingResource("computation", true)

    override fun starting(description: Description) {
        super.starting(description)

        // Inject schedulers
        RxJavaPlugins.setComputationSchedulerHandler {
            IdlingSchedulerWrapper(it, idlingComputation)
        }
        RxJavaPlugins.setIoSchedulerHandler {
            IdlingSchedulerWrapper(it, idlingIo)
        }

        // Register idling resources in espresso
        IdlingRegistry.getInstance().register(idlingComputation)
        IdlingRegistry.getInstance().register(idlingIo)
    }

    override fun finished(description: Description) {
        // Unregister idling resources from espresso
        IdlingRegistry.getInstance().unregister(idlingComputation)
        IdlingRegistry.getInstance().unregister(idlingIo)

        super.finished(description)
    }
}

class IdlingSchedulerWrapper(private val wrapped: Scheduler, private val countingIdlingResource: CountingIdlingResource) : Scheduler() {

    private class OneTimeIdler(private val referenceCounter: CountingIdlingResource) {
        private var incremented = false
        fun acquire() = synchronized(this) {
            if (!incremented) {
                referenceCounter.increment()
                incremented = true
            }
        }

        fun release() = synchronized(this) {
            if (incremented) {
                referenceCounter.decrement()
                incremented = false
            }
        }
    }

    private class IdlingRunnableRelease(private val runnable: Runnable, private val oneTimeIdler: OneTimeIdler) : Runnable {
        override fun run() {
            oneTimeIdler.acquire()
            try {
                runnable.run()
            } finally {
                oneTimeIdler.release()
            }
        }
    }

    private class DisposableWrapper(private val original: Disposable, private val onDispose: () -> Unit) : Disposable {
        override fun isDisposed(): Boolean = original.isDisposed
        override fun dispose() = original.dispose().also { onDispose() }
    }

    private fun wrap(run: Runnable, func: (Runnable) -> Disposable): Disposable {
        val oneTimeIdler = OneTimeIdler(countingIdlingResource)
        oneTimeIdler.acquire()
        return DisposableWrapper(func(IdlingRunnableRelease(run, oneTimeIdler)), onDispose = { oneTimeIdler.release() })
    }

    inner class IdlingWorkerWrapper(private val wrapped: Worker) : Worker() {
        override fun isDisposed(): Boolean = wrapped.isDisposed
        override fun dispose() = wrapped.dispose()
        override fun now(unit: TimeUnit): Long = wrapped.now(unit)
        override fun schedule(run: Runnable): Disposable = wrap(run) { wrapped.schedule(it) }
        override fun schedule(run: Runnable, delay: Long, unit: TimeUnit): Disposable =
                if (delay <= 0L)
                    wrap(run) { wrapped.schedule(it, delay, unit) } else
                    wrapped.schedule(IdlingRunnableRelease(run, OneTimeIdler(countingIdlingResource)), delay, unit)

        override fun schedulePeriodically(run: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): Disposable = wrapped.schedulePeriodically(IdlingRunnableRelease(run, OneTimeIdler(countingIdlingResource)), initialDelay, period, unit)
    }

    override fun schedulePeriodicallyDirect(run: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): Disposable = wrapped.schedulePeriodicallyDirect(IdlingRunnableRelease(run, OneTimeIdler(countingIdlingResource)), initialDelay, period, unit)
    override fun scheduleDirect(run: Runnable): Disposable = wrap(run) { wrapped.scheduleDirect(it) }
    override fun scheduleDirect(run: Runnable, delay: Long, unit: TimeUnit): Disposable =
            if (delay <= 0L)
                wrap(run) { wrapped.scheduleDirect(it, delay, unit) } else
                wrapped.scheduleDirect(IdlingRunnableRelease(run, OneTimeIdler(countingIdlingResource)), delay, unit)
    override fun shutdown() = wrapped.shutdown()
    override fun start() = wrapped.start()
    override fun now(unit: TimeUnit): Long = wrapped.now(unit)
    override fun <S> `when`(combine: Function<Flowable<Flowable<Completable>>, Completable>): S where S : Disposable?, S : Scheduler =
            wrapped.`when`(combine)

    override fun createWorker(): Worker = IdlingWorkerWrapper(wrapped.createWorker())
}