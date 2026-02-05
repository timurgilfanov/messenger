package timur.gilfanov.messenger.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ReduceOnlyInActorRuleTest {

    private val rule = ReduceOnlyInActorRule(Config.empty)

    @Test
    fun `allows reduce inside scan block`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.search

            class SearchStore {
                val state = actor(intents)
                    .scan(State()) { acc, result ->
                        reduce(acc, result)
                    }
                    .stateIn(scope)

                private fun reduce(state: State, result: Result): State = state
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(0, findings.size, "Expected 0 findings but got ${findings.size}: $findings")
    }

    @Test
    fun `detects reduce outside scan block`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.search

            class SearchStore {
                fun doSomething() {
                    val newState = reduce(state.value, result)
                }

                private fun reduce(state: State, result: Result): State = state
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(1, findings.size, "Expected 1 finding but got ${findings.size}: $findings")
    }

    @Test
    fun `ignores non-Store classes`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.search

            class SearchViewModel {
                fun doSomething() {
                    reduce(state, result)
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(0, findings.size, "Expected 0 findings but got ${findings.size}: $findings")
    }

    @Test
    fun `ignores reduce function definition`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.search

            class SearchStore {
                private fun reduce(state: State, result: Result): State = state
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(0, findings.size, "Expected 0 findings but got ${findings.size}: $findings")
    }

    @Test
    fun `ignores classes outside messenger ui package`() {
        val code = """
            package com.example.other

            class SomeStore {
                fun doSomething() {
                    reduce(state, result)
                }
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(0, findings.size, "Expected 0 findings but got ${findings.size}: $findings")
    }

    @Test
    fun `allows reduce inside nested lambda within scan`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.search

            class SearchStore {
                val state = actor(intents)
                    .scan(State()) { acc, result ->
                        result.items.fold(acc) { current, item ->
                            reduce(current, item)
                        }
                    }
                    .stateIn(scope)

                private fun reduce(state: State, result: Any): State = state
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(0, findings.size, "Expected 0 findings but got ${findings.size}: $findings")
    }

    @Test
    fun `detects reduce in map block outside scan`() {
        val code = """
            package timur.gilfanov.messenger.ui.screen.search

            class SearchStore {
                val transformed = items.map { item ->
                    reduce(state.value, item)
                }

                private fun reduce(state: State, result: Any): State = state
            }
        """.trimIndent()

        val findings = rule.lint(code)
        assertEquals(1, findings.size, "Expected 1 finding but got ${findings.size}: $findings")
    }
}
