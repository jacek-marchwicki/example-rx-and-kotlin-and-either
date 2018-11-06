package com.example.timetravel

import android.app.Activity
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.ViewMatchers.isDisplayed
import android.support.test.espresso.matcher.ViewMatchers.withId
import android.support.test.espresso.matcher.ViewMatchers.withText
import android.support.test.filters.MediumTest
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import com.example.timetravel.rules.LocaleTestRule
import com.example.timetravel.rules.TimeTravelRule
import com.example.timetravel.rules.dateOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


@RunWith(AndroidJUnit4::class)
@MediumTest
class MainActivityTest {

    @Rule @JvmField val localeTestRule = LocaleTestRule(Locale.US, TimeZone.getTimeZone("UTC"))
    @Rule @JvmField val activityRule = activityTestRule<MainActivity>()
    @Rule @JvmField val timeTravelRule = TimeTravelRule()

    @Test
    fun afterStartActivity_timeItemIsDisplayed() {
        activityRule.launchActivity()

        onView(withId(R.id.main_activity_time))
                .check(matches(isDisplayed()))
    }

    @Test
    fun testThatYouShouldNotWrite() {
        val expected = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date())

        activityRule.launchActivity()

        onView(withId(R.id.main_activity_time))
                .check(matches(withText(expected)))
    }

    @Test
    fun whenTimeIsMocked_displayCorrectTime() {
        timeTravelRule.timeTravel(dateOf("2005-01-01T13:00:00+0000"))

        activityRule.launchActivity()

        onView(withId(R.id.main_activity_time))
                .check(matches(withText("1:00 PM")))
    }

    @Test
    fun whenTimeIsMockedInGermany_displayCorrectTime() {
        localeTestRule.setLocale(Locale.GERMANY)
        timeTravelRule.timeTravel(dateOf("2005-01-01T13:00:00+0000"))

        activityRule.launchActivity()

        onView(withId(R.id.main_activity_time))
                .check(matches(withText("13:00")))
    }

    @Test
    fun whenTimeIsMockedInUs_displayCorrectTime() {
        localeTestRule.setLocale(Locale.US)
        timeTravelRule.timeTravel(dateOf("2005-01-01T13:00:00+0000"))

        activityRule.launchActivity()

        onView(withId(R.id.main_activity_time))
                .check(matches(withText("1:00 PM")))
    }
}


inline fun <reified T : Activity> activityTestRule(): ActivityTestRule<T> = ActivityTestRule(T::class.java, false, false)
fun <T : Activity> ActivityTestRule<T>.launchActivity(): T = this.launchActivity(null)