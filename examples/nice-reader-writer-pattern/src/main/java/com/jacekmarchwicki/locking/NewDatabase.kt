package com.jacekmarchwicki.locking

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class NewDatabase {
    /**
     * Interface that will allow only reading from our database
     */
    interface Read {
        val list: List<String>
        fun itemExist(item: String): Boolean
    }

    /**
     * Interface that will allow reading and writing
     */
    interface Write : Read {
        /*
         * In kotlin we can override val with var so writes will be allowed
         */
        override var list: List<String>
        fun addItem(item: String)
    }

    /**
     * Actually implementation of database
     *
     * It should be private so none will accidentally call it without locking
     */
    private val implementation = object : Write {
        private var internalList: List<String> = listOf()
        override fun itemExist(item: String): Boolean = list.contains(item)

        override var list: List<String>
            get() = internalList
            set(value) {internalList = value}

        override fun addItem(item: String) {
            internalList = internalList.plus(item)
        }

    }

    /*
     * Locking mechanism
     *
     * TIP: we use `inline` function so kotlin will make this code faster without creation of objects
     */
    internal val readWriteLock = ReentrantReadWriteLock()
    inline internal fun <T> write(func: Write.() -> T): T = readWriteLock.write { func(implementation) }
    inline internal fun <T> read(func: Read.() -> T): T = readWriteLock.read { func(implementation) }
}