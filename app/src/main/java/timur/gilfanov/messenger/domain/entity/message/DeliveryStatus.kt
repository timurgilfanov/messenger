package timur.gilfanov.messenger.domain.entity.message

sealed class DeliveryStatus {
    data class Sending(val progress: Int) : DeliveryStatus() {
        init {
            @Suppress("MagicNumber")
            require(progress in 0..100) { "Progress must be between 0 and 100" }
        }
    }
    data class Failed(val reason: DeliveryError) : DeliveryStatus()
    object Sent : DeliveryStatus()
    object Delivered : DeliveryStatus()
    object Read : DeliveryStatus()

    override fun toString(): String = when (this) {
        is Sending -> "Sending($progress)"
        is Failed -> "Failed($reason)"
        Sent -> "Sent"
        Delivered -> "Delivered"
        Read -> "Read"
    }
}
