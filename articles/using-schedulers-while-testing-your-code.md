# Using schedulers while testing your code

## Let's start with some example

We would like to implement UI that will send some post to API. Our UI will need a button that will send a post, and a text field called "message" so a user will be able to choose a message that will be posted to the server. Of course, our UI can display errors and will show success (via toast).
We will implement everything via MVC and will test presenter.

At a starting point we will have two classes that will be returned from API:
* `ApiError` - it's used to represent some errors from API,
* `Post` that represent post that needs to be added to the server.

```kt
sealed class ApiError {
    object NoNetwork : ApiError()
}
data class Post(val message: String)
```

We will have some kind of Dao that will contact server:

```kt
class PostsDao {
    /* This is not real dao so we fake some response */
    fun sendToApi(post: Post): Single<Either<ApiError, Post>> = Single.just(Either.right(post) as Either<ApiError, Post>)
            .delay(1, TimeUnit.SECONDS, Schedulers.computation())
}
```

Of course, we will have some UI:

```kt
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


And presenter:

```kt
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

So we will add tests:

```kt
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
    fun `if posting fail, show error`() {
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

But actually, we have an issue in the presenter, because we don't change thread that is returned by the presenter. This is not hard to fix.

```kt

    class PostsPresenter(private val postsDao: PostsDao,
                         clickObservable: Observable<Unit>,
                         messageObservable: Observable<String>,
                         uiScheduler: Scheduler) {

        private val posToApi = clickObservable.withLatestFrom(messageObservable, { _, title -> Post(title)})
                .switchMap { postsDao.sendToApi(it).toObservable()
                  .observeOn(uiScheduler) }
                .replay(1)

        fun connect(): Subscription = posToApi.connect()

        val showErrorText: Observable<ApiError?> = posToApi.map { it.fold({ it }, { null }) }
        val showSuccessToast: Observable<Unit> = posToApi.filter { it.isRight() }.map { Unit }
    }
```

and than update presenter with:

```kt
private fun createPresenter() = PostsPresenter(postsDao, clickObservable, messageObservable, Schedulers.immediate())
```

pretty simple wasn't it?

but now we want to debounce users click on send on 1second, so we update our presenter:

```kt
 private val posToApi = clickObservable.debounce(1, TimeUnit.SECONDS, uiScheduler)
                .withLatestFrom(messageObservable, { _, title -> Post(title)})
                .switchMap { postsDao.sendToApi(it).toObservable()
                        .observeOn(uiScheduler)}
                .replay(1)
```

And hurray.. everything works... but wait... did you observed time that took for your tests to accomplish?

It was 66ms before and now its 3s and 66ms? Maybe this is not a problem for this small test suite. But if you will have a test suite that contains 1000 or 10000 tests this can become 15minutes/3hours longer for your build to finish.

So what is a solution? The answer is `TestScheduler` (Schedulers.test()). Test scheduler allows manimpulating time. So we will change our test initialization to:

```kt
private val uiScheduler = Schedulers.test()
private fun createPresenter() = PostsPresenter(postsDao, clickObservable, messageObservable, uiScheduler)
```

then we need to give instructions to the test, with how much time has passed:

```kt
@Test
fun `if posting fail, show error`() {
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

Using `uiScheduler.advanceTimeBy(1, TimeUnit.SECONDS)` we virtually move into the future by one second. And yes... it's possible to move even 1 year into the future ;)

But TestScheduler can be a little annoying in some situations. Lest's remove debounce from our code:

```kt
private val posToApi = clickObservable
        .withLatestFrom(messageObservable, { _, title -> Post(title)})
        .switchMap { postsDao.sendToApi(it).toObservable()
                .observeOn(uiScheduler)}
        .replay(1)
```

and still use TestScheduler it test initialization:

```kt
private val uiScheduler = Schedulers.test()
private fun createPresenter() = PostsPresenter(postsDao, clickObservable, messageObservable, uiScheduler)
```

and test code without `advanceTimeBy`:

```kt
@Test
fun `if posting fail, show error`() {
    val errors = TestSubscriber<ApiError>()
    val presenter = createPresenter()
    presenter.showErrorText.subscribe(errors)
    presenter.connect()

    messageObservable.onNext("krowa")
    clickObservable.onNext(Unit)

    errors.assertReceivedOnNext(listOf(ApiError.NoNetwork))
}
```

And now test will fail :/ What happened? If you'll switch to `Schedulers.immediate()` your test code will work. This is because scheduling (`.observeOn`, `.subscribeOn`) via TestScheduler need to be invoked by `uiScheduler.advanceTimeBy()` or by `uiScheduler.triggerActions()`, so you need to change your test code to:


```kt
@Test
fun `if posting fail, show error`() {
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

And this is ugly... It's definitely ugly if you need to think about threads in your tests when you don't want to. Isn't be cool to have a Scheduler that will work instantly for instant schedules and manually for time-delayed schedules? I'll give you one that took minutes to implement:

```kt
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

If you will use this scheduler in your code:
```kt
private val uiScheduler = Schedulers.test()
private fun createPresenter() = PostsPresenter(postsDao, clickObservable, messageObservable, uiScheduler)
```

You will no longer need to invoke `uiScheduler.triggerActions()` this will be done automatically.


## Conclusions

1. Don't care about threading changes in your tests if you don't want to check threading,
2. Don't use Schedulers.immediate() so your tests will be quicker and more reliable,
3. Don't use TestSubscriber.await* methods because they can be flaky if tests will take more time to finish,
4. Use ImmediateTestScheduler() in your tests - so they will be fast and reliable.


## Used libraries

```groovy
compile "org.funktionale:funktionale-either:1.0.1"
compile "io.reactivex:rxjava:1.3.0"

testCompile "junit:junit:4.11"
testCompile 'org.mockito:mockito-core:2.13.0'
testCompile "com.nhaarman:mockito-kotlin-kt1.1:1.5.0"
```

# Authors
Authors:
* Jacek Marchwicki