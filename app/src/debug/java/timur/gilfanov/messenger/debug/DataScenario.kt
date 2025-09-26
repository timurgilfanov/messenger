package timur.gilfanov.messenger.debug

/**
 * Defines different data scenarios for debug builds.
 * Each scenario provides a different set of test data to help with development and testing.
 */
enum class DataScenario(
    val chatCount: Int,
    val messagesPerChat: IntRange,
    val description: String,
) {
    /**
     * Empty state testing - no data at all
     */
    EMPTY(
        chatCount = 0,
        messagesPerChat = 0..0,
        description = "Empty state testing",
    ),

    /**
     * Minimal data for quick testing and development
     */
    MINIMAL(
        chatCount = 5,
        messagesPerChat = 5..10,
        description = "Minimal data for quick testing",
    ),

    /**
     * Standard development data with moderate amount of content
     */
    STANDARD(
        chatCount = 100,
        messagesPerChat = 20..50,
        description = "Standard development data",
    ),

    /**
     * Heavy data for performance testing and stress scenarios
     */
    HEAVY(
        chatCount = 1000,
        messagesPerChat = 1..2000,
        description = "Heavy data for performance testing",
    ),

    /**
     * Edge cases including Unicode, very long messages, empty chats, etc.
     */
    EDGE_CASES(
        chatCount = 10,
        messagesPerChat = 1..100,
        description = "Edge cases with Unicode and long messages",
    ),

    /**
     * Polished demo data for presentations and demonstrations
     */
    DEMO(
        chatCount = 8,
        messagesPerChat = 10..30,
        description = "Polished data for demonstrations",
    ),
    ;

    companion object {
        /**
         * Parse scenario from string, case-insensitive
         */
        fun fromString(value: String): DataScenario? =
            entries.find { it.name.equals(value, ignoreCase = true) }

        /**
         * Get all scenario names for UI display
         */
        fun getAllNames(): List<String> = entries.map { it.name }
    }

    /**
     * Convert to configuration object for data generation
     */
    fun toConfig(): DataGenerationConfig = DataGenerationConfig(
        chatCount = chatCount,
        messageCountRange = messagesPerChat,
        includeEdgeCases = this == EDGE_CASES,
        polishedContent = this == DEMO,
        scenario = this,
    )
}
