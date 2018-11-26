package com.example.sleep

import android.app.Activity
import android.support.test.rule.ActivityTestRule


inline fun <reified T : Activity> activityTestRule(): ActivityTestRule<T> = ActivityTestRule(T::class.java, false, false)
fun <T : Activity> ActivityTestRule<T>.launchActivity(): T = this.launchActivity(null)