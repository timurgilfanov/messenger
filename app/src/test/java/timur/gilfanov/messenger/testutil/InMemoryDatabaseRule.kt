package timur.gilfanov.messenger.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.dao.ParticipantDao

/**
 * JUnit Rule that provides an in-memory Room database for testing.
 * Automatically creates the database before each test and closes it after.
 *
 * Usage:
 * ```
 * @get:Rule
 * val databaseRule = InMemoryDatabaseRule()
 *
 * @Test
 * fun test() {
 *     val database = databaseRule.database
 *     val chatDao = databaseRule.chatDao
 *     // ... test logic
 * }
 * ```
 */
class InMemoryDatabaseRule : TestWatcher() {

    lateinit var database: MessengerDatabase
        private set

    val chatDao: ChatDao
        get() = database.chatDao()

    val messageDao: MessageDao
        get() = database.messageDao()

    val participantDao: ParticipantDao
        get() = database.participantDao()

    override fun starting(description: Description) {
        val context: Context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(context, MessengerDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    override fun finished(description: Description) {
        database.close()
    }
}
