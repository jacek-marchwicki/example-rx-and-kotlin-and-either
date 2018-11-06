package com.example.timetravel.rules

import android.support.test.filters.SmallTest
import android.support.test.runner.AndroidJUnit4
import io.reactivex.schedulers.Schedulers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@SmallTest
@RunWith(AndroidJUnit4::class)
class TimeTravelRuleTest {
    @Rule
    @JvmField val rule = TimeTravelRule()

    @Test
    fun whenMockingTime_timeIsCorrectlyUpdated() {
        rule.timeTravel(dateOf("2005-01-01T12:00:00+0000"))

        val mocked = Schedulers.io().now(TimeUnit.MILLISECONDS)

        assertTrue(mocked <= dateOf("2005-01-01T13:00:00+0000").time)
        assertTrue(mocked >= dateOf("2005-01-01T11:00:00+0000").time)
    }
}