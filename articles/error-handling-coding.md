# Errors... oh... those errors - coding

![Woman blow a bubble with bubblegum. Source: https://unsplash.com/photos/66ufHo7498k](error-handling-coding/cover2.jpg)

Some time ago I wrote article about how important is to handle errors in nice way in article [Errors... oh... those errors](error-handling.md).
I gave a promise that there will be continuation with coding examples. This is the continuation.

Main objectives:
- Almost `0 code` to use,
- Customizable,
- Extendable.


## TL;DR:

Full code of the example is available [here](../examples/error-handling/src/main/java/com/jacekmarchwicki/example/MainActivity.kt)

## Idea

The ida about errors are quite simple. They can appear in some time, and can be resolved in future. 
Sometimes they can be resolved without user interaction (like user device will reconnect to WiFi network automatically).

So good way of thinking about errors is that they are some kind of temporary state of the application, 
that you should show to a user.
I think the word `state` is pretty important when we would like to implement error handling.

## Testimony

This article is not about how you should make requests to your API, and how you should handle async
 calls so lets assume the following example is perfect way to do API request and I will use it in examples.

```kotlin
class MyActivity : Activity {
    private fun execute(success: (String) -> Unit, failure: (Errors) -> Unit) {
        Thread({
            runOnUiThread {
                if (result.isSuccess) success(result.ok) else failure(result.exception)
            }
        }).start()
    }
}
```

So I will say again. This is not a scope of this article so we ignore issues like:
- Multiple request will be done after re-creating activity (eg. rotation the screen).
- Creating multiple threads that is not memory and CPU efficient.
- Memory leaks that are caused by threads.
- Crashes can be caused by returning value in wrong activity state (eg. after `onDestory()` ).

There are lot of solutions for those problems, but we will focus on showing errors to user.

## Ugly way

So the simples solution for error handling will be:

```kotlin
fun load() {
    execute(
        success = {
            textView.text = "OK"
        },
        failure = {
            Toast.makeText(this, "Ooopps...", Toast.LENGTH_SHORT).show()
        })
}
```

And yas this way is better han nothing but its quite a farr away of the good one.

1. It does not say what is wrong.
2. Error will disapear after a while, so user may didn't notice this toast.
3. When user hit refresh (execute `load()` again) and data will finally load but toast can still be on his screen. He see error and succesed loaded data.
4. Does not support showing progress.

## Be stateful

We use `Option<X>` from popular [funKTionale library](https://github.com/MarioAriasC/funKTionale).
This is simple type that can hold absent value `var error = Option.None`, or real value `var error = Option.Some(IOException())`.

So to handle our error handling in stateful way, we change our code to:

```kotlin
var error: Option<Exception> = Option.None

fun load() {
    execute(
        success = {
            textView.text = "OK"
            error = Option.None
            showError()
        },
        failure = { exception ->
            textView.text = ""
            error = Option.Some(exception)
            showError()
        })
}

fun showError() {
    errorTextView.text = it.fold({""}, {"Some error..."})
}
```

**TIP**: `fun <R> Option<T>.fold(ifEmpty: () -> R, some: (T) -> R): R` executes and returns first function if value is empty (`Option.None`), or second if value has data (`Option.Some(IOException())`).

Ok.. so lets introduce another functional type called `Either<L, R>`. It also implemented in [funKTionale library](https://github.com/MarioAriasC/funKTionale).
`Either<L, R>` is a type that can be either left or right. `Either.Right` is usually used as correct answer, and `Either.Left` us used as wrong answer.
`var result = Either.right("Response")` or `var result = Either.left(IOException())`.

So our code will look like this:

```kotlin
lateinit var result: Either<String, Exception>

fun load() {
    execute(
        success = {
            result = Either.left("OK")
            showError()
        },
        failure = { exception ->
            result = Either.right(exception)
            showError()
        })
}

fun showError() {
    errorTextView.text = it.fold({"Some error..."}, {""})
    textView.text = it.fold({""}, {"Response: $it"})
}
```

Now our code is more stateful but still far away from clean. It also does not support progress of loading.
Also this `lateinit` is potential place for crashes.

```kotlin
object LoadingException : Exception()

var result: Either<String, Exception> = Either.left(LoadingException)

fun load() {
    result = Either.left(LoadingException)
    showError()
    execute(
        success = {
            result = Either.left("OK")
            showError()
        },
        failure = { exception ->
            result = Either.right(exception)
            showError()
        })
}

fun showError() {
    errorTextView.text = it.fold({if (it == LoadingException) "Loading..." else "Some error..."}, {""})
    textView.text = it.fold({""}, {"Response: $it"})
}
```

Now our code:
1. hide errors when loading success.
2. error does not dissapear after a while without resolving user problem,
3. show progress bar.

And ofcourse we can shoe more detailed messages
 
```kotlin
fun showError() {
    errorTextView.text = it.fold({ when (it) {
         LoadingException-> "Loading..."
         is IOException -> "Problem with API ${it.errorMessage}"
         else -> "Unknown error...."
        } }, {""})
    textView.text = it.fold({ "" }, { "Response: $it" })
}
```

No we can add some useful function that will better show our errors:

```kotlin
fun Context.defaultErrorText(error: Exception): String = when (error) {
    LoadingException -> getString(R.string.errors_not_yet_loaded)
    is IOException -> getString(R.string.errors_no_internet_connection)
    else -> getString(R.string.errors_unknown_error, error.errorMessage)
}
```

so our code will change to:
```kotlin
fun showError() {
    errorTextView.text = it.fold({ defaultErrrorText(it) }, { "" })
    textView.text = it.fold({ "" }, { "Response: $it" })
}
```

This code is pretty good but it does not allow for customization, like showing nice images or adding fancy animations. It event does not allow to show spinner when instead of `Loading...` text.

## Customizable solution

Lets work on customizable solution. Lets start with an interface `ErrorHandler<T>`:

```kotlin
interface ErrorHandler<in T> {
    /**
     * Return value when error is handled
     */
    fun showError(error: T): Option<DismissError>
}
typealias DismissError = () -> Unit
```

This interface allows to handle some types of errors and display them to the user. It will also allow to dismiss errors.

We also create class `ErrorManager<in T>` (look on [full code](../examples/error-handling/src/main/java/com/jacekmarchwicki/example/MainActivity.kt) later):

```kotlin
class ErrorManager<in T>(private val errorHandlers: List<ErrorHandler<T>>) {
    private var lastError: Option<DismissError> = Option.None

    /**
     * Present error to user
     */
    fun showError(error: Option<T>) = TODO("Implementation in example source")
}
```

This class allows add multiple error handlers that supports multiple errors. And use it as one big ErrorHandler that support handling multiple types of errors in multiple ways.

So now our code look like this:

```kotlin
val errorManager = ErrorManager(listOf(/** list of error managers **/ ))
fun showError() {
    errorManager.showError(it.left().toOption())
    textView.text = it.fold({ "" }, { "Response: $it" })
}
```

Now we will implement first error handler, that will add text view with an error to container with error message. It will remove this view if error will be resolved.


```kotlin
class ShowScreenErrorHandler<in T>(private val view: ViewGroup, private val text: (T) -> Option<String>) : ErrorHandler<T> {
    override fun showError(error: T): Option<DismissError> = text(error)
            .map {
                val newView = LayoutInflater.from(view.context).inflate(R.layout.error_layout, view, false)
                view.addView(newView)
                newView.error_layout_text.text = it
                fun () { view.removeView(newView) }
            }
}
```

So now our `errorManager` will look like this:

```kotlin
val errorManager = ErrorManager<Exception>(listOf(
                ShowScreenErrorHandler(frameLayout, { Option.Some(defaultErrorText(it)) })
        ))
```

But I said this solution is `customizable` but nothing has changed yet :/ Let fix this. 
You decided to add your fancy progress bar to all places in your app instead of `Loading...` indicator. You just create new `ErrorHandler`:

```kotlin
class ProgressErrorHandler(private val view: FrameLayout) : ErrorHandler<Errors> {
    override fun showError(error: Errors): Option<DismissError> = if (error is LoadingException) {
        Option.None
    } else {
        val newView = LayoutInflater.from(view.context).inflate(R.layout.progress_layout, view, false)
        view.addView(newView)
        val dismiss = { view.removeView(newView) }
        Option.Some(dismiss)
    }

}
```

and change your code to:

```kotlin
val errorManager = ErrorManager(listOf(
        ProgressErrorHandler(frameLayout),
        ShowScreenErrorHandler(frameLayout, { Option.Some(defaultErrorText(it)) })
    ))
```

Ok... but now requirements has changed and one request can return NotFound (404) error and you should display some nice text error to user.
 
```kotlin
val errorManager = ErrorManager(listOf(
        ProgressErrorHandler(activity_main_content),
        ShowScreenErrorHandler(activity_main_content, {
            Option.Some(when (it) {
                NotFoundException -> "Page deleted"
                else -> defaultErrorText(it)
            })
        })
))
```

## Advanced customization

Now you would like to do something fancy :)

Lets start with some handler that simplify handling only one error:

```kotlin
inline fun <reified T : K, K> castErrorHandler(crossinline show: (T) -> DismissError) : ErrorHandler<K> = object : ErrorHandler<K> {
    override fun showError(error: K): Option<DismissError> = if (error is T) {
        Option.Some(show(error))
    } else {
        Option.None
    }

}
```

### Animations

I.e. you have "Like" button. If user click it (during progress) you would like to slowly animate it.

```kotlin
val likeErrorManager = ErrorManager(listOf(
            castErrorHandler<LoadingException, Exception> {
                val animation = likeButton.animate().alpha(0f).apply { start() }
                likeButton.enabled = false
                fun() {
                    animation.cancel()
                    likeButton.animate().alpha(1.0f).start()
                    likeButton.enabled = true
                }
            },
            SnackbarErrorHandler(frameLayout, { Option.Some(defaultErrorText(it)) })
    ))
```

and yes.. this is not an issue instead of using `ShowScreenErrorHandler` we can use `SnackbarErrorHandler` that will show snackbar in case actions like liking, sending, saving etc.:

```kotlin
class SnackbarErrorHandler<in T>(private val view: View, private val text: (T) -> Option<String>) : ErrorHandler<T> {
    override fun showError(error: T): Option<DismissError> = text(error)
            .map {
                val snackbar = Snackbar.make(view, it, Snackbar.LENGTH_SHORT)
                        .apply { show() }
                val dismiss = { snackbar.dismiss() }
                dismiss
            }
}
```

### Images

We usually some kind of nice image with text when user does not have messages or contacts on lists:
 
```kotlin
val errorManager = ErrorManager(listOf(
        ViewWithImageErrorHandler<EmptyListException, Exception>(R.drawable.empty_list_image, R.string.empty_list_string),
        ShowScreenErrorHandler(frameLayout, { Option.Some(defaultErrorText(it)) })
))
```

And I think it wouldn't be hard to implement `ViewWithImageErrorHandler` by yourself ;)

## EditText

If you implement login view and user type wrong password you can do this like:

```kotlin
val errorManager = ErrorManager(listOf(
            castErrorHandler<WrongPasswordException, Exception> {
                passwordTextView.showError("Wrong password")
                fun() {
                    passwordTextView.showError()
                }
            },
            SnackbarErrorHandler(frameLayout, { Option.Some(defaultErrorText(it)) })
    ))
```

or

```kotlin
val errorManager = ErrorManager(listOf(
            EditTextErrorView<WrongPasswordException, Exception>(passwordTextView, R.string.wrong_password),
            EditTextErrorView<WrongEmailException, Exception>(emailTextView, R.string.wrong_email),
            SnackbarErrorHandler(frameLayout, { Option.Some(defaultErrorText(it)) })
    ))
```

You can imagine how `EditTextErrorView` could be implemented and reused in many places in your code. 

## Snackbar and Views

* For actions (e.g. "Liking", "Sending", "Posting", "Commenting", "Sharing", "Logging in") use non persistent errors like `SnackbarErrorHandler` or other error handlers that can be dismissed.
* For stateful data (e.g. "List of comments", "List of posts", "Post view", "User details") use persistent errors like `ShowScreenErrorHandler` that do not disapear.
