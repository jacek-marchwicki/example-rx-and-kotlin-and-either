package com.jacekmarchwicki.locking

import org.junit.Assert.*
import org.junit.Test
import kotlin.concurrent.read
import kotlin.concurrent.write

class OldDatabaseTest {
    private val db = OldDatabase()

    @Test
    fun `check if at the starting point, the list is empty`() {
        val lock = db.readWriteLock.readLock()
        val list = try {
            db.internalList
        } finally {
            lock.unlock()
        }
        assertEquals(listOf<String>(), list)
    }

    @Test
    fun `if an item is added, the list is expanded`() {
        val lock1 = db.readWriteLock.writeLock()
        try {
            db.internalList = db.internalList.plus("something")
        } finally {
            lock1.unlock()
        }

        val lock2 = db.readWriteLock.readLock()
        val list = try {
            db.internalList
        } finally {
            lock2.unlock()
        }

        assertEquals(listOf("something"), list )
    }

    @Test
    fun `if an item is added, the list is expanded - nicer`() {
        db.readWriteLock.write {
            db.internalList = db.internalList.plus("something")
        }

        val list = db.readWriteLock.read {
            db.internalList
        }

        assertEquals(listOf("something"), list )
    }
}