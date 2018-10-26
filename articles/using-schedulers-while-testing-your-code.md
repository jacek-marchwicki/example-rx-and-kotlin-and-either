# Using schedulers while testing your code

## Let's start with an example

We would like to implement UI that sends a post to a server. Our UI needs a text field for typing a message and a button for sending it to the server. Of course, the UI displays error/success messages (via toast). We implement everything using MVC architecture and test the presenter.

At the starting point, there are two classes that represent responses from the API:
* `ApiError` - represents an error from the API,
* `Post` - represents a post from the server.

```kotlin
sealed class ApiError {
    object NoNetwork : ApiError()
}
data class Post(val message: String)
```

There is a kind of model that communicates with the server:

```kotlin
class PostsDao {
    /* As this is not real dao, we fake a response */
    fun sendToApi(post: Post): Single<Either<ApiError, Post>> = Single.just(Either.right(post) as Either<ApiError, Post>)
            .delay(1, TimeUnit.SECONDS, Schedulers.computation())
}
```

Of course, there is a UI:

```kotlin
class PostsActivity : AppCompatActivity() {
    val subscription = SerialSubscription()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.posts_activity)

        presenter = PostsPresenter(
                postsDao = PostsDao(),
                clickObservable = posts_activity_send_button.clicks(),
                messageObservable = posts_activity_message_text_view.textChanges())
        subscription.set(Subscription.from(
                presenter.showErrorText
                        .subscribe { posts_activity_send_button.showError(it.fold({ null }, { it.toString() })) },
                presenter.showSuccessToast
                        .subscribe { Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show() }
        ))
    }

    override fun onDestroy() {
        super.onDestroy()
        subscription.set(Subscriptions.empty())
    }
}
```


And the presenter:

```kotlin
class PostsPresenter(private val postsDao: PostsDao,
                         clickObservable: Observable<Unit>,
                         messageObservable: Observable<String>) {

        private val posToApi = clickObservable.withLatestFrom(messageObservable, { _, title -> Post(title)})
                .switchMap { postsDao.sendToApi(it).toObservable() }
                .replay(1)

        fun connect(): Subscription = posToApi.connect()

        val showErrorText: Observable<Option<ApiError>> = posToApi.map { it.left().toOption() }
        val showSuccessToast: Observable<Unit> = posToApi.filter { it.isRight() }.map { Unit }
    }
```

Now, finally, tests:

```kotlin
class PostTest {
    private var postsDao = mock<PostsDao> {
        on { sendToApi(any())} doReturn Single.just(Either.left(ApiError.NoNetwork) as Either<ApiError, Post>)
    }
    private var clickObservable = PublishSubject.create<Unit>()
    private var messageObservable = BehaviorSubject.create<String>()

    private fun createPresenter() = PostsPresenter(postsDao, clickObservable, messageObservable)

    @Test
    fun `when user type message and click, the request is sent with correct title`() {
        val presenter = createPresenter()
        presenter.connect()

        messageObservable.onNext("krowa")
        clickObservable.onNext(Unit)

        verify(postsDao).sendToApi(Post("krowa"))
    }

    @Test
    fun `if posting fails, show error`() {
        val errors = TestSubscriber<Option<ApiError>>()
        val presenter = createPresenter()
        presenter.showErrorText.subscribe(errors)
        presenter.connect()

        messageObservable.onNext("krowa")
        clickObservable.onNext(Unit)

        errors.assertReceivedOnNext(listOf(Option.Some(ApiError.NoNetwork)))
    }

    @Test
    fun `if posting success, show success toast`() {
        whenever(postsDao.sendToApi(any())) doReturn Single.just(Either.right(Post("x")) as Either<ApiError, Post>)
        val success = TestSubscriber<Unit>()
        val presenter = createPresenter()
        presenter.showSuccessToast.subscribe(success)
        presenter.connect()

        messageObservable.onNext("krowa")
        clickObservable.onNext(Unit)

        success.assertReceivedOnNext(listOf(Unit))
    }
}
```

But actually, the presenter has an issue with response returning on network thread instead of UI thread. This is not hard to fix.

```kotlin

    class PostsPresenter(/** unchanged **/
                         uiScheduler: Scheduler) {

        private val posToApi = clickObservable.withLatestFrom(messageObservable, { _, title -> Post(title)})
                .switchMap { 
                    postsDao.sendToApi(it).toObservable()
                    .observeOn(uiScheduler) 
                }
                .replay(1)

        /** unchanged **/
    }
```

and than update presenter with:

```kotlin
private fun createPresenter() = PostsPresenter(postsDao, clickObservable, messageObservable, Schedulers.immediate())
```

Pretty simple, wasn't it?

But now we would like to discard restless clicking on the button. So we use debounce operator to ignore clicks within 1 second:

```kotlin
 private val posToApi = clickObservable.debounce(1, TimeUnit.SECONDS, uiScheduler)
                .withLatestFrom(messageObservable, { _, title -> Post(title)})
                .switchMap { 
                    postsDao.sendToApi(it).toObservable()
                        .observeOn(uiScheduler)
                }
                .replay(1)
```

And hurray... everything works... but wait... did you see how much time it took for your tests to accomplish?

It was 66 ms before and now its 3 s? It might not be a problem for this small test suite but if you have one that contains 1,000 or 10,000 tests, your executions will take, respectively, 15 mins or 3 hrs longer.

So what’s the solution? The answer is `TestScheduler` (`Schedulers.test()`). Test scheduler allows manipulating time. We change our test’s initialization to:

