package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.annotation.ExcludeFromCoverageGenerated
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Suppress("LongParameterList")
@Composable
fun MessageInput(
    state: TextFieldState,
    isSending: Boolean,
    modifier: Modifier = Modifier,
    textValidationError: String? = null,
    onSendMessage: () -> Unit = {},
) {
    println(
        "${System.currentTimeMillis()}[${Thread.currentThread().name}] ChatScreen.MessageInput " +
            "textValidationError: $textValidationError, text: ${state.text}",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = TextFieldValue(
                text = state.text.toString(),
                selection = state.selection,
                composition = state.composition,
            ),
            onValueChange = { state.setTextAndPlaceCursorAtEnd(it.text) },
            modifier = Modifier
                .weight(1f)
                .testTag("message_input"),
            placeholder = { Text(stringResource(R.string.message_input_placeholder)) },
            shape = RoundedCornerShape(24.dp),
            isError = textValidationError != null,
            label = if (textValidationError != null) {
                {
                    Text(
                        text = textValidationError,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                null
            },
            enabled = !isSending,
            maxLines = 16,
        )

        Button(onSendMessage, textValidationError == null, isSending)
    }
}

@Composable
private fun Button(onSendMessage: () -> Unit, isValid: Boolean, isSending: Boolean) {
    IconButton(
        onClick = onSendMessage,
        enabled = isValid && !isSending,
        modifier = Modifier
            .padding(start = 8.dp)
            .testTag("send_button"),
    ) {
        if (isSending) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.send_message_content_description),
                tint = if (isValid) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@ExcludeFromCoverageGenerated
@Composable
private fun MessageInputEmptyPreview() {
    MessengerTheme {
        MessageInput(
            state = TextFieldState(""),
            isSending = false,
        )
    }
}

@Preview(showBackground = true, locale = "de")
@ExcludeFromCoverageGenerated
@Composable
private fun MessageInputEmptyGermanPreview() {
    MessengerTheme {
        MessageInput(
            state = TextFieldState(""),
            isSending = false,
        )
    }
}

@Preview(showBackground = true)
@ExcludeFromCoverageGenerated
@Composable
private fun MessageInputWithTextPreview() {
    MessengerTheme {
        MessageInput(
            state = TextFieldState("Hello, this is my message!"),
            isSending = false,
        )
    }
}

@Preview(showBackground = true)
@ExcludeFromCoverageGenerated
@Composable
private fun MessageInputNotValidPreview() {
    MessengerTheme {
        MessageInput(
            state = TextFieldState("Hello, this is not valid message!"),
            textValidationError = "This message is not valid",
            isSending = false,
        )
    }
}

@Preview(showBackground = true, heightDp = 480)
@ExcludeFromCoverageGenerated
@Composable
private fun MessageInputTooLongPreview() {
    MessengerTheme {
        @Suppress("MagicNumber")
        val longText = "a".repeat(2001)
        MessageInput(
            state = TextFieldState(longText),
            textValidationError = "This message is not valid",
            isSending = false,
        )
    }
}

@Preview(showBackground = true)
@ExcludeFromCoverageGenerated
@Composable
@Suppress("MagicNumber")
private fun MessageInputWithSelectionPreview() {
    MessengerTheme {
        MessageInput(
            state = TextFieldState(
                "Hello, this is my message!",
                TextRange(0, 5),
            ),
            isSending = false,
        )
    }
}

@Preview(showBackground = true)
@ExcludeFromCoverageGenerated
@Composable
private fun MessageInputSendingPreview() {
    MessengerTheme {
        MessageInput(
            state = TextFieldState("Sending this message..."),
            isSending = true,
        )
    }
}
