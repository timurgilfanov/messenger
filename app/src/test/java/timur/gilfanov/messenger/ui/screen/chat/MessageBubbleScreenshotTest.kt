package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.testutil.LocaleTimeZoneRule
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "en-rUS-w360dp-h640dp-xxhdpi")
class MessageBubbleScreenshotTest {

    private val compose = createComposeRule()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(LocaleTimeZoneRule())
        .around(compose)

    @Test
    fun outgoing_read_light() {
        val model = MessageUiModel(
            id = "1",
            text = "Hello, this is a short message",
            senderId = "me",
            senderName = "Me",
            createdAt = "10:24",
            deliveryStatus = DeliveryStatus.Read,
            isFromCurrentUser = true,
        )

        compose.setContent {
            // LTR, light theme, fixed width for determinism
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                MessengerTheme(darkTheme = false) {
                    Box(Modifier.width(320.dp).padding(16.dp)) {
                        MessageBubble(message = model)
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage()
    }

    @Test
    fun incoming_failed_dark() {
        val model = MessageUiModel(
            id = "2",
            text =
            "Longer incoming message that should wrap across lines to test bubble width and " +
                "timestamp wrapping behaviors.",
            senderId = "u2",
            senderName = "Alice",
            createdAt = "22:05",
            deliveryStatus = DeliveryStatus.Failed(DeliveryError.NetworkUnavailable),
            isFromCurrentUser = false,
        )

        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                MessengerTheme(darkTheme = true) {
                    Box(Modifier.width(320.dp).padding(16.dp)) {
                        MessageBubble(message = model)
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage()
    }
}
