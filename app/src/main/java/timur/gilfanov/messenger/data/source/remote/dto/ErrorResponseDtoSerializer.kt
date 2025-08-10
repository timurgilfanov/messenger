package timur.gilfanov.messenger.data.source.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

/**
 * Custom serializer for ErrorResponseDto that handles direct ApiErrorCode serialization
 * while extracting additional data from details map for complex error types.
 */
object ErrorResponseDtoSerializer : KSerializer<ErrorResponseDto> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ErrorResponseDto") {
        element<String>("code")
        element<String>("message")
        element<Map<String, String>?>("details", isOptional = true)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("CyclomaticComplexMethod") // Comprehensive error code mapping
    override fun serialize(encoder: Encoder, value: ErrorResponseDto) {
        encoder.encodeStructure(descriptor) {
            // Serialize ApiErrorCode back to string
            val codeString = when (value.code) {
                is ApiErrorCode.ChatNotFound -> "CHAT_NOT_FOUND"
                is ApiErrorCode.MessageNotFound -> "MESSAGE_NOT_FOUND"
                is ApiErrorCode.InvalidInviteLink -> "INVALID_INVITE_LINK"
                is ApiErrorCode.ExpiredInviteLink -> "EXPIRED_INVITE_LINK"
                is ApiErrorCode.ChatClosed -> "CHAT_CLOSED"
                is ApiErrorCode.AlreadyJoined -> "ALREADY_JOINED"
                is ApiErrorCode.ChatFull -> "CHAT_FULL"
                is ApiErrorCode.UserBlocked -> "USER_BLOCKED"
                is ApiErrorCode.RateLimitExceeded -> "RATE_LIMIT_EXCEEDED"
                is ApiErrorCode.CooldownActive -> "COOLDOWN_ACTIVE"
                is ApiErrorCode.Unauthorized -> "UNAUTHORIZED"
                is ApiErrorCode.NetworkNotAvailable -> "NETWORK_NOT_AVAILABLE"
                is ApiErrorCode.ServerUnreachable -> "SERVER_UNREACHABLE"
                is ApiErrorCode.ServerError -> "SERVER_ERROR"
                is ApiErrorCode.Unknown -> value.code.originalCode
            }

            encodeStringElement(descriptor, 0, codeString)
            encodeStringElement(descriptor, 1, value.message)

            // For CooldownActive, add remainingMs to details
            val finalDetails = when (value.code) {
                is ApiErrorCode.CooldownActive -> {
                    val currentDetails = value.details?.toMutableMap() ?: mutableMapOf()
                    currentDetails["remainingMs"] = value.code.remainingMs.toString()
                    currentDetails
                }
                else -> value.details
            }

            if (finalDetails != null) {
                encodeSerializableElement(
                    descriptor,
                    2,
                    MapSerializer(String.serializer(), String.serializer()),
                    finalDetails,
                )
            } else {
                encodeNullableSerializableElement(
                    descriptor,
                    2,
                    MapSerializer(String.serializer(), String.serializer()),
                    null,
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("CyclomaticComplexMethod") // Comprehensive error code mapping
    override fun deserialize(
        decoder: Decoder,
    ): ErrorResponseDto = decoder.decodeStructure(descriptor) {
        var code: String? = null
        var message: String? = null
        var details: Map<String, String>? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                0 -> code = decodeStringElement(descriptor, 0)
                1 -> message = decodeStringElement(descriptor, 1)
                2 -> details = decodeNullableSerializableElement(
                    descriptor,
                    2,
                    MapSerializer(String.serializer(), String.serializer()),
                )
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected index: $index")
            }
        }

        require(code != null && message != null) { "Missing required fields" }

        // Convert string code to sealed class with data extraction
        val apiErrorCode = when (code) {
            "CHAT_NOT_FOUND" -> ApiErrorCode.ChatNotFound
            "MESSAGE_NOT_FOUND" -> ApiErrorCode.MessageNotFound
            "INVALID_INVITE_LINK" -> ApiErrorCode.InvalidInviteLink
            "EXPIRED_INVITE_LINK" -> ApiErrorCode.ExpiredInviteLink
            "CHAT_CLOSED" -> ApiErrorCode.ChatClosed
            "ALREADY_JOINED" -> ApiErrorCode.AlreadyJoined
            "CHAT_FULL" -> ApiErrorCode.ChatFull
            "USER_BLOCKED" -> ApiErrorCode.UserBlocked
            "RATE_LIMIT_EXCEEDED" -> ApiErrorCode.RateLimitExceeded
            "COOLDOWN_ACTIVE" -> {
                @Suppress("MagicNumber") // Standard time constants
                val defaultCooldownMs = 5 * 60 * 1000L // 5 minutes default
                val remainingMs =
                    details?.get("remainingMs")?.toLongOrNull() ?: defaultCooldownMs
                ApiErrorCode.CooldownActive(remainingMs)
            }
            "UNAUTHORIZED" -> ApiErrorCode.Unauthorized
            "NETWORK_NOT_AVAILABLE" -> ApiErrorCode.NetworkNotAvailable
            "SERVER_UNREACHABLE" -> ApiErrorCode.ServerUnreachable
            "SERVER_ERROR" -> ApiErrorCode.ServerError
            else -> ApiErrorCode.Unknown(code)
        }

        ErrorResponseDto(apiErrorCode, message, details)
    }
}
