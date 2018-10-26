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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class InstantTestSchedulerTest {
    @Test
    fun `at start time is 0`() {
        val scheduler = InstantTestScheduler()

        assertEquals(0, scheduler.now(TimeUnit.SECONDS))
    }

    @Test
    fun `instant actions are scheduled instantly`() {
        val scheduler = InstantTestScheduler()
        var executions = 0

        scheduler.createWorker().schedule { executions += 1 }

        assertEquals(1, executions)
    }

    @Test
    fun `0 time actions are scheduled instantly`() {
        val scheduler = InstantTestScheduler()
        var executions = 0

        scheduler.createWorker().schedule({ executions += 1 }, 0, TimeUnit.SECONDS)

        assertEquals(1, executions)
    }

    @Test
    fun `delayed executions are executed after time`() {
        val scheduler = InstantTestScheduler()
        var executions = 0
        scheduler.createWorker().schedule({ executions += 1 }, 1L, TimeUnit.SECONDS)
        assertEquals(0, executions)

        scheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        assertEquals(1, executions)
    }

}