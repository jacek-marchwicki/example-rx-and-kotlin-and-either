# Kotlin and RxJava with extension functions

This is an introduction to some patterns that can be helpful during an implementation of Android applications that operate on any structured data, especially downloaded from APIs.

**Warning: Lots of codes.** Since it's relatively a technical article and I am an Android dev (probably as you are), there will be a lot of code examples.

# TL;DR;
[The full code is available here](../examples/kotlin-and-rxjava-with-extensions-functions/src/main/java/com/jacekmarchwicki/examplerxextensions/MainActivity.kt)

# Used libraries

**TIP:** In the example, I use RxJava 1.x but almost the same code can be used with RxJava 2.x

```groovy
implementation "io.reactivex:rxjava:1.3.0"
implementation "io.reactivex:rxandroid:1.2.1"
implementation "org.funktionale:funktionale-all:1.2"
implementation "com.jakewharton.rxbinding:rxbinding-kotlin:1.0.1"
implementation 'com.github.jacek-marchwicki.recyclerview-changes-detector:universal-adapter:1.0.2'
```

# Let’s start with an example

We would like to implement UI that displays posts from API and allows sending new ones. 
Our UI needs a RecyclerView for displaying posts, a button, and a text field, so a user is able to post to the server.

Firstly, you have two classes that represent API responses:

* `ApiError` — represents some errors from API,
* `Post` — represents a post that needs to be added to the server.

```kotlin
interface DefaultError // General errors class
object ApiError : DefaultError // Api errors class

data class Post(val id: String, val title: String)
```

