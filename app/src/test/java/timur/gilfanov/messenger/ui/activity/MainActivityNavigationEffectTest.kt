package timur.gilfanov.messenger.ui.activity

import androidx.navigation3.runtime.NavKey
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.navigation.Chat
import timur.gilfanov.messenger.navigation.Language
import timur.gilfanov.messenger.navigation.Login
import timur.gilfanov.messenger.navigation.Main
import timur.gilfanov.messenger.navigation.ProfileEdit
import timur.gilfanov.messenger.navigation.Signup

@Category(Component::class)
class MainActivityNavigationEffectTest {

    @Test
    fun `NavigateToLogin from empty stack adds Login`() {
        val stack = mutableListOf<NavKey>()

        applyMainActivityEffect(MainActivitySideEffect.NavigateToLogin, stack)

        assertEquals(listOf<NavKey>(Login), stack)
    }

    @Test
    fun `NavigateToLogin from Login top keeps stack`() {
        val stack = mutableListOf<NavKey>(Login)

        applyMainActivityEffect(MainActivitySideEffect.NavigateToLogin, stack)

        assertEquals(listOf<NavKey>(Login), stack)
    }

    @Test
    fun `NavigateToLogin from Signup top keeps stack`() {
        val stack = mutableListOf<NavKey>(Signup)

        applyMainActivityEffect(MainActivitySideEffect.NavigateToLogin, stack)

        assertEquals(listOf<NavKey>(Signup), stack)
    }

    @Test
    fun `NavigateToLogin from Main clears and adds Login`() {
        val stack = mutableListOf<NavKey>(Main)

        applyMainActivityEffect(MainActivitySideEffect.NavigateToLogin, stack)

        assertEquals(listOf<NavKey>(Login), stack)
    }

    @Test
    fun `NavigateToLogin from deep authenticated stack clears and adds Login`() {
        val stack = mutableListOf<NavKey>(Main, Chat("chat-id"))

        applyMainActivityEffect(MainActivitySideEffect.NavigateToLogin, stack)

        assertEquals(listOf<NavKey>(Login), stack)
    }

    @Test
    fun `NavigateToMain from empty stack adds Main`() {
        val stack = mutableListOf<NavKey>()

        applyMainActivityEffect(MainActivitySideEffect.NavigateToMain, stack)

        assertEquals(listOf<NavKey>(Main), stack)
    }

    @Test
    fun `NavigateToMain from Login clears and adds Main`() {
        val stack = mutableListOf<NavKey>(Login)

        applyMainActivityEffect(MainActivitySideEffect.NavigateToMain, stack)

        assertEquals(listOf<NavKey>(Main), stack)
    }

    @Test
    fun `NavigateToMain from Signup clears and adds Main`() {
        val stack = mutableListOf<NavKey>(Signup)

        applyMainActivityEffect(MainActivitySideEffect.NavigateToMain, stack)

        assertEquals(listOf<NavKey>(Main), stack)
    }

    @Test
    fun `NavigateToMain from Main keeps stack`() {
        val stack = mutableListOf<NavKey>(Main)

        applyMainActivityEffect(MainActivitySideEffect.NavigateToMain, stack)

        assertEquals(listOf<NavKey>(Main), stack)
    }

    @Test
    fun `NavigateToMain from Chat keeps deeper authenticated stack`() {
        val stack = mutableListOf<NavKey>(Main, Chat("chat-id"))

        applyMainActivityEffect(MainActivitySideEffect.NavigateToMain, stack)

        assertEquals(listOf<NavKey>(Main, Chat("chat-id")), stack)
    }

    @Test
    fun `NavigateToMain from ProfileEdit keeps stack`() {
        val stack = mutableListOf<NavKey>(Main, ProfileEdit)

        applyMainActivityEffect(MainActivitySideEffect.NavigateToMain, stack)

        assertEquals(listOf<NavKey>(Main, ProfileEdit), stack)
    }

    @Test
    fun `NavigateToMain from Language keeps stack`() {
        val stack = mutableListOf<NavKey>(Main, Language)

        applyMainActivityEffect(MainActivitySideEffect.NavigateToMain, stack)

        assertEquals(listOf<NavKey>(Main, Language), stack)
    }
}
