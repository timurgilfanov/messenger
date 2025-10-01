package timur.gilfanov.messenger.debug

import timur.gilfanov.messenger.domain.entity.chat.Chat

class SampleDataProviderDecorator(private val provider: SampleDataProvider) :
    SampleDataProvider by provider {

    var generateEmptyChats = false

    override fun generateChats(config: DataGenerationConfig): List<Chat> {
        if (generateEmptyChats) return emptyList()
        return provider.generateChats(config)
    }
}
