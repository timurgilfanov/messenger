package timur.gilfanov.messenger.domain.entity.user

/**
 * Represents the identity of a user on a specific device.
 *
 * Combines user identification with device identification to uniquely
 * identify a user-device pair in the system.
 *
 * @property userId The unique identifier of the user
 * @property deviceId The unique identifier of the device
 */
data class Identity(val userId: UserId, val deviceId: DeviceId)
