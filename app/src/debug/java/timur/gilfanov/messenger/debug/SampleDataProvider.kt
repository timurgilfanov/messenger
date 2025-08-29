package timur.gilfanov.messenger.debug

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage

@Suppress("MagicNumber", "TooManyFunctions") // It's fine in this debug/test data generation context
@Singleton
class SampleDataProvider @Inject constructor() {

    companion object {
        // Current user ID - matches the one in MainActivity and test helpers
        private const val CURRENT_USER_ID = "550e8400-e29b-41d4-a716-446655440000"

        // Base time for message generation (going backwards from now)
        private val baseTime = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    }

    private val firstNames = listOf(
        "Emma", "Liam", "Olivia", "Noah", "Ava", "Ethan", "Sophia", "Mason",
        "Isabella", "William", "Mia", "James", "Charlotte", "Benjamin", "Amelia",
        "Lucas", "Harper", "Henry", "Evelyn", "Alexander", "Abigail", "Sebastian",
    )

    private val lastNames = listOf(
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller",
        "Davis", "Rodriguez", "Martinez", "Lopez", "Wilson", "Anderson", "Taylor",
        "Thomas", "Hernandez", "Moore", "Martin", "Jackson", "Thompson",
    )

    private val groupNames = listOf(
        "Project Team Alpha", "Book Club", "Gaming Squad", "Study Group",
        "Fitness Buddies", "Travel Planning", "Movie Night Crew", "Food Lovers",
        "Weekend Warriors", "Tech Talk", "Family Group", "Running Club",
        "Photography Club", "Music Lovers", "Hiking Adventures", "Coffee Chat",
    )

    private val casualMessages = listOf(
        "Hey! How's it going?",
        "What's up?",
        "How was your day?",
        "Thanks for yesterday!",
        "Running a bit late, be there soon",
        "Did you see that?",
        "That's awesome! 🎉",
        "Sounds good to me",
        "Let me know when you're free",
        "Perfect timing!",
        "I'll be right there",
        "Thanks so much!",
        "You're the best 😊",
        "Can't wait!",
        "Absolutely!",
        "No worries at all",
    )

    private val workMessages = listOf(
        "Could you review this when you get a chance?",
        "Meeting moved to 3 PM",
        "Great work on the presentation!",
        "Let's sync up tomorrow morning",
        "I'll send the updated files shortly",
        "The client loved the proposal",
        "Can we schedule a quick call?",
        "I'll handle the deployment",
        "Code review is complete",
        "Ready for the demo?",
        "All tests are passing ✅",
        "Deployment went smoothly",
        "Nice catch on that bug!",
        "I'll update the documentation",
    )

    private val socialMessages = listOf(
        "Want to grab coffee later?",
        "How about dinner tomorrow?",
        "The concert was amazing!",
        "Did you watch the game?",
        "Check out this funny video",
        "Happy birthday! 🎂",
        "Congratulations! 🎉",
        "Hope you feel better soon",
        "Safe travels!",
        "Welcome back!",
        "Miss you already",
        "Can't believe it's Friday!",
        "Weekend plans?",
        "See you at the party!",
    )

    private val edgeCaseMessages = listOf(
        "🎉🎂🎈 Happy Birthday! Hope your special day is filled with happiness and cake! 🍰✨",
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor " +
            "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis " +
            "nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.",
        "Hello 世界! 🌍 こんにちは Здравствуй مرحبا नमस्ते",
        "🚀🌟✨🎯⚡️🔥💎🏆🎪🎭🎨🎵🎸🏀⚽️🏊‍♀️🚴‍♂️🧘‍♀️",
        "Check out this link: https://www.example.com/very/long/url/path/that/goes/on/and/on/and" +
            "/contains/many/parameters?param1=value1&param2=value2",
        "", // Empty message
        "a", // Single character
        "This is a message with\nmultiple\nlines\nto test formatting",
        "Testing special characters: !@#$%^&*()_+-=[]{}|;':\",./<>?",
        "𝕿𝖍𝖎𝖘 𝖎𝖘 𝖚𝖓𝖎𝖈𝖔𝖉𝖊 𝖒𝖆𝖙𝖍 𝖙𝖊𝖝𝖙",
    )

