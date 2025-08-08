package timur.gilfanov.messenger.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple test to verify our Android test infrastructure compiles and works.
 */
@RunWith(AndroidJUnit4::class)
class AndroidTestInfrastructureTest {

    @Test
    fun testInfrastructureCompiles() {
        // Test that we can create our test helper classes
        val repository = AndroidTestRepositoryWithRealImplementation(
            AndroidTestDataHelper.DataScenario.NON_EMPTY,
        )

        // Simple verification that objects were created
        assert(repository != null)
    }

    @Test
    fun testDataHelperConstants() {
        // Verify our constants are accessible
        assert(AndroidTestDataHelper.USER_ID.isNotEmpty())
        assert(AndroidTestDataHelper.ALICE_CHAT_ID.isNotEmpty())
        assert(AndroidTestDataHelper.ALICE_TEXT_1.isNotEmpty())
    }
}
