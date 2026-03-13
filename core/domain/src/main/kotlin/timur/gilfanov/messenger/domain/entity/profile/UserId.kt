package timur.gilfanov.messenger.domain.entity.profile

import java.util.UUID

/**
 * Unique identifier for a user in the Profile/Identity bounded context.
 *
 * Represents the authenticated user's identity within the system.
 * Used for profile management, settings, and authentication.
 *
 * Note: This is intentionally separate from [timur.gilfanov.messenger.domain.entity.chat.ParticipantId] to:
 * - Maintain bounded context separation (DDD principle)
 * - Protect user privacy (ParticipantId should not be matchable to UserId)
 * - Support future extensibility (anonymous participants, bots, external users)
 *
 * @see timur.gilfanov.messenger.domain.entity.chat.ParticipantId
 */
@JvmInline
value class UserId(val id: UUID)
