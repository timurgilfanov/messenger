package timur.gilfanov.messenger.domain.entity.user

sealed interface UILanguage {
    object English : UILanguage
    object German : UILanguage
}
