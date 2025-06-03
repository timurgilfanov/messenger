package timur.gilfanov.messenger.domain

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class DeclarationTest {

    @Test
    fun `use cases reside in package usecase`() {
        Konsist
            .scopeFromProject()
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue {
                it.resideInPackage("..usecase..")
            }
    }
}
