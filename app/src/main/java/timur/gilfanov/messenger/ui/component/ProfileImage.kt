package timur.gilfanov.messenger.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import timur.gilfanov.messenger.ui.theme.MessengerTheme
import timur.gilfanov.messenger.util.generateProfileImageUrl

@Composable
fun ProfileImage(
    pictureUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (pictureUrl != null) {
            SubcomposeAsyncImage(
                model = pictureUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            ) {
                when (painter.state) {
                    is coil3.compose.AsyncImagePainter.State.Loading -> {
                        ProfileImageFallback(
                            name = name,
                            contentColor = contentColor,
                            iconSize = size / 2,
                        )
                    }
                    is coil3.compose.AsyncImagePainter.State.Error -> {
                        ProfileImageFallback(
                            name = name,
                            contentColor = contentColor,
                            iconSize = size / 2,
                        )
                    }
                    else -> {
                        SubcomposeAsyncImageContent()
                    }
                }
            }
        } else {
            ProfileImageFallback(
                name = name,
                contentColor = contentColor,
                iconSize = size / 2,
            )
        }
    }
}

@Composable
private fun ProfileImageFallback(name: String, contentColor: Color, iconSize: Dp) {
    if (name.isNotEmpty()) {
        Text(
            text = name.first().uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    } else {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = contentColor,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileImageWithUrlPreview() {
    val profileImageUrl = generateProfileImageUrl("John Doe")
    MessengerTheme {
        ProfileImage(
            pictureUrl = profileImageUrl,
            name = "John Doe",
            size = 48.dp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileImageWithoutUrlPreview() {
    MessengerTheme {
        ProfileImage(
            pictureUrl = null,
            name = "Alice Smith",
            size = 48.dp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileImageEmptyNamePreview() {
    MessengerTheme {
        ProfileImage(
            pictureUrl = null,
            name = "",
            size = 48.dp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileImageSmallSizePreview() {
    MessengerTheme {
        ProfileImage(
            pictureUrl = null,
            name = "Bob",
            size = 32.dp,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileImageLargeSizePreview() {
    MessengerTheme {
        ProfileImage(
            pictureUrl = null,
            name = "Sarah",
            size = 64.dp,
        )
    }
}