You have also some service, probably written using [retrofit](http://square.github.io/retrofit/). We will mock it:

```kotlin
class PostsService {
    fun getPosts(authorization: String): Single<List<Post>> = 
        Single.just(listOf(Post("${authorization}id1", "test")))
            .delay(2, TimeUnit.SECONDS)

    fun addPost(authorization: String, post: Post): Single<Unit> = 
        Single.just(Unit).delay(2, TimeUnit.SECONDS)
}
```

I expect that you already have some UI and some model (Dao - Data Access Object) that will provide authorization.

```kotlin
interface AuthorizedDao {
    val userId: String

    /**
     * Allows executing requests with authorization tokens
     */
    fun <T> callWithAuthTokenSingle(
      request: (authorization: String) -> Single<Either<DefaultError, T>>
      ): Single<Either<DefaultError, T>>
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
    override val authorizedDaoObservable: Observable<Either<DefaultError, AuthorizedDao>> = 
        Observable.just(Either.right(LoggedInDao("me")) as Either<DefaultError, AuthorizedDao>)
            .mergeWith(Observable.never()).cache()

    data class LoggedInDao(override val userId: String) : AuthorizedDao {
        // always call request with fake token
        override fun <T> callWithAuthTokenSingle(
          request: (String) -> Single<Either<DefaultError, T>>): Single<Either<DefaultError, T>> =
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

# Start the implementation

You create some base class for receiving posts and for sending new ones.

```kotlin
class PostsDaos(val computationScheduler: Scheduler,
                val service: PostsService) {
    private val cache = HashMap<AuthorizedDao, PostsDao>()
    fun getDao(key: AuthorizedDao): PostsDao = cache.getOrPut(key, { PostsDao(key) })

    inner class PostsDao(private val authorizedDao: AuthorizedDao) {
        val posts: Observable<Either<DefaultError, List<Post>>> = 
          Observable.error(NotImplementedError())
        fun createPost(post: Post): Single<Either<DefaultError, Unit>> = 
          Single.error(NotImplementedError())
    }

}
```

`PostsDao` needs `val authorizedDao: AuthorizedDao` because posts are different for different authorized users and we don't want to return other user's posts when a user re-logs in-to another account.
Cache is used so the same instance will always return the same authorized user. We could use more sophisticated cache but HashMap is good enough.

Now you implement receiving posts in your `PostsDao`:

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

* Now, you request a new authorization token (*1*) and request API (*2*).
* You need to use request API over a different thread.
* You want to handle errors in a nifty way (*4, 5*).
* You want to convert your `Single<>` to `Observable<>` (*6*).
* And you also want to cache results (*7*).

Now, you need to implement sending posts in your `PostsDao`:

```kotlin
fun createPost(post: Post): Single<Either<DefaultError, Unit>> =
    authorizedDao.callWithAuthTokenSingle { authorization -> // 1
        service.addPost(authorization, post) // 2
                .subscribeOn(networkScheduler) // 3
                .map { Either.right(it) as Either<DefaultError, Unit> } // 4
                .onErrorReturn { Either.left(ApiError as DefaultError) } // 5
    }
    .flatMap { response -> 
      Single.fromCallable { refreshSubject.onNext(Unit) }
      .map { response } 
    } // 6
```

* Points from *1-6* are the same as in the fetch example.
* But now, you don't convert to `Observable` because this is an action that will create a post and give one result.
* You also don't cache result because invoking `createPost(Post)` should always create a post.
* But you need to inform posts that something has changed (*6*).

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
* We make sure we ignore multiple refreshes requests when they occur (*0.2*).
* We will `flatMapSingle` (*0.3*) with our API call each request for downloading data. We don't want to use more than one concurrent API call.
* Instead of caching all results, we want to use only the most recent one (`replay(1).refCount()`) (*6.1, 6.2*)
 
Now your `PostsDao` is super cool. When a post will be added, the list of posts will be automatically refreshed.
Your business logic is hidden inside of `PostsDao`.

# Presenters

Presenters and UI are not a part of this tutorial but if you would like to see how to use code that you have just written, check the full code here:
[Presenters and UI implementation](../examples/kotlin-and-rxjava-with-extensions-functions/src/main/java/com/jacekmarchwicki/examplerxextensions/MainActivity.kt)

# So what can we do better?

## Better error handling

In my opinion, the ugliest code is here:

```kotlin
val x = Single.just(Unit)
  .map { Either.right(it) as Either<DefaultError, Unit> }
  .onErrorReturn { Either.left(ApiError as DefaultError) }
```

* It requires type definition and casting.
* It's very common for API requests.
* It requires two operations but it's a single operation (just a changing error to either).

So let's define the useful extension function:

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

But the implementation of our extension function could be more universal:

```kotlin
fun <T> Single<T>.toTry(): Single<Try<T>> = 
  map { Try.Success(it) as Try<T> } 
  .onErrorReturn { Try.Failure(it) }
fun <T> Single<Try<T>>.toEither(): Single<Either<Throwable, T>> = 
  map { it.toEither() }
fun <L, R> Single<R>.toEither(func: (Throwable) -> L): Single<Either<L, R>> = 
  toTry()
  .toEither()
  .map { it.left().map(func) }
```

Now, you can implement handling an API errors in a universal super cool way:

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

fun <T> Single<T>.handleApiErrors():Single<Either<DefaultError, T>> = 
  toEither(handleErrors)
```

## Better refreshing

IMHO the code:

```kotlin
val x = refreshSubject.startWith(Unit)
    .onBackpressureLatest()
    .flatMapSingle({
        /* ... */
    }, false, 1)
```

is pretty hard to understand. If the code is hard to understand, you need to rewrite it:

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

What you have learned:
1. Extension functions in Kotlin can make your code more readable. You also do not have to write the same code constantly.
2. Writing business logic in DAO's (Data Access Objects) makes your code easier to understand. Logic is separated from views.
3. Either's are better than throwable's for handling errors.
4. Good code model allows agile modification of it without touching views.

# What's more

If you would like to see how to test it, check this out: [Using schedulers while testing your code](using-schedulers-while-testing-your-code.md)

# Authors

Author:
* Jacek Marchwicki [jacek.marchwicki@gmail.com](mailto:jacek.marchwicki@gmail.com)

Corrected by:
* Marcin Adamczewski
* Andrzej Wyduba