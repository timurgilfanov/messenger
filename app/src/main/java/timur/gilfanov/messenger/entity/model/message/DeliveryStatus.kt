package timur.gilfanov.messenger.entity.model.message

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
}
