/**
 * Copyright 2018 Jacek Marchwicki <jacek.marchwicki@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package com.example.scheduler

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.stub
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import org.funktionale.either.Either
import org.funktionale.option.Option
import org.junit.Test
import java.util.concurrent.TimeUnit

class PostsPresenterTest {

    private var postsDao = mock<PostsDao> {
        on { sendToApi(any())} doReturn Single.just(Either.left(ApiError.NoNetwork) as Either<ApiError, Post>)
    }
    private var clickObservable = PublishSubject.create<Unit>()
    private var messageObservable = BehaviorSubject.create<String>()

    private val uiScheduler = InstantTestScheduler()

    private fun createPresenter(): PostsPresenter = PostsPresenter(postsDao, clickObservable, messageObservable, uiScheduler)

    @Test
    fun `when user types message and clicks, a request is sent with correct title`() {
        createPresenter().showSuccessToast.test()

        messageObservable.onNext("krowa")
        clickObservable.onNext(Unit)

        verify(postsDao).sendToApi(Post("krowa"))
    }

    @Test
    fun `if posting fail, show an error message`() {
        val presenter = createPresenter()
        val errors = presenter.showErrorText.test()

        messageObservable.onNext("krowa")
        clickObservable.onNext(Unit)

        errors.assertValue(Option.Some(ApiError.NoNetwork))
    }

    @Test
    fun `if posting succeeds, show success toast`() {
        postsDao.stub {
            on { sendToApi(any())} doReturn Single.just(Either.right(Post("x")) as Either<ApiError, Post>)
        }
        val presenter = createPresenter()
        val success = presenter.showSuccessToast.test()

        messageObservable.onNext("krowa")
        clickObservable.onNext(Unit)

        success.assertValue(Unit)
    }

    @Test
    fun `example test for test scheduler`() {
        val timer = Observable.interval(1L, TimeUnit.SECONDS, uiScheduler).test()
        timer.assertValueCount(0)

        uiScheduler.advanceTimeBy(1L, TimeUnit.SECONDS)
        timer.assertValueCount(1)

        uiScheduler.advanceTimeBy(1L, TimeUnit.SECONDS)
        timer.assertValueCount(2)
    }
}