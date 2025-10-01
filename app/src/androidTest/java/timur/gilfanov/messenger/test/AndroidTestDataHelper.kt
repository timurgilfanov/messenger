package timur.gilfanov.messenger.test

import androidx.room.withTransaction
import java.util.UUID
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.entity.ChatEntity
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.entity.MessageEntity
import timur.gilfanov.messenger.data.source.local.database.entity.MessageType
import timur.gilfanov.messenger.data.source.local.database.entity.ParticipantEntity
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage

object AndroidTestDataHelper {

    enum class DataScenario {
        EMPTY,
        NON_EMPTY,
    }

    // Test user and chat IDs - reusing same constants for consistency with unit tests
    const val USER_ID = "550e8400-e29b-41d4-a716-446655440000"
    const val ALICE_USER_ID = "550e8400-e29b-41d4-a716-446655440001"
    const val BOB_USER_ID = "550e8400-e29b-41d4-a716-446655440005"
    const val ALICE_CHAT_ID = "550e8400-e29b-41d4-a716-446655440002"
    const val BOB_CHAT_ID = "550e8400-e29b-41d4-a716-446655440006"
    const val ALICE_MESSAGE_1_ID = "550e8400-e29b-41d4-a716-446655440003"
    const val ALICE_MESSAGE_2_ID = "550e8400-e29b-41d4-a716-446655440004"
    const val BOB_MESSAGE_1_ID = "550e8400-e29b-41d4-a716-446655440007"

    const val ALICE_TEXT_1 = "Hello! 👋"
    const val ALICE_TEXT_2 = "How are you doing today?"
    const val BOB_TEXT_1 = "Hey there! How's the project going?"

    // Fixed timestamps for reproducible tests
    val FIXED_TIMESTAMP = Instant.fromEpochMilliseconds(1672531200000L) // 2023-01-01 00:00:00 UTC
    val ALICE_JOIN_TIME = Instant.fromEpochMilliseconds(1672531100000L) // 2023-01-01 00:01:40 UTC
    val BOB_JOIN_TIME = Instant.fromEpochMilliseconds(1672531150000L) // 2023-01-01 00:02:30 UTC
    val MESSAGE_1_TIME = Instant.fromEpochMilliseconds(1672531300000L) // 2023-01-01 00:05:00 UTC
    val MESSAGE_2_TIME = Instant.fromEpochMilliseconds(1672531400000L) // 2023-01-01 00:06:40 UTC
    val MESSAGE_3_TIME = Instant.fromEpochMilliseconds(1672531500000L) // 2023-01-01 00:08:20 UTC

    val currentUserId = ParticipantId(UUID.fromString(USER_ID))
    val aliceUserId = ParticipantId(UUID.fromString(ALICE_USER_ID))
    val bobUserId = ParticipantId(UUID.fromString(BOB_USER_ID))
    val aliceChatId = ChatId(UUID.fromString(ALICE_CHAT_ID))
    val bobChatId = ChatId(UUID.fromString(BOB_CHAT_ID))

    val currentUser = Participant(
        id = currentUserId,
        name = "You",
        pictureUrl = null,
        joinedAt = FIXED_TIMESTAMP,
        onlineAt = FIXED_TIMESTAMP,
        isAdmin = false,
        isModerator = false,
    )

    val aliceUser = Participant(
        id = aliceUserId,
        name = "Alice",
        pictureUrl = null,
        joinedAt = ALICE_JOIN_TIME,
        onlineAt = ALICE_JOIN_TIME,
        isAdmin = false,
        isModerator = false,
    )

    val bobUser = Participant(
        id = bobUserId,
        name = "Bob",
        pictureUrl = null,
        joinedAt = BOB_JOIN_TIME,
        onlineAt = BOB_JOIN_TIME,
        isAdmin = false,
        isModerator = false,
    )

    // Create complete Alice chat with all messages that will be in the database
    val aliceChat = Chat(
        id = aliceChatId,
        participants = persistentSetOf(currentUser, aliceUser),
        name = "Alice",
        pictureUrl = null,
        rules = persistentSetOf(),
        unreadMessagesCount = 0,
        lastReadMessageId = null,
        messages = persistentListOf(
            TextMessage(
                id = MessageId(UUID.fromString(ALICE_MESSAGE_1_ID)),
                text = ALICE_TEXT_1,
                parentId = null,
                sender = aliceUser,
                recipient = aliceChatId,
                createdAt = MESSAGE_1_TIME,
                sentAt = MESSAGE_1_TIME,
                deliveredAt = MESSAGE_1_TIME,
                editedAt = null,
                deliveryStatus = DeliveryStatus.Read,
            ),
            TextMessage(
                id = MessageId(UUID.fromString(ALICE_MESSAGE_2_ID)),
                text = ALICE_TEXT_2,
                parentId = null,
                sender = currentUser,
                recipient = aliceChatId,
                createdAt = MESSAGE_2_TIME,
                sentAt = MESSAGE_2_TIME,
                deliveredAt = MESSAGE_2_TIME,
                editedAt = null,
                deliveryStatus = DeliveryStatus.Delivered,
            ),
        ),
    )

    val bobChat = Chat(
        id = bobChatId,
        participants = persistentSetOf(currentUser, bobUser),
        name = "Bob",
        pictureUrl = null,
        rules = persistentSetOf(),
        unreadMessagesCount = 1,
        lastReadMessageId = null,
        messages = persistentListOf(
            TextMessage(
                id = MessageId(UUID.fromString(BOB_MESSAGE_1_ID)),
                text = BOB_TEXT_1,
                parentId = null,
                sender = bobUser,
                recipient = bobChatId,
                createdAt = MESSAGE_3_TIME,
                sentAt = MESSAGE_3_TIME,
                deliveredAt = MESSAGE_3_TIME,
                editedAt = null,
                deliveryStatus = DeliveryStatus.Delivered,
            ),
        ),
    )