    // Current user participant
    private val currentUser = Participant(
        id = ParticipantId(UUID.fromString(CURRENT_USER_ID)),
        name = "You",
        pictureUrl = null,
        joinedAt = baseTime - 30.days,
        onlineAt = baseTime,
        isAdmin = false,
        isModerator = false,
    )

    /**
     * Generate chats based on the provided configuration
     */
    fun generateChats(config: DataGenerationConfig): List<Chat> = when (config.scenario) {
        DataScenario.EMPTY -> emptyList()
        DataScenario.MINIMAL -> generateMinimalChats(config)
        DataScenario.STANDARD -> generateStandardChats(config)
        DataScenario.HEAVY -> generateHeavyChats(config)
        DataScenario.EDGE_CASES -> generateEdgeCaseChats(config)
        DataScenario.DEMO -> generateDemoChats(config)
    }

    private fun generateMinimalChats(config: DataGenerationConfig): List<Chat> =
        generateChatsWithConfig(config, useSimpleNames = true)

    private fun generateStandardChats(config: DataGenerationConfig): List<Chat> =
        generateChatsWithConfig(config, useSimpleNames = false)

    private fun generateHeavyChats(config: DataGenerationConfig): List<Chat> =
        generateChatsWithConfig(config, useSimpleNames = false, includeInactiveChats = true)

    private fun generateEdgeCaseChats(config: DataGenerationConfig): List<Chat> {
        val chats = mutableListOf<Chat>()

        // Add some normal chats
        chats.addAll(generateChatsWithConfig(config.copy(chatCount = config.chatCount - 3)))

        // Add edge case chats
        chats.add(createEmptyChat())
        chats.add(createUnicodeChat())
        chats.add(createLongMessageChat())

        return chats
    }

    private fun generateDemoChats(config: DataGenerationConfig): List<Chat> {
        val demoChats = mutableListOf<Chat>()

        // Alice - Close friend
        demoChats.add(
            createDemoChat(
                name = "Alice Chen",
                messages = listOf(
                    "Hey! Are we still on for coffee tomorrow?",
                    "I found this great new place downtown",
                    "Can't wait to catch up! ☕️",
                ),
            ),
        )

        // Work Team
        demoChats.add(
            createDemoGroupChat(
                name = "Mobile Team",
                participants = listOf("Alex", "Sarah", "Mike"),
                messages = listOf(
                    "Great work on the new feature!",
                    "Code review looks good 👍",
                    "Ready for the demo tomorrow?",
                ),
            ),
        )

        // Family
        demoChats.add(
            createDemoChat(
                name = "Mom",
                messages = listOf(
                    "Don't forget about Sunday dinner!",
                    "Your dad is making his famous pasta",
                    "Love you! ❤️",
                ),
            ),
        )

        // Add more standard chats to reach config count
        val remaining = config.chatCount - demoChats.size
        if (remaining > 0) {
            demoChats.addAll(
                generateChatsWithConfig(
                    config.copy(chatCount = remaining),
                    useSimpleNames = true,
                ),
            )
        }

        return demoChats
    }

    private fun generateChatsWithConfig(
        config: DataGenerationConfig,
        useSimpleNames: Boolean = false,
        includeInactiveChats: Boolean = false,
    ): List<Chat> = List(config.chatCount) { index ->
        val isGroup = index % 4 == 0 // 25% are group chats
        val participants = if (isGroup) {
            generateGroupParticipants(3 + Random.nextInt(4)) // 3-6 participants
        } else {
            generateOneOnOneParticipants(useSimpleNames)
        }

        val chatName = if (isGroup) {
            groupNames.random()
        } else {
            participants.first { it.id != currentUser.id }.name
        }

        val messageCount = config.getRandomMessageCount()
        val isInactive = includeInactiveChats && Random.nextDouble() < 0.3 // 30% inactive

        val messages = if (isInactive) {
            // Inactive chats have older messages
            generateMessages(participants, messageCount, 7.days, 30.days)
        } else {
            generateMessages(participants, messageCount)
        }

        val unreadCount = when {
            messages.isEmpty() -> 0
            Random.nextDouble() < 0.3 -> Random.nextInt(1, 6) // 30% have unread
            else -> 0
        }

        Chat(
            id = ChatId(UUID.randomUUID()),
            name = chatName,
            pictureUrl = null,
            participants = participants.toPersistentSet(),
            rules = persistentSetOf(),
            messages = messages.toPersistentList(),
            unreadMessagesCount = unreadCount,
            lastReadMessageId = if (unreadCount > 0 && messages.isNotEmpty()) {
                messages[messages.size - unreadCount - 1].id
            } else {
                null
            },
        )
    }

