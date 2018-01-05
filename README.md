# Extensions functions for RxJava (article draft)

This introduction of some patterns that can be helpful during the implementation of Android applications that operate on some structured data especially downloaded from some kind of API.

**Warning: Lot of code alert.** Because I'm apps developer and probably you also, I will show a lot of code.

# TL;DR;
[Full code is available here](https://github.com/jacek-marchwicki/example-rx-and-kotlin-and-either/blob/master/app/src/main/java/com/jacekmarchwicki/examplerxextensions/MainActivity.kt)

# Used libraries

**TIP:** We use RxJava 1.x but almost the same code can be used with RxJava 2.x

```groovy
implementation "io.reactivex:rxjava:1.3.0"
implementation "io.reactivex:rxandroid:1.2.1"
implementation "org.funktionale:funktionale-all:1.2"
implementation "com.jakewharton.rxbinding:rxbinding-kotlin:1.0.1"
implementation 'com.github.jacek-marchwicki.recyclerview-changes-detector:universal-adapter:1.0.2'
```

# Let’s start with some example

We like to implement UI that displays posts that from API and allow to send new. 
Our UI need a RecyclerView for displaying the posts, a button, and a text field so a user is able to post to the server.

At the starting point, you have two classes that represent API responses:

* `ApiError` — represents some errors from API,
* `Post` — represents post that needs to be added to the server.

```kotlin
interface DefaultError
object ApiError : DefaultError

data class Post(val id: String, val title: String)
```

You have also some service, probably written using [retrofit](http://square.github.io/retrofit/):

```kotlin
class PostsService {
    fun getPosts(authorization: String): Single<List<Post>> = Single.just(listOf(Post("${authorization}id1", "test")))
            .delay(2, TimeUnit.SECONDS)

    fun addPost(authorization: String, post: Post): Single<Unit> = Single.just(Unit).delay(2, TimeUnit.SECONDS)
}
```

I expect that you have some UI and model (Dao - Data Access Object) that will provide authorization.

```kotlin
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

```

In this case, this will be just a mock:

```kotlin
class LoginDao : AuthorizationDao {

    // always return logged user "me"
    override val authorizedDaoObservable: Observable<Either<DefaultError, AuthorizedDao>>
            = Observable.just(Either.right(LoggedInDao("me")) as Either<DefaultError, AuthorizedDao>)
            .mergeWith(Observable.never()).cache()

    data class LoggedInDao(override val userId: String) : AuthorizedDao {
        // always call request with fake token
        override fun <T> callWithAuthTokenSingle(request: (String) -> Single<Either<DefaultError, T>>): Single<Either<DefaultError, T>> =
                request("$userId fake token")
    }
}
```

**Important:** All Dao's are singletons.

Your `Application` class can look like this if you don't want to use dagger:

```kotlin
class MainApplication : Application() {
    val authorizationDao: AuthorizationDao by lazy { LoginDao() }
}
```

# Start implementation

You create some base class for receiving posts and for sending new.

```kotlin
class PostsDaos(val computationScheduler: Scheduler,
                val service: PostsService) {
    private val cache = HashMap<AuthorizedDao, PostsDao>()
    fun getDao(key: AuthorizedDao): PostsDao = cache.getOrPut(key, { PostsDao(key) })

    inner class PostsDao(private val authorizedDao: AuthorizedDao) {
        val posts: Observable<Either<DefaultError, List<Post>>> = Observable.error(NotImplementedError())
        fun createPost(post: Post): Single<Either<DefaultError, Unit>> = Single.error(NotImplementedError())
    }

}
```

`PostsDao` needs `val authorizedDao: AuthorizedDao` because posts are different for different authorized users and we don't want to return other user posts when user re-login to a different account.
Cache is used so the same instance always will be returned for the same authorized user. We could use more sophisticated cache but HashMap is good enough.

Now you implement receive posts in your `PostsDao`:

```kotlin
val posts: Observable<Either<DefaultError, List<Post>>> =
    authorizedDao.callWithAuthTokenSingle { authorization -> // 1
        service.getPosts(authorization) // 2
                .subscribeOn(networkScheduler) // 3
                .map { Either.right(it) as Either<DefaultError, List<Post>> } // 4
                .onErrorReturn { Either.left(ApiError as DefaultError) } // 5
    }
    .toObservable() // 6
    .cache() // 7
```

* Now you request new authorization token (*1*) and request API (*2*).
* You need to use request API over the different thread.
* You want to handle errors in a nice way (*4, 5*).
* You want to convert your `Single<>` to `Observable<>` (*6*).
* And you also want to cache results (*7*).

Now you need implement send post in your `PostsDao`:

```kotlin
fun createPost(post: Post): Single<Either<DefaultError, Unit>> =
    authorizedDao.callWithAuthTokenSingle { authorization -> // 1
        service.addPost(authorization, post) // 2
                .subscribeOn(networkScheduler) // 3
                .map { Either.right(it) as Either<DefaultError, Unit> } // 4
                .onErrorReturn { Either.left(ApiError as DefaultError) } // 5
    }
    .flatMap { response -> Single.fromCallable { refreshSubject.onNext(Unit) }.map { response } } // 6
```

* Points from *1-6* are the same as in the fetch example.
* But now you don't convert to `Observable` because this is an action that will create a post and give one result.
* You also don't cache result because invoking `createPost(Post)` should always create a post.
* But you need to inform posts that something has changed.

Now you adjust `posts`:

```kotlin
val posts: Observable<Either<DefaultError, List<Post>>> =
    refreshSubject.startWith(Unit) // 0.1
            .onBackpressureLatest() // 0.2
            .flatMapSingle({ // 0.3
                authorizedDao.callWithAuthTokenSingle { /* ... */ } // 1,2,3,4,5
            }, delayErrors = false, maxConcurrent = 1)
            .replay(bufferSize = 1) // 6.1
            .refCount() // 6.2
```

* First (*0.1*) we will wait for changes from refreshSubject and start with some default value that will execute our request.
* We ensure if multiple refreshes request will occur we will ignore them (*0.2*).
* Each request for downloading data we will `flatMapSingle` (*0.3*) with our API call. We don't want to use more than one concurrent API call.
* Instead of caching all results we want to use only last one (`replay(1).refCount()`) (*6.1, 6.2*)
 
Now your `PostsDao` is super cool. If post will be added, list of posts is automatically refreshed.
Your business logic is hidden inside of `PostsDao`.

# Presenters

Presenters and UI are not parts of this tutorial but if you would like to see how to use code that you just wrote see the full code here:
[Presenters and UI implementation](https://github.com/jacek-marchwicki/example-rx-and-kotlin-and-either/blob/master/app/src/main/java/com/jacekmarchwicki/examplerxextensions/MainActivity.kt)

# So what we can do better

## Better error handling

In case of my perspective the ugliest code is here:

```kotlin
val x = Single.just(Unit)
  .map { Either.right(it) as Either<DefaultError, Unit> }
  .onErrorReturn { Either.left(ApiError as DefaultError) }
```

* It requires type definition and casting.
* It's very common for API requests.
* If it's actually just a changing error to either it requires two operations.

So lets define useful extension function:

```kotlin
fun <L, R> Single<R>.toEither(func: (Throwable) -> L): Single<Either<L, R>> =
        map { Either.right(it) as Either<L, R> }
                .onErrorReturn { Either.left(func(it)) }          
```

so your code will look like this:

```kotlin
val x = Single.just(Unit)
  .toEither { ApiError as DefaultError }
```

But implementation of our extension function could more universal:

```kotlin
fun <T> Single<T>.toTry(): Single<Try<T>> = map { Try.Success(it) as Try<T> } .onErrorReturn { Try.Failure(it) }
fun <T> Single<Try<T>>.toEither(): Single<Either<Throwable, T>> = map { it.toEither() }
fun <L, R> Single<R>.toEither(func: (Throwable) -> L): Single<Either<L, R>> = toTry().toEither().map { it.left().map(func) }
```

Now you can implement API error handling in a universal super cool way:

```kotlin
val x = Single.just(Unit)
  .handleApiErrors()
  
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
```

## Better refreshing

Imho the code:

```kotlin
val x = refreshSubject.startWith(Unit)
    .onBackpressureLatest()
    .flatMapSingle({
        /* ... */
    }, false, 1)
```

it's pretty hard to understand, if it's hard to understand your code you need to rewrite it:

```kotlin
fun <T> Single<T>.refreshWhen(refreshObservable: Observable<Unit>): Observable<T> = 
    refreshObservable
        .startWith(Unit)
        .onBackpressureLatest()
        .flatMapSingle({ this }, false, 1)
```

```kotlin
val posts: Observable<Either<DefaultError, List<Post>>> =
    authorizedDao.callWithAuthTokenSingle { authorization ->
        service.getPosts(authorization)
                .subscribeOn(networkScheduler)
                .handleApiErrors()
    }
    .refreshWhen(refreshSubject)
    .replay(1)
    .refCount()
```

# Summary

1. Extensions functions in Kotlin can make your code more readable.
2. Writing business logic in DAO's (Data Access Objects) makes your code easier to understand.
3. Either's are better than throwable for handling errors.

# What's more

If you would like to see how to test look here: [Using schedulers while testing your code](https://medium.com/@JMarchwicki/using-schedulers-while-testing-your-code-cd924940cc04)