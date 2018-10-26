# Errors... oh... those errors - coding

![Woman blows a bubble with bubblegum. Source: https://unsplash.com/photos/66ufHo7498k](error-handling-coding/cover2.jpg)

Some time ago, I wrote an article about how important it is to handle errors in a nice way. In the article [Errors... oh... those errors]error-handling.md), I made a promise that there would be a continuation with coding examples. This is it.

Main objectives:
- Almost `0 code` to use,
- Customizable,
- Extendable.

# TL;DR:

The full exemplary code is available [here](../examples/error-handling/src/main/java/com/jacekmarchwicki/example/MainActivity.kt).

# Idea

The idea of error messages is quite simple. They appear and, once the error is resolved, disappear. Sometimes it happens without user’s interaction (like when a user’s device reconnects to WiFi network automatically).
So, a good way of thinking about errors is that they are a temporary state of an application, which should be displayed to a user. I think the word *state* is important while implementing error handling.

# Testimony

This article isn’t about how you should make requests to your API, and how you should handle asynchronous calls, so let's assume the following is the perfect way to do API requests and let me use it in further examples.

```kotlin
class MyActivity : Activity {
    private fun execute(success: (String) -> Unit, failure: (Errors) -> Unit) {
        Thread {
            runOnUiThread {
                if (result.isSuccess) success(result.ok) else failure(result.exception)
            }
        }.start()
    }
}
```

I’ll say it again. We ignore issues like:
- Multiple requests after re-creating activity (e.g. rotation of the screen).
- Creating multiple threads (neither memory-, nor CPU-efficient).
- Memory leaks caused by unfinished threads.
- Crashes caused by returning value in a wrong activity state (e.g. after `onDestory()`).

There are lots of solutions to these problems, but we focus on displaying error messages to the user.

# Ugly way

So the simplest solution for error handling would be:

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

And yes, this way is better than nothing, but it's quite far from good.
1. It doesn’t say what’s wrong.
2. An error message disappears after a while, so a user may not notice the toast.
3. When a user hits the refresh button (execute `load()` again), data finally loads but the toast of the previous error still lingers on the screen. The user sees simultaneously the  error message and the successfully loaded data.
4. It doesn’t support displaying progress.

# Be stateful

