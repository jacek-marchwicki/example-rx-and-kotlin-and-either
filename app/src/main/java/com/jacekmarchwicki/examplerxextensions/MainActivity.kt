package com.jacekmarchwicki.examplerxextensions

import android.app.Application
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SimpleItemAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.jacekmarchwicki.universaladapter.BaseAdapterItem
import com.jacekmarchwicki.universaladapter.UniversalAdapter
import com.jacekmarchwicki.universaladapter.ViewHolderManager
import com.jakewharton.rxbinding.view.clicks
import com.jakewharton.rxbinding.widget.textChanges
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_activity_post_item.view.*
import org.funktionale.either.Either
import org.funktionale.option.Option
import org.funktionale.option.getOrElse
import org.funktionale.tries.Try
import rx.Observable
import rx.Scheduler
import rx.Single
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.observables.ConnectableObservable
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import rx.subscriptions.SerialSubscription
import rx.subscriptions.Subscriptions
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class MainApplication : Application() {
    val postsDao: PostsDaos by lazy {PostsDaos(Schedulers.io(), PostsService())}
    val authorizationDao: AuthorizationDao by lazy {LoginDao()}
}

class MainActivity : AppCompatActivity() {

    private val serialSubscription = SerialSubscription()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val itemsAdapter = UniversalAdapter(listOf(PostsHolderManager()))

        main_activity_recycler.apply {
            layoutManager = LinearLayoutManager(context).apply {
                recycleChildrenOnDetach = true
                (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            }
            adapter = itemsAdapter
        }


        val presenter = (application as MainApplication)
                .let {
                    MainPresenter(it.postsDao, it.authorizationDao, AndroidSchedulers.mainThread(),
                            main_activity_edit_text.textChanges().map { it.toString() },
                            main_activity_button.clicks())
                }

        serialSubscription.set(Subscriptions.from(
                presenter.showSendSuccessToast.subscribe {
                    Toast.makeText(this, "Sent", Toast.LENGTH_SHORT).show()
                },
                presenter.showErrorToast.subscribe {
                    it.forEach { Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show() }
                },
                presenter.buttonEnabled.subscribe(main_activity_button::setEnabled),
                presenter.error.subscribe {
                    main_activity_error.text = it.map { it.toString() }.getOrElse { "" }
                    main_activity_error.visibility = it.fold({ View.GONE}, { View.VISIBLE})
                },
                presenter.textClear.subscribe { main_activity_edit_text.setText("") },
                presenter.adapterItems.subscribe(itemsAdapter::call),
                presenter.connect()
        ))
    }

    override fun onDestroy() {
        serialSubscription.set(Subscriptions.empty())
        super.onDestroy()
    }
}

class PostsHolderManager : ViewHolderManager {
    override fun createViewHolder(parent: ViewGroup, inflater: LayoutInflater): ViewHolderManager.BaseViewHolder<*> = Holder(inflater.inflate(R.layout.main_activity_post_item, parent, false))

    override fun matches(baseAdapterItem: BaseAdapterItem): Boolean = baseAdapterItem is MainPresenter.AdapterItem.PostItem

    class Holder(view: View) : ViewHolderManager.BaseViewHolder<MainPresenter.AdapterItem.PostItem>(view) {
        override fun bind(item: MainPresenter.AdapterItem.PostItem) {
            itemView.main_activity_post_item_text.text = item.text
        }
    }

}

class MainPresenter(postsDaos: PostsDaos,
                    authorizationDao: AuthorizationDao,
                    uiScheduler: Scheduler,
                    title: Observable<String>,
                    sendClickObservable: Observable<Unit>) {

    private val posts: Observable<Either<DefaultError, List<Post>>> = authorizationDao.authorizedDaoObservable
            .switchMap { it.fold({ Observable.just(Either.left(it) as Either<DefaultError, List<Post>>) }, { postsDaos.getDao(it).posts }) }
            .observeOn(uiScheduler)
            .startWith(Either.left(NotYetLoadedError))
            .replay(1)
            .refCount()

    val adapterItems: Observable<List<AdapterItem>> = posts.map {
        it.right()
                .toOption()
                .map { it.map { AdapterItem.PostItem(it.id, it.title) as AdapterItem } }.
                getOrElse { listOf() }
    }
    val error: Observable<Option<DefaultError>> = posts.map { it.left().toOption() }

    private val sendObservable: ConnectableObservable<Either<DefaultError, Unit>> =
            sendClickObservable.withLatestFrom(title, { _, title -> title })
                    .switchMap { title ->
                        authorizationDao.authorizedDaoObservable.first().toSingle()
                                .flatMap {
                                    it.fold({ Single.just(Either.left(it) as Either<DefaultError, Unit>) }, {
                                        postsDaos.getDao(it).createPost(Post(UUID.randomUUID().toString(), title))
                                    })
                                }
                                .toObservable()
                                .observeOn(uiScheduler)
                                .startWith(Either.left(NotYetLoadedError))
                    }
                    .publish()

    fun connect(): Subscription = sendObservable.connect()
    val showSendSuccessToast: Observable<Unit> = sendObservable.flatMap { it.fold({ Observable.empty<Unit>() }, { Observable.just(Unit) }) }

    val showErrorToast: Observable<Option<DefaultError>> = sendObservable.map { it.left().toOption().flatMap { if (it is NotYetLoadedError) Option.None else Option.Some(it) } }
    val buttonEnabled: Observable<Boolean> = sendObservable.map { it.fold({it !is NotYetLoadedError}, {true}) }.startWith(true)
    val textClear: Observable<Unit> = sendObservable.flatMap { it.fold({ Observable.empty<Unit>() }, { Observable.just(Unit) }) }

    sealed class AdapterItem : BaseAdapterItem {
        data class PostItem(val id: String, val text: String): AdapterItem() {
            override fun adapterId(): Long = id.hashCode().toLong()
            override fun matches(item: BaseAdapterItem): Boolean = (this as? PostItem)?.id == id
        }

        override fun same(item: BaseAdapterItem): Boolean = this == item

    }
}
interface DefaultError
object NotYetLoadedError : DefaultError