    private fun generateOneOnOneParticipants(useSimpleNames: Boolean = false): List<Participant> {
        val otherUser = if (useSimpleNames) {
            generateParticipant(firstNames.random())
        } else {
            generateParticipant("${firstNames.random()} ${lastNames.random()}")
        }
        return listOf(currentUser, otherUser)
    }

    private fun generateGroupParticipants(count: Int): List<Participant> {
        val participants = mutableListOf(currentUser)
        repeat(count - 1) {
            participants.add(generateParticipant("${firstNames.random()} ${lastNames.random()}"))
        }
        return participants
    }

    private fun generateParticipant(name: String): Participant = Participant(
        id = ParticipantId(UUID.randomUUID()),
        name = name,
        pictureUrl = null,
        joinedAt = baseTime - Random.nextInt(1, 365).days,
        onlineAt = baseTime - Random.nextInt(1, 24).hours,
        isAdmin = false,
        isModerator = false,
    )

    private fun generateMessages(
        participants: List<Participant>,
        count: Int,
        startTimeOffset: kotlin.time.Duration = 0.minutes,
        endTimeOffset: kotlin.time.Duration = 7.days,
    ): List<Message> {
        if (count == 0) return emptyList()

        val messages = mutableListOf<Message>()
        val timeStep = endTimeOffset / count

        repeat(count) { index ->
            val messageTime = baseTime - endTimeOffset + (timeStep * index) + startTimeOffset
            val sender = participants.random()
            val isFromCurrentUser = sender.id == currentUser.id

            val messageText = getRandomMessage(participants.size > 2)

            val deliveryStatus = when {
                isFromCurrentUser -> when (Random.nextInt(4)) {
                    0 -> DeliveryStatus.Sending(Random.nextInt(100))
                    1 -> DeliveryStatus.Delivered
                    2 -> DeliveryStatus.Read
                    else -> DeliveryStatus.Read
                }
                else -> DeliveryStatus.Read
            }

            messages.add(
                TextMessage(
                    id = MessageId(UUID.randomUUID()),
                    text = messageText,
                    parentId = null,
                    sender = sender,
                    recipient = ChatId(UUID.randomUUID()), // Will be set properly by caller
                    createdAt = messageTime,
                    sentAt = messageTime,
                    deliveredAt = if (deliveryStatus !is DeliveryStatus.Sending) {
                        messageTime
                    } else {
                        null
                    },
                    editedAt = if (Random.nextDouble() < 0.1) messageTime + 5.minutes else null,
                    deliveryStatus = deliveryStatus,
                ),
            )
        }

        return messages.sortedBy { it.createdAt }
    }

    private fun getRandomMessage(isGroup: Boolean): String = when (Random.nextInt(3)) {
        0 -> casualMessages.random()
        1 -> if (isGroup) workMessages.random() else socialMessages.random()
        else -> casualMessages.random()
    }

    private fun createEmptyChat(): Chat = Chat(
        id = ChatId(UUID.randomUUID()),
        name = "Empty Chat",
        pictureUrl = null,
        participants = setOf(
            currentUser,
            generateParticipant("Silent Bob"),
        ).toPersistentSet(),
        rules = persistentSetOf(),
        messages = persistentListOf(),
        unreadMessagesCount = 0,
        lastReadMessageId = null,
    )

