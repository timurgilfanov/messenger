package timur.gilfanov.messenger.data.source.remote

sealed interface RemoteSetting<out T> {
    data class Valid<T>(val value: T, val serverVersion: Int) : RemoteSetting<T>

    data object Missing : RemoteSetting<Nothing>

    data class InvalidValue<T>(val rawValue: String, val serverVersion: Int) : RemoteSetting<T>
}
