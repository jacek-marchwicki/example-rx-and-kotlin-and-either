package com.example.timetravel.rules

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Scheduler
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function
import io.reactivex.plugins.RxJavaPlugins
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class TimeTravelRule(private val defaultDate: Date? = null) : TestWatcher() {
    private val timeMockedSchedulers = arrayListOf<MockTimeSchedulerWrapper>()
    private var difference: Long = 0L

    override fun starting(description: Description?) {
        super.starting(description)

        defaultDate?.let { timeTravel(it) }

        RxJavaPlugins.setComputationSchedulerHandler {
            MockTimeSchedulerWrapper(it).also {
                it.timeTravel(difference)
                timeMockedSchedulers.add(it)
            }
        }
        RxJavaPlugins.setIoSchedulerHandler {
            MockTimeSchedulerWrapper(it).also {
                it.timeTravel(difference)
                timeMockedSchedulers.add(it)
            }
        }
        RxAndroidPlugins.setMainThreadSchedulerHandler {
            MockTimeSchedulerWrapper(it).also {
                it.timeTravel(difference)
                timeMockedSchedulers.add(it)
            }
        }
    }

    fun timeTravel(date: Date) = setDifference(System.currentTimeMillis() - date.time)

    fun resetTimeTravel() = setDifference(0L)

    private fun setDifference(difference: Long) {
        this.difference = difference
        timeMockedSchedulers.forEach {
            it.timeTravel(difference)
        }
    }

    override fun finished(description: Description?) {
        super.finished(description)
        resetTimeTravel()
        RxJavaPlugins.setComputationSchedulerHandler(null)
        RxJavaPlugins.setIoSchedulerHandler(null)
        RxAndroidPlugins.setMainThreadSchedulerHandler(null)
    }
}

private class MockTimeSchedulerWrapper(private val wrapped: Scheduler) : Scheduler() {

    private var difference: Long = 0L
    fun timeTravel(difference: Long) {
        this.difference = difference
    }

    inner class MockTimeWorker(private val wrapped: Worker) : Worker() {
        override fun isDisposed(): Boolean = wrapped.isDisposed
        override fun dispose() = wrapped.dispose()
        override fun now(unit: TimeUnit): Long =
                unit.convert(wrapped.now(TimeUnit.MILLISECONDS) - difference, TimeUnit.MILLISECONDS)
        override fun schedule(run: Runnable, delay: Long, unit: TimeUnit): Disposable = wrapped.schedule(run, delay, unit)
        override fun schedule(run: Runnable): Disposable = wrapped.schedule(run)
        override fun schedulePeriodically(run: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): Disposable = wrapped.schedulePeriodically(run, initialDelay, period, unit)
    }

    override fun schedulePeriodicallyDirect(run: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): Disposable = wrapped.schedulePeriodicallyDirect(run, initialDelay, period, unit)
    override fun scheduleDirect(run: Runnable): Disposable = wrapped.scheduleDirect(run)
    override fun scheduleDirect(run: Runnable, delay: Long, unit: TimeUnit): Disposable = wrapped.scheduleDirect(run, delay, unit)
    override fun shutdown() = wrapped.shutdown()
    override fun start() = wrapped.start()
    override fun now(unit: TimeUnit): Long = unit.convert(wrapped.now(TimeUnit.MILLISECONDS) - difference, TimeUnit.MILLISECONDS)
    override fun <S> `when`(combine: Function<Flowable<Flowable<Completable>>, Completable>): S where S : Disposable?, S : Scheduler =
            wrapped.`when`(combine)
    override fun createWorker(): Worker = MockTimeWorker(wrapped.createWorker())
}

fun dateOf(text: String): Date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.GERMAN).parse(text)