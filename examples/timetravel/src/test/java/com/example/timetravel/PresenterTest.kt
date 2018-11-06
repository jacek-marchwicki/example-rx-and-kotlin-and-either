package com.example.timetravel

import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.TestScheduler
import org.junit.Test
import java.lang.AssertionError
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class PresenterTest {

    private val uiScheduler = TestScheduler()
    private fun create(locale: Locale = Locale.GERMANY) = Presenter(uiScheduler, locale, TimeZone.getTimeZone("UTC"))

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.GERMAN)

    private fun toMillis(date: String):Long = dateFormat.parse(date).time

    @Test
    fun `when time is 1300, show it as 1300 in germany`() {
        uiScheduler.advanceTimeTo(toMillis("2005-01-01T13:00:00+0000"), TimeUnit.MILLISECONDS)

        create(Locale.GERMANY).time.test().assertValue("13:00")
    }

    @Test
    fun `when time is 1300, show it as 100 PM in US`() {
        uiScheduler.advanceTimeTo(toMillis("2005-01-01T13:00:00+0000"), TimeUnit.MILLISECONDS)

        create(Locale.US).time.test().assertValue("1:00 PM")
    }

    @Test
    fun `when time move, update time`() {
        uiScheduler.advanceTimeTo(toMillis("2005-01-01T13:00:00+0000"), TimeUnit.MILLISECONDS)

        val timeText = create(Locale.GERMANY).time.test()
        timeText.assertLasValue("13:00")

        uiScheduler.advanceTimeTo(toMillis("2005-01-01T13:01:00+0000"), TimeUnit.MILLISECONDS)
        timeText.assertLasValue("13:01")
    }
}

private fun <T> TestObserver<T>.assertLasValue(value: T): TestObserver<T> {
    if (valueCount() < 1) throw AssertionError("Expected at least one element")
    return assertValueAt(valueCount() - 1, value)
}

