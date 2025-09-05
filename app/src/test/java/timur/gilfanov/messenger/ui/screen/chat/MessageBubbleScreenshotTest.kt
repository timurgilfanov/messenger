package timur.gilfanov.messenger.ui.screen.chat

import org.junit.Test
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.ui.screenshot.ScreenshotTestBase
import timur.gilfanov.messenger.util.generateProfileImageUrl

class MessageBubbleScreenshotTest : ScreenshotTestBase() {

    private val baseMessage = MessageUiModel(
        id = "1",
        text = "Hello, this is a test message",
        senderId = "user1",
        senderName = "Alice",
        createdAt = "10:24",
        deliveryStatus = DeliveryStatus.Read,
        isFromCurrentUser = true,
        senderPictureUrl = generateProfileImageUrl("Alice"),
    )

    @Test
    fun outgoing_read_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(message = baseMessage)
        }
    }

    @Test
    fun outgoing_read_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(message = baseMessage)
        }
    }

    @Test
    fun outgoing_delivered_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = baseMessage.copy(
                    deliveryStatus = DeliveryStatus.Delivered,
                ),
            )
        }
    }

    @Test
    fun outgoing_delivered_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(
                message = baseMessage.copy(
                    deliveryStatus = DeliveryStatus.Delivered,
                ),
            )
        }
    }

    @Test
    fun outgoing_sending_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = baseMessage.copy(
                    deliveryStatus = DeliveryStatus.Sending(50),
                ),
            )
        }
    }

    @Test
    fun outgoing_sending_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(
                message = baseMessage.copy(
                    deliveryStatus = DeliveryStatus.Sending(50),
                ),
            )
        }
    }

    @Test
    fun outgoing_failedNetwork_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = baseMessage.copy(
                    deliveryStatus = DeliveryStatus.Failed(DeliveryError.NetworkUnavailable),
                ),
            )
        }
    }

    @Test
    fun outgoing_failedNetwork_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(
                message = baseMessage.copy(
                    deliveryStatus = DeliveryStatus.Failed(DeliveryError.NetworkUnavailable),
                ),
            )
        }
    }

    @Test
    fun outgoing_failedPermission_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = baseMessage.copy(
                    deliveryStatus = DeliveryStatus.Failed(DeliveryError.RecipientBlocked),
                ),
            )
        }
    }

    @Test
    fun outgoing_failedPermission_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(
                message = baseMessage.copy(
                    deliveryStatus = DeliveryStatus.Failed(DeliveryError.RecipientBlocked),
                ),
            )
        }
    }

    @Test
    fun incoming_basic_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = baseMessage.copy(
                    isFromCurrentUser = false,
                    senderName = "Bob",
                ),
            )
        }
    }

    @Test
    fun incoming_basic_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(
                message = baseMessage.copy(
                    isFromCurrentUser = false,
                    senderName = "Bob",
                ),
            )
        }
    }

    @Test
    fun incoming_failed_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = MessageUiModel(
                    id = "2",
                    text = "Longer incoming message that should wrap across lines to test bubble " +
                        "width and timestamp wrapping behaviors.",
                    senderId = "u2",
                    senderName = "Alice",
                    createdAt = "22:05",
                    deliveryStatus = DeliveryStatus.Failed(DeliveryError.NetworkUnavailable),
                    isFromCurrentUser = false,
                    senderPictureUrl = generateProfileImageUrl("Alice"),
                ),
            )
        }
    }

    @Test
    fun incoming_failed_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(
                message = MessageUiModel(
                    id = "2",
                    text = "Longer incoming message that should wrap across lines to test bubble " +
                        "width and timestamp wrapping behaviors.",
                    senderId = "u2",
                    senderName = "Alice",
                    createdAt = "22:05",
                    deliveryStatus = DeliveryStatus.Failed(DeliveryError.NetworkUnavailable),
                    isFromCurrentUser = false,
                    senderPictureUrl = null,
                ),
            )
        }
    }

    @Test
    fun longText_outgoing_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "This is a very long message that should definitely wrap across " +
                        "multiple lines to test how the message bubble handles longer text " +
                        "content and whether the timestamp positioning works correctly in all cases.",
                ),
            )
        }
    }

    @Test
    fun longText_incoming_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "This is a very long message that should definitely wrap across " +
                        "multiple lines to test how the message bubble handles longer " +
                        "text content and whether the timestamp positioning works correctly.",
                    isFromCurrentUser = false,
                    senderName = "Very Long Sender Name That Might Wrap",
                ),
            )
        }
    }

    @Test
    fun shortText_outgoing_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "Hi!",
                ),
            )
        }
    }

    @Test
    fun shortText_incoming_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "Hi!",
                    isFromCurrentUser = false,
                    senderName = "Bob",
                ),
            )
        }
    }

    @Test
    fun longSenderName_incoming_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "Message from user with long name",
                    isFromCurrentUser = false,
                    senderName = "Dr. Alexandra Catherine Richardson-Williams III",
                ),
            )
        }
    }

    @Test
    fun multilineText_outgoing_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "Line one\nLine two\nLine three with some additional text",
                ),
            )
        }
    }

    @Test
    fun smallScreen_longText_light() {
        captureScreenshot(
            TestConfiguration(
                screenSize = ScreenSize.SMALL,
                theme = Theme.LIGHT,
            ),
        ) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "This message should adapt to small screen width appropriately",
                ),
            )
        }
    }

    @Test
    fun largeScreen_shortText_dark() {
        captureScreenshot(
            TestConfiguration(
                screenSize = ScreenSize.LARGE,
                theme = Theme.DARK,
            ),
        ) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "Short message on large screen",
                    isFromCurrentUser = false,
                    senderName = "Alice",
                ),
            )
        }
    }

    @Test
    fun rtl_outgoing_light() {
        captureScreenshot(
            TestConfiguration(
                theme = Theme.LIGHT,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            @Suppress("SpellCheckingInspection")
            MessageBubble(
                message = baseMessage.copy(
                    text = "مرحبا، هذه رسالة تجريبية",
                    deliveryStatus = DeliveryStatus.Read,
                ),
            )
        }
    }

    @Test
    fun rtl_incoming_dark() {
        captureScreenshot(
            TestConfiguration(
                theme = Theme.DARK,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            @Suppress("SpellCheckingInspection")
            MessageBubble(
                message = baseMessage.copy(
                    text = "مرحبا، هذه رسالة تجريبية من المرسل",
                    isFromCurrentUser = false,
                    senderName = "احمد محمد",
                ),
            )
        }
    }

    @Test
    fun rtl_failed_outgoing_light() {
        captureScreenshot(
            TestConfiguration(
                theme = Theme.LIGHT,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            @Suppress("SpellCheckingInspection")
            MessageBubble(
                message = baseMessage.copy(
                    text = "رسالة فاشلة في الإرسال",
                    deliveryStatus = DeliveryStatus.Failed(DeliveryError.NetworkUnavailable),
                ),
            )
        }
    }

    @Test
    fun emojiText_outgoing_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "Hello! 👋 How are you? 😊🎉",
                ),
            )
        }
    }

    @Test
    fun numbersAndSymbols_incoming_dark() {
        captureScreenshot(TestConfiguration(theme = Theme.DARK)) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "Meeting at 3:30 PM. Address: 123-456-7890. Cost: $25.99!",
                    isFromCurrentUser = false,
                    senderName = "Conference Organizer",
                ),
            )
        }
    }

    @Test
    fun longTimestamp_outgoing_light() {
        captureScreenshot(TestConfiguration(theme = Theme.LIGHT)) {
            MessageBubble(
                message = baseMessage.copy(
                    text = "Message with long timestamp",
                    createdAt = "Yesterday 11:59 PM",
                ),
            )
        }
    }
}
