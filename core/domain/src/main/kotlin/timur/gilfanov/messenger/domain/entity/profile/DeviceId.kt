package timur.gilfanov.messenger.domain.entity.profile

import java.util.UUID

/**
 * Uniquely identifies a device in the system.
 *
 * Used to distinguish between different devices that the same user may use
 * to access the application.
 */
@JvmInline
value class DeviceId(val id: UUID)
