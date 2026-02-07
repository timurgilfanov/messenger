package timur.gilfanov.messenger.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NoMutableStateFlowExposureRuleTest {

    private val rule = NoMutableStateFlowExposureRule(Config.empty)

    @Test
    fun `detects exposed MutableStateFlow without asStateFlow`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.chat

            import kotlinx.coroutines.flow.MutableStateFlow

            class ChatStore {
                val badState = MutableStateFlow(Unit)
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(1, findings.size, "Expected 1 finding but got ${findings.size}: $findings")
    }

    @Test
    fun `allows StateFlow via asStateFlow`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.chat

            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.asStateFlow

            class ChatStore {
                val state = MutableStateFlow(Unit).asStateFlow()
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(0, findings.size, "Expected 0 findings but got ${findings.size}: $findings")
    }

    @Test
    fun `detects explicit MutableStateFlow type`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.chat

            import kotlinx.coroutines.flow.MutableStateFlow

            class ChatStore {
                val badState: MutableStateFlow<Unit> = MutableStateFlow(Unit)
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(1, findings.size, "Expected 1 finding but got ${findings.size}: $findings")
    }

    @Test
    fun `ignores private MutableStateFlow`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.chat

            import kotlinx.coroutines.flow.MutableStateFlow

            class ChatStore {
                private val _state = MutableStateFlow(Unit)
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(0, findings.size, "Expected 0 findings but got ${findings.size}: $findings")
    }

    @Test
    fun `ignores Store classes implementing ContainerHost`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.chat

            import kotlinx.coroutines.flow.MutableStateFlow

            class ChatStore : ContainerHost<State, SideEffect> {
                val state = MutableStateFlow(Unit)
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(0, findings.size, "Expected 0 findings but got ${findings.size}: $findings")
    }

    @Test
    fun `ignores non-Store classes`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.chat

            import kotlinx.coroutines.flow.MutableStateFlow

            class ChatViewModel {
                val state = MutableStateFlow(Unit)
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(0, findings.size, "Expected 0 findings but got ${findings.size}: $findings")
    }
}
