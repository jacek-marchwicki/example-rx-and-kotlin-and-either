package com.example.sleep

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.TestScheduler
import org.junit.Test
import java.io.IOException
import java.lang.AssertionError

class PresenterTest {

    private val uiScheduler = TestScheduler()
    private val computationScheduler = TestScheduler()
    private val service = mock<Service> {
        on { getDataFromServer() } doReturn Single.just(DataFromServer("data from the server"))
    }
    private fun create() = Presenter(service, computationScheduler, uiScheduler)

    @Test
    fun preconditions() {
        create()
    }

    @Test
    fun `when server return data, show it as a text`() {
        val text = create().text.test()

        computationScheduler.triggerActions()
        uiScheduler.triggerActions()
        text.assertLasValue("data from the server")
    }

    @Test
    fun `when data is loading, show empty text`() {
        service.stub {
            on { getDataFromServer()} doReturn Single.never()
        }
        val text = create().text.test()

        computationScheduler.triggerActions()
        uiScheduler.triggerActions()
        text.assertLasValue("")
    }

    @Test
    fun `when server return error, show error`() {
        service.stub {
            on { getDataFromServer()} doReturn Single.error(IOException())
        }
        val text = create().text.test()

        computationScheduler.triggerActions()
        uiScheduler.triggerActions()
        text.assertLasValue("Error")
    }

}

private fun <T> TestObserver<T>.assertLasValue(value: T): TestObserver<T> {
    if (valueCount() < 1) throw AssertionError("Expected at least one element")
    return assertValueAt(valueCount() - 1, value)
}

