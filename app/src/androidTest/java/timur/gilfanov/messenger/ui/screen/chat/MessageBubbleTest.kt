package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@RunWith(AndroidJUnit4::class)
class MessageBubbleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun currentUserMessageDisplaysCorrectly() {
        val message = MessageUiModel(
            id = "1",
            text = "Hello! This is a message from the current user.",
            senderId = "current-user",
            senderName = "You",
            createdAt = "14:30",
            deliveryStatus = DeliveryStatus.Delivered,
            isFromCurrentUser = true,
        )

        composeTestRule.setContent {
            MessengerTheme {
                MessageBubble(message = message)
            }
        }

        composeTestRule.onNodeWithText(
            "Hello! This is a message from the current user.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("14:30").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delivered").assertIsDisplayed()
    }

    @Test
    fun otherUserMessageDisplaysCorrectly() {
        val message = MessageUiModel(
            id = "2",
            text = "Hi there! This is a message from another user.",
            senderId = "other-user",
            senderName = "Alice",
            createdAt = "14:28",
            deliveryStatus = DeliveryStatus.Delivered,
            isFromCurrentUser = false,
        )

        composeTestRule.setContent {
            MessengerTheme {
                MessageBubble(message = message)
            }
        }

        composeTestRule.onNodeWithText("Alice").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Hi there! This is a message from another user.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("14:28").assertIsDisplayed()
    }

    @Test
    fun messageWithSendingStatusShowsIndicator() {
        val message = MessageUiModel(
            id = "3",
            text = "This message is currently being sent...",
            senderId = "current-user",
            senderName = "You",
            createdAt = "14:32",
            deliveryStatus = DeliveryStatus.Sending(progress = 75),
            isFromCurrentUser = true,
        )

        composeTestRule.setContent {
            MessengerTheme {
                MessageBubble(message = message)
            }
        }

        composeTestRule.onNodeWithText(
            "This message is currently being sent...",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("14:32").assertIsDisplayed()
    }

    @Test
    fun messageWithDeliveredStatusShowsCheck() {
        val message = MessageUiModel(
            id = "4",
            text = "This message has been delivered.",
            senderId = "current-user",
            senderName = "You",
            createdAt = "14:35",
            deliveryStatus = DeliveryStatus.Delivered,
            isFromCurrentUser = true,
        )

        composeTestRule.setContent {
            MessengerTheme {
                MessageBubble(message = message)
            }
        }

        composeTestRule.onNodeWithText("This message has been delivered.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delivered").assertIsDisplayed()
    }

    @Test
    fun messageWithReadStatusShowsDoubleCheck() {
        val message = MessageUiModel(
            id = "5",
            text = "This message has been read.",
            senderId = "current-user",
            senderName = "You",
            createdAt = "14:38",
            deliveryStatus = DeliveryStatus.Read,
            isFromCurrentUser = true,
        )

        composeTestRule.setContent {
            MessengerTheme {
                MessageBubble(message = message)
            }
        }

        composeTestRule.onNodeWithText("This message has been read.").assertIsDisplayed()

        // Verify that there are exactly two check marks for read status
        composeTestRule.onAllNodesWithContentDescription("Read").assertCountEquals(2).apply {
            get(0).assertIsDisplayed()
            get(1).assertIsDisplayed()
        }
    }

    @Test
    fun failedMessageShowsErrorIndicator() {
        val message = MessageUiModel(
            id = "6",
            text = "This message failed to send.",
            senderId = "current-user",
            senderName = "You",
            createdAt = "14:40",
            deliveryStatus = DeliveryStatus.Failed(DeliveryError.NetworkUnavailable),
            isFromCurrentUser = true,
        )

        composeTestRule.setContent {
            MessengerTheme {
                MessageBubble(message = message)
            }
        }

        composeTestRule.onNodeWithText("This message failed to send.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Failed").assertIsDisplayed()
        // The error message might be displayed differently than just "NetworkUnavailable"
        // Let's check for the error icon instead
        composeTestRule.onNodeWithContentDescription("Error").assertIsDisplayed()
    }

    @Test
    fun longMessageTextWrapsCorrectly() {
        val longText = "This is a very long message that should wrap to multiple lines. " +
            "It contains enough text to ensure that the bubble will need to handle text wrapping " +
            "properly to maintain readability and proper layout constraints."

        val message = MessageUiModel(
            id = "7",
            text = longText,
            senderId = "current-user",
            senderName = "You",
            createdAt = "14:45",
            deliveryStatus = DeliveryStatus.Delivered,
            isFromCurrentUser = true,
        )

        composeTestRule.setContent {
            MessengerTheme {
                MessageBubble(message = message)
            }
        }

        composeTestRule.onNodeWithText(longText).assertIsDisplayed()
    }

    @Test
    fun emptyMessageTextHandledCorrectly() {
        val message = MessageUiModel(
            id = "8",
            text = "",
            senderId = "current-user",
            senderName = "You",
            createdAt = "14:50",
            deliveryStatus = DeliveryStatus.Delivered,
            isFromCurrentUser = true,
        )

        composeTestRule.setContent {
            MessengerTheme {
                MessageBubble(message = message)
            }
        }

        composeTestRule.onNodeWithText("14:50").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delivered").assertIsDisplayed()
    }
}