    private fun createUnicodeChat(): Chat {
        val unicodeParticipant = generateParticipant("李小明 🌸")
        val messages = listOf(
            TextMessage(
                id = MessageId(UUID.randomUUID()),
                text = "Hello 世界! 🌍 こんにちは",
                parentId = null,
                sender = unicodeParticipant,
                recipient = ChatId(UUID.randomUUID()),
                createdAt = baseTime - 1.hours,
                sentAt = baseTime - 1.hours,
                deliveredAt = baseTime - 1.hours,
                editedAt = null,
                deliveryStatus = DeliveryStatus.Read,
            ),
        )

        return Chat(
            id = ChatId(UUID.randomUUID()),
            name = "Unicode Test",
            pictureUrl = null,
            participants = setOf(currentUser, unicodeParticipant).toPersistentSet(),
            rules = persistentSetOf(),
            messages = messages.toPersistentList(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )
    }

    private fun createLongMessageChat(): Chat {
        val participant = generateParticipant("Verbose Victor")
        val longMessage = edgeCaseMessages[1] // The Lorem ipsum text

        val messages = listOf(
            TextMessage(
                id = MessageId(UUID.randomUUID()),
                text = longMessage,
                parentId = null,
                sender = participant,
                recipient = ChatId(UUID.randomUUID()),
                createdAt = baseTime - 2.hours,
                sentAt = baseTime - 2.hours,
                deliveredAt = baseTime - 2.hours,
                editedAt = null,
                deliveryStatus = DeliveryStatus.Read,
            ),
        )

        return Chat(
            id = ChatId(UUID.randomUUID()),
            name = "Long Messages",
            pictureUrl = null,
            participants = setOf(currentUser, participant).toPersistentSet(),
            rules = persistentSetOf(),
            messages = messages.toPersistentList(),
            unreadMessagesCount = 1,
            lastReadMessageId = null,
        )
    }

    private fun createDemoChat(name: String, messages: List<String>): Chat {
        val participant = generateParticipant(name)
        val chatMessages = messages.mapIndexed { index, text ->
            val isFromCurrentUser = index % 2 == 1
            TextMessage(
                id = MessageId(UUID.randomUUID()),
                text = text,
                parentId = null,
                sender = if (isFromCurrentUser) currentUser else participant,
                recipient = ChatId(UUID.randomUUID()),
                createdAt = baseTime - (messages.size - index).hours,
                sentAt = baseTime - (messages.size - index).hours,
                deliveredAt = baseTime - (messages.size - index).hours,
                editedAt = null,
                deliveryStatus = DeliveryStatus.Read,
            )
        }

        return Chat(
            id = ChatId(UUID.randomUUID()),
            name = name,
            pictureUrl = null,
            participants = setOf(currentUser, participant).toPersistentSet(),
            rules = persistentSetOf(),
            messages = chatMessages.toPersistentList(),
            unreadMessagesCount = if (messages.isNotEmpty()) 1 else 0,
            lastReadMessageId = null,
        )
    }

    private fun createDemoGroupChat(
        name: String,
        participants: List<String>,
        messages: List<String>,
    ): Chat {
        val allParticipants = mutableListOf(currentUser)
        allParticipants.addAll(participants.map { generateParticipant(it) })

        val chatMessages = messages.mapIndexed { index, text ->
            val sender = allParticipants[index % allParticipants.size]
            TextMessage(
                id = MessageId(UUID.randomUUID()),
                text = text,
                parentId = null,
                sender = sender,
                recipient = ChatId(UUID.randomUUID()),
                createdAt = baseTime - (messages.size - index).hours,
                sentAt = baseTime - (messages.size - index).hours,
                deliveredAt = baseTime - (messages.size - index).hours,
                editedAt = null,
                deliveryStatus = DeliveryStatus.Read,
            )
        }

        return Chat(
            id = ChatId(UUID.randomUUID()),
            name = name,
            pictureUrl = null,
            participants = allParticipants.toPersistentSet(),
            rules = persistentSetOf(),
            messages = chatMessages.toPersistentList(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )
    }

    /**
     * Generate a single message for auto-activity simulation
     */
    fun generateSingleMessage(chat: Chat): Message {
        val sender = chat.participants.filter { it.id != currentUser.id }.randomOrNull()
            ?: chat.participants.random()

        val messageText = getRandomMessage(chat.participants.size > 2)
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

        return TextMessage(
            id = MessageId(UUID.randomUUID()),
            text = messageText,
            parentId = null,
            sender = sender,
            recipient = chat.id,
            createdAt = now,
            sentAt = now,
            deliveredAt = now,
            editedAt = null,
            deliveryStatus = DeliveryStatus.Delivered,
        )
    }
}