We use `Option<X>` from popular [funKTionale library](https://github.com/MarioAriasC/funKTionale). 
This is a simple type that can hold absent value `var error = Option.None`, or real value `var error = Option.Some(IOException())`.

So to handle our error management in a stateful way, we change our code to:

```kotlin
var error: Option<Exception> = Option.None

fun load() {
    execute(
        success = {
            textView.text = "OK"
            error = Option.None
            showHideError()
        },
        failure = { exception ->
            textView.text = ""
            error = Option.Some(exception)
            showHideError()
        })
}

fun showHideError() {
    errorTextView.text = it.fold({""}, {"Some error..."})
}
```

**TIP**: `fun <R> Option<T>.fold(ifEmpty: () -> R, some: (T) -> R): R` executes and returns the first function if the value’s empty (`Option.None`), or the other one if the value’s defined (`Option.Some(IOException())`).

Ok... so let's introduce another functional type called `Either<L, R>`,  also available in [funKTionale library](https://github.com/MarioAriasC/funKTionale). \
`Either<L, R>` is a type that contains either left or right value. `Either.Right` is usually used as the correct answer, and `Either.Left` is used as the wrong answer. `var result = Either.right("Response")` or `var result = Either.left(IOException())`.
So our code would now look like this:

```kotlin
lateinit var result: Either<String, Exception>

fun load() {
    execute(
        success = {
            result = Either.left("OK")
            showHideError()
        },
        failure = { exception ->
            result = Either.right(exception)
            showHideError()
        })
}

fun showHideError() {
    errorTextView.text = it.fold({"Some error..."}, {""})
    textView.text = it.fold({""}, {"Response: $it"})
}
```

Now, our code is more stateful but still far from clean. It still doesn’t support displaying progress. Also, this particular lateinit is crash-prone.

```kotlin
object LoadingException : Exception()

var result: Either<String, Exception> = Either.left(LoadingException)

fun load() {
    result = Either.left(LoadingException)
    showHideError()
    execute(
        success = {
            result = Either.left("OK")
            showHideError()
        },
        failure = { exception ->
            result = Either.right(exception)
            showHideError()
        })
}

fun showHideError() {
    errorTextView.text = it.fold({if (it == LoadingException) "Loading..." else "Some error..."}, {""})
    textView.text = it.fold({""}, {"Response: $it"})
}
```

Now our code:
1. hides error messages when loading succeeds,
2. prevents error messages from disappearing unless the problem is resolved,
3. shows the progress indicator.

Furthermore, we can display more detailed messages
 
```kotlin
fun showHideError() {
    errorTextView.text = it.fold({ when (it) {
         LoadingException-> "Loading..."
         is IOException -> "Problem with API ${it.errorMessage}"
         else -> "Unknown error...."
        } }, {""})
    textView.text = it.fold({ "" }, { "Response: $it" })
}
```

Now, we can add a common function that will better describe what went wrong:

```kotlin
fun Context.defaultErrorText(error: Exception): String = when (error) {
    LoadingException -> getString(R.string.errors_not_yet_loaded)
    is IOException -> getString(R.string.errors_no_internet_connection)
    else -> getString(R.string.errors_unknown_error, error.errorMessage)
}
```

so our code will change to:

```kotlin
fun showHideError() {
    errorTextView.text = it.fold({ defaultErrrorText(it) }, { "" })
    textView.text = it.fold({ "" }, { "Response: $it" })
}
```

This looks pretty good but it doesn’t allow customization like showing nice images or adding fancy animations. It doesn’t even allow showing a spinner instead of *Loading...* text.

## Customizable solution

Let's work on the customizable solution, starting with the interface `ErrorHandler<T>`:

```kotlin
interface ErrorHandler<in T> {
    /**
     * Return value when error is handled
     */
    fun showHideError(error: T): Option<DismissError>
}
typealias DismissError = () -> Unit
```

This allows handling some types of errors and displaying/dismissing them.
We also create `ErrorManager<in T>` class (take a look at [full code](../examples/error-handling/src/main/java/com/jacekmarchwicki/example/MainActivity.kt) later):

```kotlin
class ErrorManager<in T>(private val errorHandlers: List<ErrorHandler<T>>) {
    private var lastError: Option<DismissError> = Option.None

    /**
     * Present error to user
     */
    fun showHideError(error: Option<T>) = TODO("Implementation in the source code")
}
```

This class allows adding multiple error handlers. Each error handler supports a different class of errors. As a result, we get one big ErrorManager that supports handling multiple types of errors in multiple ways.
So now our code looks like this:

```kotlin
val errorManager = ErrorManager(listOf(/** list of error managers **/ ))
fun showHideError() {
    errorManager.showHideError(it.left().toOption())
    textView.text = it.fold({ "" }, { "Response: $it" })
}
```

We implement the first error handler that adds a text view to a container with an error message. Error handler removes the view as soon as the error is resolved.

```kotlin
class ShowScreenErrorHandler<in T>(private val view: ViewGroup, private val text: (T) -> Option<String>) : ErrorHandler<T> {
    override fun showHideError(error: T): Option<DismissError> = text(error)
            .map {
                val newView = LayoutInflater.from(view.context).inflate(R.layout.error_layout, view, false)
                view.addView(newView)
                newView.error_layout_text.text = it
                fun () { view.removeView(newView) }
            }
}
```

So now our errorManager looks like this:

```kotlin
val errorManager = ErrorManager<Exception>(listOf(
                ShowScreenErrorHandler(frameLayout), { Option.Some(defaultErrorText(it)) }
        ))
```

I’ve said before that this solution is *customizable* but nothing has changed yet :/ Let's fix this. 
You decide that instead of *Loading..*. text you want a fancy spinner everywhere in the app. 
You create a new `ErrorHandler`:

```kotlin
class ProgressErrorHandler(private val view: FrameLayout) : ErrorHandler<Errors> {
    override fun showHideError(error: Errors): Option<DismissError> = if (error is LoadingException) {
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

Ok... But now requirements have changed and one of the requests may return *NotFound (404)* error. You should display some nice error message to the user.
 
```kotlin
val errorManager = ErrorManager(listOf(
        ProgressErrorHandler(activity_main_content),
        ShowScreenErrorHandler(activity_main_content) {
            Option.Some(when (it) {
                NotFoundException -> "Page deleted"
                else -> defaultErrorText(it)
            })
        }
))
```

## Advanced customization

Now you might wanna do something fancy :)
Let's start with a function that simplifies handling just one error:

```kotlin
inline fun <reified T : K, K> castErrorHandler(crossinline show: (T) -> DismissError) : ErrorHandler<K> = object : ErrorHandler<K> {
    override fun showHideError(error: K): Option<DismissError> = if (error is T) {
        Option.Some(show(error))
    } else {
        Option.None
    }

}
```

### Animations

I.e. you have a "Like" button. After a user clicks it we show progress by slowly animating the button.

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

And yes... there is no typo. Instead of using `ShowScreenErrorHandler`, we can use `SnackbarErrorHandler` that will show a snack bar:

```kotlin
class SnackbarErrorHandler<in T>(private val view: View, private val text: (T) -> Option<String>) : ErrorHandler<T> {
    override fun showHideError(error: T): Option<DismissError> = text(error)
            .map {
                val snackbar = Snackbar.make(view, it, Snackbar.LENGTH_SHORT)
                        .apply { show() }
                val dismiss = { snackbar.dismiss() }
                dismiss
            }
}
```

## Images

We usually display some kind of a nice image with text when a user doesn’t have messages or contacts on the list:
 
```kotlin
val errorManager = ErrorManager(listOf(
        ViewWithImageErrorHandler<EmptyListException, Exception>(R.drawable.empty_list_image, R.string.empty_list_string),
        ShowScreenErrorHandler(frameLayout, { Option.Some(defaultErrorText(it)) })
))
```

And I think it wouldn't be hard for you to implement `ViewWithImageErrorHandler` by yourself ;)

## EditText

If you implement a login screen and a user types in a wrong password, you can manage those errors like this:

```kotlin
val errorManager = ErrorManager(listOf(
            castErrorHandler<WrongPasswordException, Exception> {
                passwordTextView.showHideError("Wrong password")
                fun() {
                    passwordTextView.showHideError()
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

You can imagine how `EditTextErrorView` could be implemented and reused in different contexts of the app. 

# Error message persistence

For actions (e.g. "Liking", "Sending", "Posting", "Creating", "Commenting", "Sharing", "Logging in"), use non-persistent error handlers like `SnackbarErrorHandler`, `EditTextErrorViewHandler`, or others that can be dismissed.
For stateful data (e.g. "List of comments", "List of posts", "Post view", "User details"), use persistent error handlers like `ShowScreenErrorHandler` or `ViewWithImageErrorHandler` that don’t disappear before the error is resolved.

[More about error message persistence](error-handling.md#errors-persistence)

# Summary

* Effective error handling shouldn't be hard to implement.
* By using the code that I proposed, you can implement very simple and very powerful error handling in your whole app fairly quickly.
* Begin with the simplest snack bar and error text view, then extend them to a more user-friendly solution.
* Reuse your error handling code as described in this tutorial.
* Implement error handling everywhere, where code-unrelated errors happen, e.g. network issues, wrong http status codes, problems with reading/writing files, no space left on the device issues.

# What's more

* [Kotlin and RxJava with extensions functions](kotlin-and-rxjava-with-extensions-functions.md)
* [Using schedulers while testing your code](using-schedulers-while-testing-your-code.md)
* [Errors... oh... those errors - Part 1](error-handling.md)


# Authors

* Jacek Marchwicki [jacek.marchwicki@gmail.com](mailto:jacek.marchwicki@gmail.com)