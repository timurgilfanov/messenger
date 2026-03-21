package timur.gilfanov.messenger.auth.data.source.local

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import timur.gilfanov.messenger.annotations.FeatureTest
import timur.gilfanov.messenger.domain.entity.ResultWithError

@RunWith(AndroidJUnit4::class)
@FeatureTest
class AuthSessionCipherImplTest {

    private val cipher = AuthSessionCipherImpl()

    @Test
    fun encryptAndDecrypt_returnsOriginalPlaintext() {
        val encryptedResult = cipher.encrypt("token-value-1")
        val encrypted = assertIs<ResultWithError.Success<String, AuthSessionCipherError>>(
            encryptedResult,
        ).data

        val decryptedResult = cipher.decrypt(encrypted)

        assertEquals(
            ResultWithError.Success<String, AuthSessionCipherError>("token-value-1"),
            decryptedResult,
        )
    }

    @Test
    fun encrypt_returnsBase64EncodedIvPlusCiphertext() {
        val result = cipher.encrypt("token-value-2")

        val encoded = assertIs<ResultWithError.Success<String, AuthSessionCipherError>>(result).data
        val decoded = Base64.decode(encoded, Base64.NO_WRAP)

        assertTrue(decoded.size > 12)
    }

    @Test
    fun decrypt_returnsDataCorrupted_forNonBase64Input() {
        val result = cipher.decrypt("%%%not-base64%%%")

        assertEquals(ResultWithError.Failure(AuthSessionCipherError.DataCorrupted), result)
    }

    @Test
    fun decrypt_returnsDataCorrupted_forPayloadShorterThanIv() {
        val shortPayload = Base64.encodeToString(byteArrayOf(1, 2, 3), Base64.NO_WRAP)

        val result = cipher.decrypt(shortPayload)

        assertEquals(ResultWithError.Failure(AuthSessionCipherError.DataCorrupted), result)
    }

    @Test
    fun decrypt_returnsDataCorrupted_forTamperedCiphertext() {
        val encryptedResult = cipher.encrypt("token-value-3")
        val encrypted = assertIs<ResultWithError.Success<String, AuthSessionCipherError>>(
            encryptedResult,
        ).data
        val tamperedPayload = Base64.decode(encrypted, Base64.NO_WRAP).apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 0x01).toByte()
        }
        val tamperedEncoded = Base64.encodeToString(tamperedPayload, Base64.NO_WRAP)

        val result = cipher.decrypt(tamperedEncoded)

        assertEquals(ResultWithError.Failure(AuthSessionCipherError.DataCorrupted), result)
    }
}