```kotlin
private val uiScheduler = Schedulers.test()
private fun createPresenter() = PostsPresenter(postsDao, clickObservable, messageObservable, uiScheduler)
```

then we need to give instructions to the test, with how much time has passed:

```kotlin
@Test
fun `if posting fails, show error`() {
    val errors = TestSubscriber<ApiError>()
    val presenter = createPresenter()
    presenter.showErrorText.subscribe(errors)
    presenter.connect()

    messageObservable.onNext("krowa")
    clickObservable.onNext(Unit)
    uiScheduler.advanceTimeBy(1, TimeUnit.SECONDS)

    errors.assertReceivedOnNext(listOf(ApiError.NoNetwork))
}
```

Time machine, `uiScheduler.advanceTimeBy(1, TimeUnit.SECONDS)`, sends execution into the future one second away. And yes... it's possible to go even 1 year into the future ;)
However, usage of `TestScheduler` can be a little annoying. Let's remove debounce from our code:

```kotlin
private val posToApi = clickObservable
        .withLatestFrom(messageObservable, { _, title -> Post(title)})
        .switchMap {
            postsDao.sendToApi(it).toObservable()
                .observeOn(uiScheduler)
        }
        .replay(1)
```

and still use `TestScheduler` it in test’s initialization:

```kotlin
private val uiScheduler = Schedulers.test()
private fun createPresenter() = PostsPresenter(postsDao, clickObservable, messageObservable, uiScheduler)
```

Test code without `advanceTimeBy`:

```kotlin
@Test
fun `if posting fails, show error`() {
    val errors = TestSubscriber<ApiError>()
    val presenter = createPresenter()
    presenter.showErrorText.subscribe(errors)
    presenter.connect()

    messageObservable.onNext("krowa")
    clickObservable.onNext(Unit)

    errors.assertReceivedOnNext(listOf(ApiError.NoNetwork))
}
```

And now the test fails :/ What happened? If you switch back to `Schedulers.immediate()`, your test code will work. This is because scheduling (`.observeOn`, `.subscribeOn`) on `TestScheduler` needs to be triggered by `uiScheduler.advanceTimeBy()` or by `uiScheduler.triggerActions()`, so you need to change your test code to:

```kotlin
@Test
fun `if posting fails, show error`() {
    val errors = TestSubscriber<ApiError>()
    val presenter = createPresenter()
    presenter.showErrorText.subscribe(errors)
    presenter.connect()

    messageObservable.onNext("krowa")
    clickObservable.onNext(Unit)
    uiScheduler.triggerActions()

    errors.assertReceivedOnNext(listOf(ApiError.NoNetwork))
}
```

And this is ugly... Nothing more awful than thinking about threads when you test business logic. Wouldn’t it be cool to have a Scheduler that triggers actions instantly for instant subscriptions and, for time-delayed subscriptions, leaves triggering to me? I'll give you one that takes a minute to implement:

```kotlin
class ImmediateTestScheduler : TestScheduler() {
    inner class WrappingWorker(private val worker: Worker) : Worker() {
        override fun schedule(action: Action0?): Subscription = 
          worker.schedule(action)
            .also { triggerActions() }
        override fun schedule(action: Action0?, delayTime: Long, unit: TimeUnit?): Subscription = 
          worker.schedule(action, delayTime, unit)
            .also { triggerActions() }
        override fun schedulePeriodically(action: Action0?, initialDelay: Long, period: Long, unit: TimeUnit?): Subscription = 
          worker.schedulePeriodically(action, initialDelay, period, unit)
            .also { triggerActions() }
        override fun isUnsubscribed(): Boolean = worker.isUnsubscribed
        override fun now(): Long = worker.now()
        override fun unsubscribe() = worker.unsubscribe()
    }

    override fun createWorker(): Worker = WrappingWorker(super.createWorker())
}
```

**TIP**: Implementation for RxJava 2 you can find [here](../examples/schedulers/src/test/java/com/example/scheduler/InstantTestScheduler.java).

If you use this scheduler in your code:

```kotlin
private val uiScheduler = ImmediateTestScheduler()
private fun createPresenter() = PostsPresenter(postsDao, clickObservable, messageObservable, uiScheduler)
```

You will no longer need to invoke `uiScheduler.triggerActions()`, this will be done automatically.

## Conclusions

1. Don't care about threading in your tests if you don't want to check threading,
2. Don't use `Schedulers.immediate()` because your tests execution will be slow and not reliable,
3. Don't use `TestSubscriber.await*` methods because the test can be flaky if execution needs more time to complete,
4. Use `ImmediateTestScheduler()` in your tests for them to be fast and reliable.

## Full code
The full code for RxJava 2 you can find here:

* [InstantTestScheduler](../examples/schedulers/src/test/java/com/example/scheduler/InstantTestScheduler.java).
* [InstantTestSchedulerTest](../examples/schedulers/src/test/java/com/example/scheduler/InstantTestSchedulerTest.kt).
* [PostsPresenterTest](../examples/schedulers/src/test/java/com/example/scheduler/PostsPresenterTest.kt).
* [PostsPresenter](../examples/schedulers/src/main/java/com/example/scheduler/PostsPresenter.kt).


## Used libraries

```groovy
compile "org.funktionale:funktionale-either:1.0.1"
compile "io.reactivex:rxjava:1.3.0"

testCompile "junit:junit:4.11"
testCompile 'org.mockito:mockito-core:2.13.0'
testCompile "com.nhaarman:mockito-kotlin-kt1.1:1.5.0"
```

# Authors
* Jacek Marchwicki [jacek.marchwicki@gmail.com](mailto:jacek.marchwicki@gmail.com)