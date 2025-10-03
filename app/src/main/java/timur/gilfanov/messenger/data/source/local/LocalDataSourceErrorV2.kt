package timur.gilfanov.messenger.data.source.local

sealed interface LocalDataSourceErrorV2 {
    data object StorageUnavailable : LocalDataSourceErrorV2
    data object StorageFull : LocalDataSourceErrorV2
    data object DataCorrupted : LocalDataSourceErrorV2
    data object ConcurrentModificationError : LocalDataSourceErrorV2
    data class InvalidData(val field: String, val reason: String) : LocalDataSourceErrorV2
}
