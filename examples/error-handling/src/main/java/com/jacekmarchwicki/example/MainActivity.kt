package com.jacekmarchwicki.example

import android.app.ProgressDialog.show
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.error_layout.view.*
import org.funktionale.either.Either
import org.funktionale.option.Option
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val simpleErrorManager = ErrorManager<Errors>(listOf(
                ShowScreenErrorHandler(activity_main_content, { Option.Some(defaultErrorText(it)) })
        ))
        val snackbarErrorManager = ErrorManager<Errors>(listOf(
                SnackbarErorHandler(activity_main_content, { Option.Some(defaultErrorText(it)) })
        ))

        val errorManager = ErrorManager(listOf(
                ProgressErrorHandler(activity_main_content),
                ShowScreenErrorHandler(activity_main_content, { Option.Some(defaultErrorText(it)) })
        ))

        val advancedErrorManager = ErrorManager(listOf(
                ProgressErrorHandler(activity_main_content),
                ShowScreenErrorHandler(activity_main_content, {
                    Option.Some(when (it) {
                        Errors.NotFound -> "Github items not found"
                        else -> defaultErrorText(it)
                    })
                })
        ))

        val superAdvancedErrorManager = ErrorManager(listOf(
                castErrorHandler<Errors.NotYetLoaded, Errors> {
                    val animation = activity_main_button.animate().alpha(0f).apply { start() }
                    fun() {
                        animation.cancel()
                        activity_main_button.animate().alpha(1.0f).start()
                    }
                },
                ShowScreenErrorHandler(activity_main_content, {
                    Option.Some(when (it) {
                        Errors.NotFound -> "GitHub items not found"
                        else -> defaultErrorText(it)
                    })
                })
        ))

        activity_main_button.setOnClickListener {
            superAdvancedErrorManager.showError(Option.Some(Errors.NotYetLoaded))
            activity_main_text_view.text = ""
            execute({
                activity_main_text_view.text = it
                superAdvancedErrorManager.showError(Option.None)
            }, {
                activity_main_text_view.text = ""
                superAdvancedErrorManager.showError(Option.Some(it))
            })
        }
    }

    private fun execute(success: (String) -> Unit, failure: (Errors) -> Unit) {
        // Normally we should use some network library as retrofit but this is not a case of this
        // tutorial
        Thread({
            val result = doANetworkCall(applicationContext)
            runOnUiThread {
                result.fold({
                   failure(it)
                }, {
                    success(it)
                })
            }
        }).start()
    }
}

fun Context.defaultErrorText(error: Errors): String = when (error) {
    Errors.NotYetLoaded -> getString(R.string.errors_not_yet_loaded)
    Errors.NoInternetConnection -> getString(R.string.errors_no_internet_connection)
    Errors.NetworkError -> getString(R.string.errors_network_error)
    Errors.NotFound -> getString(R.string.errors_not_found)
    is Errors.SignInRequest -> getString(R.string.errors_sign_in_request)
    is Errors.UnknownError -> getString(R.string.errors_unknown_error, error.errorMessage)
}

inline fun <reified T : K, K> castErrorHandler(crossinline show: (T) -> DismissError) : ErrorHandler<K> = object : ErrorHandler<K> {
    override fun showError(error: K): Option<DismissError> = if (error is T) {
        Option.Some(show(error))
    } else {
        Option.None
    }

}

class ProgressErrorHandler(private val view: FrameLayout) : ErrorHandler<Errors> {
    override fun showError(error: Errors): Option<DismissError> = if (error is Errors.NotYetLoaded) {
        Option.None
    } else {
        val newView = LayoutInflater.from(view.context).inflate(R.layout.progress_layout, view, false)
        view.addView(newView)
        val dismiss = { view.removeView(newView) }
        Option.Some(dismiss)
    }

}

class SnackbarErorHandler<in T>(private val view: View, private val text: (T) -> Option<String>) : ErrorHandler<T> {
    override fun showError(error: T): Option<DismissError> = text(error)
            .map {
                val snackbar = Snackbar.make(view, it, Snackbar.LENGTH_SHORT)
                        .apply { show() }
                val dismiss = { snackbar.dismiss() }
                dismiss
            }
}

class ShowScreenErrorHandler<in T>(private val view: ViewGroup, private val text: (T) -> Option<String>) : ErrorHandler<T> {
    override fun showError(error: T): Option<DismissError> = text(error)
            .map {
                val newView = LayoutInflater.from(view.context).inflate(R.layout.error_layout, view, false)
                view.addView(newView)
                newView.error_layout_text.text = it
                fun () { view.removeView(newView) }
            }
}

interface ErrorHandler<in T> {
    fun showError(error: T): Option<DismissError>
}

typealias DismissError = () -> Unit

class ErrorManager<in T>(private val errorHandlers: List<ErrorHandler<T>>) {
    private var lastError: Option<DismissError> = Option.None

    fun showError(error: Option<T>) {
        lastError.let {
            if (it is Option.Some) {
                it.t()
            }
            lastError = Option.None
        }

        if (error is Option.Some) {
            var newLastError: Option<DismissError> = Option.None
            for (errorHandler in errorHandlers) {
                val result = errorHandler.showError(error.t)
                if (result is Option.Some) {
                    newLastError = result
                    break
                }
            }
            if (newLastError == Option.None) {
                throw RuntimeException("Not found error handler for ${error.t}")
            }
            lastError = newLastError
        }
    }
}

sealed class Errors {
    object NotYetLoaded : Errors()
    object NoInternetConnection : Errors()
    object NetworkError : Errors()
    object NotFound : Errors()
    data class SignInRequest(val url: String) : Errors()
    data class UnknownError(val errorMessage: String) : Errors()
}

fun doANetworkCall(context: Context): Either<Errors, String> = try {
    Thread.sleep(2000)
    val url = URL("https://api.github.com/users/octocat/orgs")
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetworkInfo
    if (activeNetwork == null || activeNetwork.isConnected) {
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.readTimeout = 3000
            connection.connectTimeout = 3000
            connection.doInput = true
            connection.connect()
            connection.inputStream.use {
                if (url.host != connection.url.host) {
                    Either.left(Errors.SignInRequest(connection.url.toExternalForm()))
                } else {
                    when (connection.responseCode) {
                        HttpsURLConnection.HTTP_NOT_FOUND -> Either.left(Errors.NotFound)
                        HttpsURLConnection.HTTP_OK -> it.bufferedReader().use { Either.right(it.readText()) as Either<Errors, String> }
                        else -> Either.left(Errors.UnknownError("HTTP error code: ${connection.responseCode}"))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    } else {
        Either.left(Errors.NoInternetConnection)
    }
} catch (e: IOException) {
    Either.left(Errors.NetworkError)
}