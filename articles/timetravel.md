# Solve your problems with time during Android Integration test

![Spinning top](timetravel/cover.jpg)
Image from https://unsplash.com/photos/iiRQxPCDQ_Y webpage.

When you develop a real-life app with integrations tests you probably faced a problem that time is moving :)

It doesn't matter if you develop an alarm clock or a feed app that is less related to time, I'm almost 100% sure that app is more or less time-related.

If parts of the app are time-based it might be tricky to maintain consistent tests results.

During Unit Tests, it's a usually good pattern to use `TestScheduler` or even solution that I proposed in ["Using schedulers while testing your code" article](using-schedulers-while-testing-your-code.md), 
but in integrations tests, you need something more real-life.

# TL;DR;

You can simply fake/adjust current time and locales via [TimeTravelRule](../examples/timetravel/src/androidTest/java/com/example/timetravel/rules/TimeTravelRule.kt).
, [LocaleTestRule](../examples/timetravel/src/androidTest/java/com/example/timetravel/rules/LocaleTestRule.kt) rules.
For an usage look here [MainActivityTest](../examples/timetravel/src/androidTest/java/com/example/timetravel/MainActivityTest.kt).

# Example

Let's implement very simple UI that displays the current time as a text view.

In `Activity#onCreate()` we have only:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    setContentView(R.layout.main_activity)
    val presenter = Presenter(AndroidSchedulers.mainThread(), Locale.getDefault(), TimeZone.getDefault())
    presenter.time.subscribe(main_activity_time::setText)
}
```

And our presenter is also very simple:

```kotlin
class Presenter(uiScheduler: Scheduler, locale: Locale, timeZone: TimeZone) {
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale)
            .also { it.timeZone = timeZone }
    val time: Observable<String> = Observable.interval(1L, TimeUnit.SECONDS, uiScheduler)
            .startWith(0L)
            .map { Date(uiScheduler.now(TimeUnit.MILLISECONDS)) }
            .map { timeFormat.format(it) }
}
```

Unit Test for this class is very simple, so I'll skip describing implementation details of those. 
You can look at [PresenterTest.kt](../examples/timetravel/src/test/java/com/example/timetravel/PresenterTest.kt) class.

# Let's implement our first UI test

The first test starts the activity and checks if time label is displayed.

```kotlin
@RunWith(AndroidJUnit4::class)
@MediumTest
class MainActivityTest {
@Rule @JvmField val activityRule = activityTestRule<MainActivity>()

    @Test
    fun afterStartActivity_timeItemIsDisplayed() {
        activityRule.launchActivity()
    
        onView(withId(R.id.main_activity_time))
                .check(matches(isDisplayed()))
    }
}
```

But the test does not check business logic. It doesn't check the correctness of the label's value.

# Real-life test

Now you would like to test if a current time is displayed correctly... But guess what.. You don't know what time is now.. and how it should be displayed.

You could write a test like this:

```kotlin
val expected = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date())

activityRule.launchActivity()

onView(withId(R.id.main_activity_time))
        .check(matches(withText(expected)))
```

But the test actually doesn't test's anything ;) It uses the same logic as it is used inside the application.
So if the logic is broken your test will silently pass broken code.

You could mock the presenter. This is perfectly fine, but your test will become a UI test instead of an integration test. 
We want integration test. 

# Let's face the problem

## Problem 1 (time)

The primary problem is that tests execute at the random time, so how to verify if the correct value is displayed. 

### Idea
So it would be good to write a JUnit Rule that in some tests time will be faked. Something like this:

```kotlin
@Rule @JvmField val timeTravelRule = TimeTravelRule()
```

Than we could write following test:

```kotlin
timeTravelRule.timeTravel(dateOf("2005-01-01T13:00:00+0000"))

activityRule.launchActivity()

onView(withId(R.id.main_activity_time))
        .check(matches(withText("1:00 PM")))