data class Post(val id: String, val title: String)

/* Parameters will be used in real implementation */ @Suppress("UNUSED_PARAMETER")
class PostsService {
    fun getPosts(authorization: String): Single<List<Post>> = Single.just(listOf(Post("${authorization}id1", "test")))
            .delay(2, TimeUnit.SECONDS)

    fun addPost(authorization: String, post: Post): Single<Unit> = Single.just(Unit).delay(2, TimeUnit.SECONDS)
}

class PostsDaos(val networkScheduler: Scheduler,
                val service: PostsService) {
    private val cache = HashMap<AuthorizedDao, PostsDao>()
    fun getDao(key: AuthorizedDao): PostsDao = cache.getOrPut(key, { PostsDao(key) })

    inner class PostsDao(private val authorizedDao: AuthorizedDao) {
        private val refreshSubject = PublishSubject.create<Unit>()

        /**
         * Only used as example how to implement without refreshing
         */
        val postsSimple: Observable<Either<DefaultError, List<Post>>> =
                authorizedDao.callWithAuthTokenSingle { authorization ->
                    service.getPosts(authorization)
                            .subscribeOn(networkScheduler)
                            .handleApiErrors()
                }
                .toObservable()
                .cache()

        val posts: Observable<Either<DefaultError, List<Post>>> =
                authorizedDao.callWithAuthTokenSingle { authorization ->
                    service.getPosts(authorization)
                            .subscribeOn(networkScheduler)
                            .handleApiErrors()
                }
                        .refreshWhen(refreshSubject)
                        .replay(1)
                        .refCount()

        fun createPost(post: Post): Single<Either<DefaultError, Unit>> =
                authorizedDao.callWithAuthTokenSingle { authorization ->
                    service.addPost(authorization, post)
                            .subscribeOn(networkScheduler)
                            .handleApiErrors()
                }
                        // Force refresh posts
                        .flatMap { response -> Single.fromCallable { refreshSubject.onNext(Unit) }.map { response } }


    }

}

class LoginDao : AuthorizationDao {

    // always return logged user "me"
    override val authorizedDaoObservable: Observable<Either<DefaultError, AuthorizedDao>> = Single.just(LoggedInDao("me") as AuthorizedDao)
            .handleApiErrors()
            .toObservable()
            .mergeWith(Observable.never())

    data class LoggedInDao(override val userId: String) : AuthorizedDao {
        // always call request with fake token
        override fun <T> callWithAuthTokenSingle(request: (String) -> Single<Either<DefaultError, T>>): Single<Either<DefaultError, T>> =
                request("$userId fake token")
    }
}

interface AuthorizedDao {
    val userId: String

    /**
     * Allows executing requests with authorization tokens
     */
    fun <T> callWithAuthTokenSingle(request: (authorization: String) -> Single<Either<DefaultError, T>>): Single<Either<DefaultError, T>>
}

interface AuthorizationDao {
    /**
     * Returns current logged user or error
     */
    val authorizedDaoObservable: Observable<Either<DefaultError, AuthorizedDao>>
}

fun <T> Single<T>.refreshWhen(refreshObservable: Observable<Unit>): Observable<T> =
        refreshObservable
                .startWith(Unit)
                .onBackpressureLatest()
                .flatMapSingle({ this }, false, 1)

fun <T> Single<T>.toTry(): Single<Try<T>> =
        map { Try.Success(it) as Try<T> }
                .onErrorReturn { Try.Failure(it) }
fun <T> Single<Try<T>>.toEither(): Single<Either<Throwable, T>> = map { it.toEither() }
fun <L, R> Single<R>.toEither(func: (Throwable) -> L): Single<Either<L, R>> = toTry().toEither().map { it.left().map(func) }

sealed class ApiError : DefaultError {
    object NoNetwork: ApiError()
    object Unknown: ApiError()
}
private val handleErrors: (Throwable) -> DefaultError = {
    when (it) {
        is IOException -> ApiError.NoNetwork
        else -> ApiError.Unknown
    }
}

fun <T> Single<T>.handleApiErrors():Single<Either<DefaultError, T>> = toEither(handleErrors)