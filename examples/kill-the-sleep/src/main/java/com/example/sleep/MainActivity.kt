package com.example.sleep

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposables
import io.reactivex.disposables.SerialDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.main_activity.*
import java.util.*

data class DataFromServer(val text: String)
interface Service {
    fun getDataFromServer(): Single<DataFromServer>
}

class ServiceFake : Service {
    override fun getDataFromServer(): Single<DataFromServer> =
            Single.fromCallable {
                // fake the API response delay
                Thread.sleep((2000L..5000L).random())

                // fake the API response data
                DataFromServer("data from the server")
            }
}

class Presenter(service: Service, computationScheduler: Scheduler, uiScheduler: Scheduler) {
    val text: Observable<String> = service.getDataFromServer()
            .map { it.text }
            .onErrorReturn { "Error" }
            .subscribeOn(computationScheduler)
            .observeOn(uiScheduler)
            .toObservable()
            .startWith("")
}

class MainActivity : AppCompatActivity() {

    private val serialDisposable = SerialDisposable()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val presenter = Presenter(ServiceFake(), Schedulers.io(), AndroidSchedulers.mainThread())

        serialDisposable.set(presenter.text.subscribe(main_activity_text::setText))
    }

    override fun onDestroy() {
        serialDisposable.set(Disposables.empty())
        super.onDestroy()
    }
}

fun LongRange.random(): Long = (Random().nextLong() % ((endInclusive + 1L) - start)) +  start