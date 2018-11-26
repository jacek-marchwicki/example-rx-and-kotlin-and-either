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

private class IdlingSchedulerWrapper(private val wrapped: Scheduler, private val countingIdlingResource: CountingIdlingResource) : Scheduler() {

    inner class IdlingRunnable(private val runnable: Runnable) : Runnable {
        override fun run() {
            countingIdlingResource.increment()
            try {
                runnable.run()
            } finally {
                countingIdlingResource.decrement()
            }
        }
    }

    inner class IdlingWorkerWrapper(private val wrapped: Worker) : Worker() {
        override fun isDisposed(): Boolean = wrapped.isDisposed
        override fun dispose() = wrapped.dispose()
        override fun now(unit: TimeUnit): Long = wrapped.now(unit)
        override fun schedule(run: Runnable): Disposable = wrapped.schedule(IdlingRunnable(run))
        override fun schedule(run: Runnable, delay: Long, unit: TimeUnit): Disposable = wrapped.schedule(IdlingRunnable(run), delay, unit)
        override fun schedulePeriodically(run: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): Disposable = wrapped.schedulePeriodically(IdlingRunnable(run), initialDelay, period, unit)
    }

    override fun schedulePeriodicallyDirect(run: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): Disposable = wrapped.schedulePeriodicallyDirect(IdlingRunnable(run), initialDelay, period, unit)
    override fun scheduleDirect(run: Runnable): Disposable = wrapped.scheduleDirect(IdlingRunnable(run))
    override fun scheduleDirect(run: Runnable, delay: Long, unit: TimeUnit): Disposable = wrapped.scheduleDirect(IdlingRunnable(run), delay, unit)
    override fun shutdown() = wrapped.shutdown()
    override fun start() = wrapped.start()
    override fun now(unit: TimeUnit): Long = wrapped.now(unit)
    override fun <S> `when`(combine: Function<Flowable<Flowable<Completable>>, Completable>): S where S : Disposable?, S : Scheduler =
            wrapped.`when`(combine)

    override fun createWorker(): Worker = IdlingWorkerWrapper(wrapped.createWorker())
}