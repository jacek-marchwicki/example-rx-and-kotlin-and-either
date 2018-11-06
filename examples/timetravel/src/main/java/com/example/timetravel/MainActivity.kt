package com.example.timetravel

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposables
import io.reactivex.disposables.SerialDisposable
import kotlinx.android.synthetic.main.main_activity.*
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class Presenter(uiScheduler: Scheduler, locale: Locale, timeZone: TimeZone) {
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale)
            .also { it.timeZone = timeZone }
    val time: Observable<String> = Observable.interval(1L, TimeUnit.SECONDS, uiScheduler)
            .startWith(0L)
            .map { Date(uiScheduler.now(TimeUnit.MILLISECONDS)) }
            .map { timeFormat.format(it) }
}

class MainActivity : AppCompatActivity() {

    private val serialDisposable = SerialDisposable()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val presenter = Presenter(AndroidSchedulers.mainThread(), Locale.getDefault(), TimeZone.getDefault())

        serialDisposable.set(presenter.time.subscribe(main_activity_time::setText))
    }

    override fun onDestroy() {
        serialDisposable.set(Disposables.empty())
        super.onDestroy()
    }
}
