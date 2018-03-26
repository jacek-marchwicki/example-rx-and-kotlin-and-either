# Nice reader/writer pattern

During the implementation of a chat app, I had to implement some kind of lock logic. 
Usually, code with locking and unlocking become hard to read.
If a business logic is complicated and you will add complicated locking, your code will become unreadable and unmaintainable.

# TL;DR;

Good example:
* [NewDatabaseTest](../examples/nice-reader-writer-pattern/src/test/java/com/jacekmarchwicki/locking/NewDatabaseTest.kt)
* [NewDatabase](../examples/nice-reader-writer-pattern/src/main/java/com/jacekmarchwicki/locking/NewDatabase.kt)

Compared to bad example:
* [OldDatabaseTest](../examples/nice-reader-writer-pattern/src/test/java/com/jacekmarchwicki/locking/OldDatabaseTest.kt)
* [OldDatabase](../examples/nice-reader-writer-pattern/src/main/java/com/jacekmarchwicki/locking/OldDatabase.kt)

# Example

Let's start with a database which looks like this:

```kotlin
class OldDatabase {

    val readWriteLock = ReentrantReadWriteLock()

    var internalList: List<String> = listOf()
}
```

and if you will add element then read it, your code will look like this:

```kotlin
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
```

It's very, very hard to read. Thankfully, Kotlin has built-in extension functions for `ReadWriteLock` so you can change your code to:

```kotlin
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
```

But still, the code is vulnerable to potential issues like (Use read lock instead of write, or even forget to use locking).
Also reading the code with something like this `db.internalList = db.internalList.plus("something")` is hard to understand.

So let's write better database with nicer API:

```kotlin
@Test
fun `if an item is added, the list is expanded`() {
    db.write { addItem("something") }

    assertEquals(listOf("something"), db.read { list })
}
```

or even:

```kotlin
@Test
fun `if data is written and read in one transaction, data is filled`() {
    val dataRead = db.write {
        addItem("something")
        addItem("something else")
        list
    }

    assertEquals(listOf("something", "something else"), dataRead)
}
```

and code like `db.read {addItem("something)}` will not even compile.

Let's do it better:

```kotlin
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
```

# Conclusions
1. Good patterns makes complicated logic more understandable,
2. If your code is hard to read you need to rewrite it,
3. Especially when you write something hard try to refactor your code to black boxes that implement only part of your logic.

# Authors
Authors:
* Jacek Marchwicki [jacek.marchwicki@gmail.com](mailto:jacek.marchwicki@gmail.com)

Corrected by:
* Piotr Mądry