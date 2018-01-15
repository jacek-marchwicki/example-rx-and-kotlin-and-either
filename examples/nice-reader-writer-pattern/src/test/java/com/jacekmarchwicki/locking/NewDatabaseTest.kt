package com.jacekmarchwicki.locking

import org.junit.Assert.*
import org.junit.Test

class NewDatabaseTest {
    private val db = NewDatabase()

    @Test
    fun `check if at the starting point, the list is empty`() {
        assertEquals(listOf<String>(), db.read { list })
    }

    @Test
    fun `if an item is added, the list is expanded`() {
        db.write { addItem("something") }

        assertEquals(listOf("something"), db.read { list })
    }

    @Test
    fun `if two elements are added, list is contains both`() {
        db.write {
            addItem("something")
            addItem("else")
        }
        assertEquals(listOf("something", "else"), db.read { list })
    }

    @Test
    fun `if a data is written and read in one transaction, the data is filled`() {
        val dataRead = db.write {
            addItem("something")
            list
        }

        assertEquals(listOf("something"), dataRead)
    }
}