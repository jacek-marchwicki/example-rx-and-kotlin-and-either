package com.jacekmarchwicki.locking

import java.util.concurrent.locks.ReentrantReadWriteLock

class OldDatabase {

    val readWriteLock = ReentrantReadWriteLock()

    var internalList: List<String> = listOf()
}