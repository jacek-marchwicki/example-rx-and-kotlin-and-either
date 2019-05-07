# No sleeping during testing RxJava app!

![A woman sleeps on edge of a rock](kill-the-sleep/cover.jpg)
Image from [https://unsplash.com/photos/BzIC8ioj7Ms](https://unsplash.com/photos/BzIC8ioj7Ms)

During development of Android tests you’ve written something like this:

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

This seems reasonable because after starting, the app makes a request in the background so you need to wait a few seconds before checking whether the result is displayed on the screen. 
I'll try to explain why it’s wrong to have sleep in test code.


# TL;DR;

* [MainActivityBetterTest.kt](../examples/kill-the-sleep/src/androidTest/java/com/example/sleep/MainActivityBetterTest.kt)
* [RxIdlingResourcesRule.kt](../examples/kill-the-sleep/src/androidTest/java/com/example/sleep/rules/RxIdlingResourcesRule.kt)


# Reasons

We have `5000L` value, why? Why 5 seconds of delay?

## Slow tests

Because we hardcoded a 5-second delay, our test will take at least these 5 seconds to complete. 
Maybe it's not a huge amount of time but consider 100 tests with at least three delays per test. 
Quick math: `100 tests * 3 delays * 5 seconds = 25 minutes`. 25 minutes of your pricey time wasted on sitting through delays. 
Usually, you only need less than half of that time for your test to complete.

## Slow network

Again, you hardcoded a 5-second delay but your tests sometimes fail. 
Firebase Test Lab shows failures from time to time. 
After debugging the issue you realize that sometimes, after the delay, the request to a server still goes on so your assertion fails during the request result check. 
Yes, network and server sometimes happen to be slower. 
During manual testing you definitely don’t mark this as a bug if the server takes a little longer to respond - this is completely normal. When a test fails, developers say: "It's not a problem, it’s probably a network issue". 
They’ll ignore the result of such a test even if it rightly finds an issue in the app. 
Developers can't trust flaky tests. The solution looks pretty simple, just increase the delay time from 5 to 10 seconds. 
I guess you know what will happen: your test suite will need 25 minutes more to complete.

## Temporary slowdown of the device

Even if you aren’t using network connection, fake request time may vary between different executions. 
During test execution, your device can do some work in the background, e.g., syncing Google account. 
You don't want to fail your test because of that.


## Test on a different device

Let's imagine a twist: you need to test your app on a device model that you normally don’t run tests on. 
If the device’s slower, a lot of your sleeps might be too short for the new device model. 
You need to increase sleep time again.

## Code change

During app development you probably introduce changes ;) 
What if a requirement has changed a little and you need to do an additional request that doesn't directly impact your business logic but may cause additional delay in tests? 
You shouldn't need to change your tests if the app works as expected.

# Solution

The Espresso Testing Framework has a feature called [Idling resources](https://developer.android.com/training/testing/espresso/idling-resource). 
It allows providing for things happening in the background making Espresso wait for  tasks to accomplish. 
Sounds like the solution for your problems? - it is!

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
Espresso contains a `CountingIdlingResource()` class that has two methods `countingIdlingResource.increment()` and `countingIdlingResource.decrement()`. 
You need to call `increment()` method before you start doing a network request and you need to call `decrement()` after you finish execution. 
In RxJava you can wrap `io()` and `computation()` schedulers via `RxJavaPlugins.setComputationSchedulerHandler { }` and ` RxJavaPlugins.setIoSchedulerHandler { }` methods.
So now we need to look at the scheduler implementation and wrap it so that it will notify `countingIdlingResource` about scheduled work.

RxJava `Scheduler` is responsible for scheduling runnables so you can simply wrap the original runnable with one that calls `increment()` and `decrement()`:

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

Then you need to wrap your scheduler so that  it’d use `IdlingRunnable`:

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

Now we implement a rule to inject `IdlingSchedulerWrapper` to RxJava and notify Espresso about `IdlingResource`:

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

1. Don't use sleeps as they slow test suite executions down.
2. Don't use sleeps as they make your test flaky. 
3. If you want a trustworthy test suite, don’t write flaky tests.
4. Use Espresso's `IdlingResource`.
5. *Use [RxIdlingResourcesRule.kt](../examples/kill-the-sleep/src/androidTest/java/com/example/sleep/rules/RxIdlingResourcesRule.kt) provided in the article*.
6. TIP: You can customize timeout for idling resource `IdlingPolicies#setIdlingResourceTimeout(long timeout, TimeUnit unit)`.

# What's more
* [Solve your problems with time during Android Integration test](timetravel.md)
* [Using schedulers while testing your code](using-schedulers-while-testing-your-code.md)
