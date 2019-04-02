# Kill the sleep in you android tests of a RxJava app

![A woman sleeps on edge of a rock](kill-the-sleep/cover.jpg)
Image from [https://unsplash.com/photos/BzIC8ioj7Ms](https://unsplash.com/photos/BzIC8ioj7Ms)

During development of android tests you written something like this:

```kotlin
@Test
fun afterStartActivity_verifyIfDataIsDisplayedFromTheServer() {
    activityRule.launchActivity()

    Thread.sleep(5000L) // 5 seconds

    onView(withId(R.id.main_activity_text))
            .check(matches(withText("data from the server")))
            .check(matches(isDisplayed()))
}
```

This seams reasonable because the app makes a request in the background, an you need to wait a few seconds before checking 
the result. I'll try to answer why the value is incorrect.


# TL;DR;

* [MainActivityBetterTest.kt](../examples/kill-the-sleep/src/androidTest/java/com/example/sleep/MainActivityBetterTest.kt)
* [RxIdlingResourcesRule.kt](../examples/kill-the-sleep/src/androidTest/java/com/example/sleep/rules/RxIdlingResourcesRule.kt)


# Reasons

We have `5000L` value, why? Why 5 seconds of delay?

## Slow tests

Because we hardcoded 5 seconds delay our test will take at least 5 seconds to accomplish. 
Maybe it's not a huge time but consider 100 tests with at least a three of such delays per test.
Quick math: `100 tests * 3 delays * 5seconds = 25 minutes`. 25 Minutes of your expensive time waiting for delays added to test code.
And usually you only need less then twice of that for you test to accomplish.

## Network is slow

Again, you hardcoded 5 seconds delay but your tests sometimes fails. 
It happens from time to time, but you see on your device's screen or on Firebase Test Lab's video that request to server was ongoing.
Yes, it happen from time to time that network or server is slower. 
During manual testing you definelty not mark this as bug if server will take a little longer to respond - this is completly normal situation.
If tests are flaky developers can't trust them. 
If they fail, we will say: "It's not a problem, probably network issue" but actually at the time new bug were introduced.
The solution looks pretty simple, just increase the time from 5 seconds to 10 seconds. 
I guess you know what will happen, your test suite will need 25 minutes more to accmplish.

## Temporary slowdown of the device

Even if you are not using network connection sleep time can vary between different executions. 
Your test device can do something in background during test execution, e.g. google account is syncing.
You don't want to fail your test if it's not your fault.

## Test on a different device

Let's imagine situation that you need to test your code if it works on a device model that you normally not running tests.
If the device is slower probably a lot of your sleeps will be to small for the new device model. You would need to increase sleep time again. 

## Code change

During developing of an app you probably introduce changes ;) 
What requirement has changed a little and you need to do additional request in background that doesn't 
directly impact your logic but may cause additional delay in tests.
You shouldn't need to change your tests if app works as expected.

# Solution

The espresso testing framework has a feature called [Idling resources](https://developer.android.com/training/testing/espresso/idling-resource). 
It allows instruct test case that something is happening so espresso should wait for the task to accomplish.
Sounds like solution for you problems - it is!

Because you use RxJava we can implement a rule so our code will change to:

```kotlin
@Rule @JvmField val rxIdlerRule = RxIdlingResourcesRule()

@Test
fun afterStartActivity_verifyIfDataIsDisplayedFromTheServer() {
    activityRule.launchActivity()

    // No Thread.sleep(3000) necessary, because we use RxIdlingResourcesRule

    onView(withId(R.id.main_activity_text))
            .check(matches(withText("data from the server")))
            .check(matches(isDisplayed()))
}
```

# Implementation
Espresso has a `CountingIdlingResource()` class that has two methods `countingIdlingResource.increment()` and `countingIdlingResource.decrement()`. 
You need to call `increment()` method after you start doing some network call and you need to call `decrement()` after you finish execution. 
RxJava has option to wrap `io()` and `computation()` schedulers by your own via `RxJavaPlugins.setComputationSchedulerHandler { }` and ` RxJavaPlugins.setIoSchedulerHandler { }` methods.
So now we need to look at the scheduler implementation and wrap it so that will notify `countingIdlingResource` about it work.

RxJava `Scheduler` is responsible for scheduling runnables so you can simply wrap original runnable with one that do `increment()` and `decrement()`:

```kotlin
class IdlingRunnable(private val countingIdlingResource: CountingIdlingResourc, private val runnable: Runnable) : Runnable {
    override fun run() {
        countingIdlingResource.increment()
        try {
            runnable.run()
        } finally {
            countingIdlingResource.decrement()
        }
    }
}
```

Than you need to wrap scheduler with the `IdlingRunnable`:

```kotlin
private class IdlingSchedulerWrapper(private val wrapped: Scheduler, private val countingIdlingResource: CountingIdlingResource) : Scheduler() {

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
```

Now we implement rule to inject your scheduler wrapper in RxJava and notify espresso about IdlingResource:

```kotlin
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
```

Now be happy and code your tests without sleeps:

```kotlin
@Rule @JvmField val rxIdlerRule = RxIdlingResourcesRule()

@Test
fun afterStartActivity_verifyIfDataIsDisplayedFromTheServer() {
    activityRule.launchActivity()

    // No Thread.sleep(3000) necessary, because we use RxIdlingResourcesRule

    onView(withId(R.id.main_activity_text))
            .check(matches(withText("data from the server")))
            .check(matches(isDisplayed()))
}
```

# Code

* [MainActivityBetterTest.kt](../examples/kill-the-sleep/src/androidTest/java/com/example/sleep/MainActivityBetterTest.kt)
* [RxIdlingResourcesRule.kt](../examples/kill-the-sleep/src/androidTest/java/com/example/sleep/rules/RxIdlingResourcesRule.kt)
* [RxIdlingResourcesRuleTest.kt](../examples/kill-the-sleep/src/androidTest/java/com/example/sleep/rules/RxIdlingResourcesRuleTest.kt)
* [MainActivity, Presenter and Service](../examples/kill-the-sleep/src/main/java/com/example/sleep/MainActivity.kt)
* [PresenterTest](../examples/kill-the-sleep/src/test/java/com/example/sleep/PresenterTest.kt)

# Summary

1. Don't use sleeps, the cause that test suite executes slower
2. Don't use sleeps, because your tests become flaky. 
3. Don't write flaky tests to trust your test suite.
4. Use `IdlingResource` so your code executes faster.
5. Use `RxIdlingResourcesRule` provided in the article.
6. If you use idling resources failure will happen only if there will be issue in the code.
7. You can customize timeout for idling resource `IdlingPolicies#setIdlingResourceTimeout(long timeout, TimeUnit unit)`
8. *Use [RxIdlingResourcesRule.kt](../examples/kill-the-sleep/src/androidTest/java/com/example/sleep/rules/RxIdlingResourcesRule.kt)*

# What's more
* [Solve your problems with time during Android Integration test](timetravel.md)
* [Using schedulers while testing your code](using-schedulers-while-testing-your-code.md)