package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.ui.screenshot.ScreenshotTestBase

class MessageInputScreenshotTest : ScreenshotTestBase() {

    @Test
    fun empty_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            MessageInput(
                state = TextFieldState(""),
                isSending = false,
            )
        }
    }

    @Test
    fun empty_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            MessageInput(
                state = TextFieldState(""),
                isSending = false,
            )
        }
    }

    @Test
    fun withShortText_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            MessageInput(
                state = TextFieldState("Hello!"),
                isSending = false,
            )
        }
    }

    @Test
    fun withShortText_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            MessageInput(
                state = TextFieldState("Hello!"),
                isSending = false,
            )
        }
    }

    @Test
    fun withLongText_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            MessageInput(
                state = TextFieldState(
                    "This is a much longer message that should wrap to multiple lines " +
                        "to test how the MessageInput component handles longer text input gracefully",
                ),
                isSending = false,
            )
        }
    }

    @Test
    fun withLongText_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            MessageInput(
                state = TextFieldState(
                    "This is a much longer message that should wrap to multiple lines " +
                        "to test how the MessageInput component handles longer text input gracefully",
                ),
                isSending = false,
            )
        }
    }

    @Test
    fun errorStateTooLong_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            MessageInput(
                state = TextFieldState("This message is too long".repeat(50)),
                textValidationError = TextValidationError.TooLong(2000),
                isSending = false,
            )
        }
    }

    @Test
    fun errorStateTooLong_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            MessageInput(
                state = TextFieldState("This message is too long".repeat(50)),
                textValidationError = TextValidationError.TooLong(2000),
                isSending = false,
            )
        }
    }

    @Test
    fun errorStateEmpty_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            MessageInput(
                state = TextFieldState("Some invalid text"),
                textValidationError = TextValidationError.Empty,
                isSending = false,
            )
        }
    }

    @Test
    fun errorStateEmpty_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            MessageInput(
                state = TextFieldState("Some invalid text"),
                textValidationError = TextValidationError.Empty,
                isSending = false,
            )
        }
    }

    @Test
    fun sending_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            MessageInput(
                state = TextFieldState("Sending this message..."),
                isSending = true,
            )
        }
    }

    @Test
    fun sending_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            MessageInput(
                state = TextFieldState("Sending this message..."),
                isSending = true,
            )
        }
    }

    @Test
    fun withTextSelection_light() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.LIGHT),
        ) {
            MessageInput(
                state = TextFieldState(
                    "Hello, this is selected text!",
                    TextRange(0, 5),
                ),
                isSending = false,
            )
        }
    }

    @Test
    fun withTextSelection_dark() {
        captureScreenshot(
            configuration = TestConfiguration(theme = Theme.DARK),
        ) {
            MessageInput(
                state = TextFieldState(
                    "Hello, this is selected text!",
                    TextRange(0, 5),
                ),
                isSending = false,
            )
        }
    }

    @Test
    fun smallScreen_longText_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.SMALL,
                theme = Theme.LIGHT,
            ),
        ) {
            MessageInput(
                state = TextFieldState(
                    "This is a test message on a small screen that should wrap appropriately",
                ),
                isSending = false,
            )
        }
    }

    @Test
    fun smallScreen_longText_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.SMALL,
                theme = Theme.DARK,
            ),
        ) {
            MessageInput(
                state = TextFieldState(
                    "This is a test message on a small screen that should wrap appropriately",
                ),
                isSending = false,
            )
        }
    }

    @Test
    fun largeScreen_multipleLines_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.LARGE,
                theme = Theme.LIGHT,
            ),
        ) {
            MessageInput(
                state = TextFieldState(
                    "Line 1\nLine 2\nLine 3\nLine 4 with some additional text to test " +
                        "multiline behavior on larger screens",
                ),
                isSending = false,
            )
        }
    }

    @Test
    fun largeScreen_multipleLines_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                screenSize = ScreenSize.LARGE,
                theme = Theme.DARK,
            ),
        ) {
            MessageInput(
                state = TextFieldState(
                    "Line 1\nLine 2\nLine 3\nLine 4 with some additional text to test " +
                        "multiline behavior on larger screens",
                ),
                isSending = false,
            )
        }
    }

    @Test
    fun rtl_withText_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.LIGHT,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            @Suppress("SpellCheckingInspection")
            MessageInput(
                state = TextFieldState("مرحبا، هذه رسالة تجريبية"),
                isSending = false,
            )
        }
    }

    @Test
    fun rtl_withText_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.DARK,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            @Suppress("SpellCheckingInspection")
            MessageInput(
                state = TextFieldState("مرحبا، هذه رسالة تجريبية"),
                isSending = false,
            )
        }
    }

    @Test
    fun rtl_sending_light() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.LIGHT,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            @Suppress("SpellCheckingInspection")
            MessageInput(
                state = TextFieldState("إرسال الرسالة..."),
                isSending = true,
            )
        }
    }

    @Test
    fun rtl_sending_dark() {
        captureScreenshot(
            configuration = TestConfiguration(
                theme = Theme.DARK,
                layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl,
            ),
        ) {
            @Suppress("SpellCheckingInspection")
            MessageInput(
                state = TextFieldState("إرسال الرسالة..."),
                isSending = true,
            )
        }
    }
}