    suspend fun prepopulateDatabase(database: MessengerDatabase) {
        database.withTransaction {
            insertParticipants(database)
            val chatDao = database.chatDao()
            insertChats(chatDao)
            insertChatParticipantCrossRef(chatDao)

            insertMessages(database)
        }
    }

    private suspend fun insertChats(chatDao: ChatDao) {
        chatDao.insertChat(
            ChatEntity(
                id = ALICE_CHAT_ID,
                name = "Alice",
                pictureUrl = null,
                rules = "[]", // Empty JSON array for rules
                unreadMessagesCount = 0,
                lastReadMessageId = null,
                updatedAt = FIXED_TIMESTAMP,
            ),
        )
        chatDao.insertChat(
            ChatEntity(
                id = BOB_CHAT_ID,
                name = "Bob",
                pictureUrl = null,
                rules = "[]", // Empty JSON array for rules
                unreadMessagesCount = 1,
                lastReadMessageId = null,
                updatedAt = FIXED_TIMESTAMP,
            ),
        )
    }

    private suspend fun insertParticipants(database: MessengerDatabase) {
        val participantDao = database.participantDao()
        participantDao.insertParticipant(
            ParticipantEntity(
                id = USER_ID,
                name = "You",
                pictureUrl = null,
                onlineAt = FIXED_TIMESTAMP,
            ),
        )
        participantDao.insertParticipant(
            ParticipantEntity(
                id = ALICE_USER_ID,
                name = "Alice",
                pictureUrl = null,
                onlineAt = ALICE_JOIN_TIME,
            ),
        )
        participantDao.insertParticipant(
            ParticipantEntity(
                id = BOB_USER_ID,
                name = "Bob",
                pictureUrl = null,
                onlineAt = BOB_JOIN_TIME,
            ),
        )
    }

    private suspend fun insertChatParticipantCrossRef(chatDao: ChatDao) {
        // Insert Alice chat participants with chat-specific properties
        chatDao.insertChatParticipantCrossRef(
            ChatParticipantCrossRef(
                chatId = ALICE_CHAT_ID,
                participantId = USER_ID,
                joinedAt = FIXED_TIMESTAMP,
                isAdmin = false,
                isModerator = false,
            ),
        )
        chatDao.insertChatParticipantCrossRef(
            ChatParticipantCrossRef(
                chatId = ALICE_CHAT_ID,
                participantId = ALICE_USER_ID,
                joinedAt = ALICE_JOIN_TIME,
                isAdmin = false,
                isModerator = false,
            ),
        )

        // Insert Bob chat participants with chat-specific properties
        chatDao.insertChatParticipantCrossRef(
            ChatParticipantCrossRef(
                chatId = BOB_CHAT_ID,
                participantId = USER_ID,
                joinedAt = FIXED_TIMESTAMP,
                isAdmin = false,
                isModerator = false,
            ),
        )
        chatDao.insertChatParticipantCrossRef(
            ChatParticipantCrossRef(
                chatId = BOB_CHAT_ID,
                participantId = BOB_USER_ID,
                joinedAt = BOB_JOIN_TIME,
                isAdmin = false,
                isModerator = false,
            ),
        )
    }

    private suspend fun insertMessages(database: MessengerDatabase): Long {
        val messageDao = database.messageDao()
        val path = """timur.gilfanov.messenger.data.source.local.database.mapper"""
        messageDao.insertMessage(
            MessageEntity(
                id = ALICE_MESSAGE_1_ID,
                chatId = ALICE_CHAT_ID,
                senderId = ALICE_USER_ID,
                parentId = null,
                type = MessageType.TEXT,
                text = ALICE_TEXT_1,
                imageUrl = null,
                deliveryStatus = """{"type":"$path.DeliveryStatusDto.Read"}""",
                createdAt = MESSAGE_1_TIME,
                sentAt = MESSAGE_1_TIME,
                deliveredAt = MESSAGE_1_TIME,
                editedAt = null,
            ),
        )
        messageDao.insertMessage(
            MessageEntity(
                id = ALICE_MESSAGE_2_ID,
                chatId = ALICE_CHAT_ID,
                senderId = USER_ID,
                parentId = null,
                type = MessageType.TEXT,
                text = ALICE_TEXT_2,
                imageUrl = null,
                deliveryStatus = """{"type":"$path.DeliveryStatusDto.Delivered"}""",
                createdAt = MESSAGE_2_TIME,
                sentAt = MESSAGE_2_TIME,
                deliveredAt = MESSAGE_2_TIME,
                editedAt = null,
            ),
        )
        return messageDao.insertMessage(
            MessageEntity(
                id = BOB_MESSAGE_1_ID,
                chatId = BOB_CHAT_ID,
                senderId = BOB_USER_ID,
                parentId = null,
                type = MessageType.TEXT,
                text = BOB_TEXT_1,
                imageUrl = null,
                deliveryStatus = """{"type":"$path.DeliveryStatusDto.Delivered"}""",
                createdAt = MESSAGE_3_TIME,
                sentAt = MESSAGE_3_TIME,
                deliveredAt = MESSAGE_3_TIME,
                editedAt = null,
            ),
        )
    }

    fun prepopulateRemoteDataSource(remoteDataSourceFake: RemoteDataSourceFake) {
        remoteDataSourceFake.addChat(aliceChat)
        remoteDataSourceFake.addChat(bobChat)
    }
}
