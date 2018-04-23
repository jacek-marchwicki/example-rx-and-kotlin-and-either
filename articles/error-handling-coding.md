# Errors... oh... those errors - coding

![Woman blows a bubble with bubblegum. Source: https://unsplash.com/photos/66ufHo7498k](error-handling-coding/cover2.jpg)

Some time ago, I wrote an article about how important it is to handle errors in a nice way. In the article [Errors... oh... those errors](error-handling.md), I made a promise that there would be a continuation with coding examples. This is the continuation.

Main objectives:
- Almost `0 code` to use,
- Customizable,
- Extendable.


# TL;DR:

The full code of the example is available [here](../examples/error-handling/src/main/java/com/jacekmarchwicki/example/MainActivity.kt)

# Idea

The idea of errors is quite simple. They can appear in some time and can be resolved in the future. 
Sometimes they can be resolved without user interaction (like user device will reconnect to WiFi network automatically).

So, a good way of thinking about errors is that they are some kind of a temporary state of the application, that should be presented to a user.
I think the word `state` is pretty important when we would like to implement error handling.

# Testimony

This article is not about how you should make requests to your API, and how you should handle async
 calls, so let's assume the following example is the perfect way to do API request and I will use it in examples.

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

So I will say again. This is not the scope of this article, so we ignore issues like:
- Multiple requests will be done after re-creating activity (eg. rotation the screen).
- Creating multiple threads that are not memory and CPU efficient.
- Memory leaks that are caused by threads.
- Crashes can be caused by returning value in a wrong activity state (eg. after `onDestory()` ).

There are a lot of solutions to these problems, but we will focus on showing errors to the user.

# Ugly way

So the simplest solution for error handling will be:

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

And yes, this way is better than nothing, but it's quite far away from the good one.

1. It does not say what is wrong.
2. An error will disappear after a while, so user may not notice this toast.
3. When a user hits refresh (execute `load()` again) and data will finally load but toast can still be on his screen. He sees an error and success loaded data.
4. Does not support showing progress.

# Be stateful

We use `Option<X>` from popular [funKTionale library](https://github.com/MarioAriasC/funKTionale).
This is a simple type that can hold absent value `var error = Option.None`, or real value `var error = Option.Some(IOException())`.

So to handle our error handling in a stateful way, we change our code to:

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

**TIP**: `fun <R> Option<T>.fold(ifEmpty: () -> R, some: (T) -> R): R` executes and returns first function if a value is empty (`Option.None`), or the second if a value has data (`Option.Some(IOException())`).

Ok.. so let's introduce another functional type called `Either<L, R>`. It is also implemented in [funKTionale library](https://github.com/MarioAriasC/funKTionale).
`Either<L, R>` is a type that can be either left or right. `Either.Right` is usually used as the correct answer, and `Either.Left` is used as the wrong answer.
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

Now, our code is more stateful but still far from being clean. It also does not support a progress of loading.
Also, this `lateinit` is a potential place for crashes.

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
1. hides errors when loading success.
2. error does not disappear after a while without resolving user problem,
3. shows the progress bar.

And, of course we can show more detailed messages
 
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

Now, we can add some useful functions that will better highlight our errors:

```kotlin
fun Context.defaultErrorText(error: Exception): String = when (error) {
    LoadingException -> getString(R.string.errors_not_yet_loaded)
    is IOException -> getString(R.string.errors_no_internet_connection)
    else -> getString(R.string.errors_unknown_error, error.errorMessage)
}
```

so our code will change:
```kotlin
fun showError() {
    errorTextView.text = it.fold({ defaultErrrorText(it) }, { "" })
    textView.text = it.fold({ "" }, { "Response: $it" })
}
```

This code is pretty good but it does not allow for customization, like showing nice images or adding fancy animations. It even does not allow to show spinner instead of `Loading...` text.

## Customizable solution

Let's work on the customizable solution. Let's start with an interface `ErrorHandler<T>`:

```kotlin
interface ErrorHandler<in T> {
    /**
     * Return value when error is handled
     */
    fun showError(error: T): Option<DismissError>
}
typealias DismissError = () -> Unit
```

This interface allows to handle some types of errors and display them to the user. It will also allow to dismiss error.

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

This class allows to add multiple error handlers that support multiple errors. And use it as one big ErrorHandler that supports handling multiple types of errors in multiple ways.

So now our code looks like this:

```kotlin
val errorManager = ErrorManager(listOf(/** list of error managers **/ ))
fun showError() {
    errorManager.showError(it.left().toOption())
    textView.text = it.fold({ "" }, { "Response: $it" })
}
```

Now we will implement the first error handler, that will add a text view with an error to a container with an error message. It will remove this view if the error will be resolved.


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

But as I said, this solution is `customizable` but nothing has changed yet :/ Let's fix this. 
You decide to add your fancy progress bar to all places in your app instead of `Loading...` indicator. 
You create new `ErrorHandler`:

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

Ok... But now requirements have changed and one request can return NotFound (404) error and you should display some nice text error to the user.
 
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

Let's start with some handler that simplifies handling just one error:

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

I.e. you have "Like" button. If user clicks it (during progress) you would like to slowly animate it.

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

and yes... This is not an issue. Instead of using `ShowScreenErrorHandler` we can use `SnackbarErrorHandler` that will show snack bar in for actions (liking, sending, saving etc.):

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

## Images

We usually show some kind of nice image with text when a user does not have messages or contacts on lists:
 
```kotlin
val errorManager = ErrorManager(listOf(
        ViewWithImageErrorHandler<EmptyListException, Exception>(R.drawable.empty_list_image, R.string.empty_list_string),
        ShowScreenErrorHandler(frameLayout, { Option.Some(defaultErrorText(it)) })
))
```

And I think it wouldn't be hard to implement `ViewWithImageErrorHandler` by yourself ;)

## EditText

If you implement login screen and user type wrong password you can do like this:

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

# Errors persistence

* For actions (e.g. "Liking", "Sending", "Posting", "Creating", "Commenting", "Sharing", "Logging in") use non persistent errors like `SnackbarErrorHandler`, `EditTextErrorView` or other error handlers that can be dismissed.
* For stateful data (e.g. "List of comments", "List of posts", "Post view", "User details") use persistent errors like `ShowScreenErrorHandler`, `ViewWithImageErrorHandler` that do not disappear.

[More about errors persistence](error-handling.md#errors-persistence)

# Summary

* Good error handling shouldn't be hard to implement
* By using this example, you can implement very simple and also very powerful error handling to your whole app fairly quickly.
* Begin with simplest snack bar and error text view solution, then extend to more user-friendly solutions.
* Reuse your error handling code as described in this tutorial.
* Implement error handling everywhere, where a non-programmer error might happen.

# What's more

* [Kotlin and RxJava with extensions functions](kotlin-and-rxjava-with-extensions-functions.md)
* [Using schedulers while testing your code](using-schedulers-while-testing-your-code.md)
* [Errors... oh... those errors - Part 1](error-handling.md)


# Authors

Author:
* Jacek Marchwicki [jacek.marchwicki@gmail.com](mailto:jacek.marchwicki@gmail.com)