```

### Defining the problem

If we have the basics of our ideal solution, we should consider how exactly our solution should work.
1. We want to fake time in tests.
2. We want to use a rule so we can reuse it in some tests and skip in others.
3. We don't want to freeze time, so the app during tests behave almost exactly as in production. Time needs to flow.
4. We use RxJava schedulers as a source of the time, so we should fake those.
5. We would like to modify application behavior as little as possible during the testing phase so we check real-life scenarios.

### Choosing a solution

We arg going to implement `TimeTravelRule` with `timeTravel(Date)` method (pt 1 and 2 passed).
Because scheduler has `Scheduler#now(TimeUnit)` method (pt 4. passed).
We can only subtract some value of the current time, to calculate faked one, so time in tests will flow from a certain moment in time that you defined in test `override fun now(unit: TimeUnit): Long = wrappedScheduler.now(unit) - mockedDifference` (pt. 3 passed).
We can simply wrap our original scheduler to adjust time and inject it via `RxJavaPlugins.setXyzSchedulerHandler`method (pt 5. passed).

### Implementing the solution

Let's write the wrapper for a scheduler:

```kotlin
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
```

Now we inject our wrappers:

```kotlin
val difference = System.currentTimeMillis() - dateOf("2005-01-01T13:00:00+0000")
RxJavaPlugins.setComputationSchedulerHandler {
    MockTimeSchedulerWrapper(it).also {
        it.timeTravel(difference)
    }
}
RxJavaPlugins.setIoSchedulerHandler {
    MockTimeSchedulerWrapper(it).also {
        it.timeTravel(difference)
    }
}
RxAndroidPlugins.setMainThreadSchedulerHandler {
    MockTimeSchedulerWrapper(it).also {
        it.timeTravel(difference)
    }
}
```

After we checked our mocking mechanism works, we merge our code in to the rule: [TimeTravelRule](../examples/timetravel/src/androidTest/java/com/example/timetravel/rules/TimeTravelRule.kt).
Now we reuse the code we defined in `Idea` subtitle:

```kotlin
timeTravelRule.timeTravel(dateOf("2005-01-01T13:00:00+0000"))

activityRule.launchActivity()

onView(withId(R.id.main_activity_time))
        .check(matches(withText("1:00 PM")))
```

## Problem 2 (locales)

The second problem is that when running the test on different devices the test execution may fail. 
The failure can be caused because a device can be set to different locales or different timezone.
In our example, `13:00` should be displayed as `1:00 PM` in US but as `13:00` in Germany.
Moreover, `13:00` in UTC isn't the same as `13:00` in `PDT` or `GMT`.
Of course, we can ensure the device is correctly set before executing tests, but this tedious and can lead to false negatives.
But what if we want to test booth behaviors at the same test execution?

Wouldn't be nice to use a rule to define locales?

### Idea

```kotlin
@Rule @JvmField val localeTestRule = LocaleTestRule(Locale.US, TimeZone.getTimeZone("UTC"))
```

You can write [LocaleTestRule](../examples/timetravel/src/androidTest/java/com/example/timetravel/rules/LocaleTestRule.kt).

Then simply use it in your tests via the same test:
```kotlin

@Test
fun whenTimeIsMocked_displayCorrectTime() {
    timeTravelRule.timeTravel(dateOf("2005-01-01T13:00:00+0000"))

    activityRule.launchActivity()

    onView(withId(R.id.main_activity_time))
            .check(matches(withText("1:00 PM")))
}
```

Or as I said change locales during test:

```kotlin
@Test
fun whenTimeIsMockedInGermany_displayCorrectTime() {
    localeTestRule.setLocale(Locale.GERMANY)
    timeTravelRule.timeTravel(dateOf("2005-01-01T13:00:00+0000"))

    activityRule.launchActivity()

    onView(withId(R.id.main_activity_time))
            .check(matches(withText("13:00")))
} 
```

# Conclusions

1. Testing might be tricky but with simple tricks (JUnit Rules) it might become very simple.
2. Use rules to simplify your testing code, readable tests code is very helpful when that test fails because of an issue.
3. Be sure your tests work consistent between executions and environments. If your tests are flaky and you can't trust them, they are useless.