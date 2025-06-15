package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.ui.theme.MessengerTheme

@Composable
fun MessageInput(
    text: String,
    isSending: Boolean,
    onTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .testTag("message_input"),
            placeholder = { Text(stringResource(R.string.message_input_placeholder)) },
            shape = RoundedCornerShape(24.dp),
            enabled = !isSending,
        )

        IconButton(
            onClick = onSendMessage,
            enabled = text.isNotBlank() && !isSending,
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
                    tint = if (text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageInputEmptyPreview() {
    MessengerTheme {
        MessageInput(
            text = "",
            isSending = false,
            onTextChange = {},
            onSendMessage = {},
        )
    }
}

@Preview(showBackground = true, locale = "de")
@Composable
private fun MessageInputEmptyGermanPreview() {
    MessengerTheme {
        MessageInput(
            text = "",
            isSending = false,
            onTextChange = {},
            onSendMessage = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageInputWithTextPreview() {
    MessengerTheme {
        MessageInput(
            text = "Hello, this is my message!",
            isSending = false,
            onTextChange = {},
            onSendMessage = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageInputSendingPreview() {
    MessengerTheme {
        MessageInput(
            text = "Sending this message...",
            isSending = true,
            onTextChange = {},
            onSendMessage = {},
        )
    }
}
