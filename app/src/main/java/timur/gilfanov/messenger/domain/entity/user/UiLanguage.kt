package timur.gilfanov.messenger.domain.entity.user

sealed interface UiLanguage {
    data object English : UiLanguage
    data object German : UiLanguage
}
