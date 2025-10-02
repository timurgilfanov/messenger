package timur.gilfanov.messenger.domain.entity.user

sealed interface UiLanguage {
    object English : UiLanguage
    object German : UiLanguage
}
