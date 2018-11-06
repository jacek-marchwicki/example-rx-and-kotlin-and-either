package com.example.timetravel.rules

import android.support.test.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.*

class LocaleTestRule(private val defaultLocale: Locale? = null, private val defaultTimeZone: TimeZone? = null) : TestWatcher() {
    lateinit var previousLocale: Locale
    lateinit var previousTimeZone: TimeZone


    override fun starting(description: Description?) {
        super.starting(description)

        previousLocale = Locale.getDefault()
        previousTimeZone = TimeZone.getDefault()
        defaultLocale?.let { setLocaleForTest(it) }
        defaultTimeZone?.let { setTimeZone(it) }
    }

    override fun finished(description: Description?) {
        super.finished(description)

        setLocaleForTest(previousLocale)
        TimeZone.setDefault(previousTimeZone)
    }


    @Suppress("DEPRECATION")
    private fun setLocaleForTest(locale: Locale) {
        val resources = InstrumentationRegistry.getTargetContext().resources
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun setLocale(locale: Locale?) = setLocaleForTest(locale ?: previousLocale)
    fun setTimeZone(timeZone: TimeZone?) = TimeZone.setDefault(timeZone ?: previousTimeZone)
}