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

import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import org.funktionale.either.Either
import org.funktionale.option.Option
import java.util.concurrent.TimeUnit

sealed class ApiError {
    object NoNetwork : ApiError()
}

data class Post(val message: String)


class PostsDao {
    /* As this is not real dao, we fake a response */
    fun sendToApi(post: Post): Single<Either<ApiError, Post>> = Single.just(Either.right(post) as Either<ApiError, Post>)
            .delay(1, TimeUnit.SECONDS, Schedulers.computation())
}

class PostsPresenter(private val postsDao: PostsDao,
                     clickObservable: Observable<Unit>,
                     messageObservable: Observable<String>,
                     uiScheduler: Scheduler) {

    private val postToApi = clickObservable.withLatestFrom(messageObservable) { _, title -> Post(title) }
            .switchMap { postsDao.sendToApi(it).toObservable().subscribeOn(uiScheduler) }
            .replay(1)
            .refCount()

    val showErrorText: Observable<Option<ApiError>> = postToApi.map { it.left().toOption() }
    val showSuccessToast: Observable<Unit> = postToApi.filter { it.isRight() }.map { Unit }
}