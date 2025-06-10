package timur.gilfanov.messenger.domain

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ArchitectureTest {

    val usecaseLayer = Layer("usecase", "timur.gilfanov.messenger.domain.usecase..")
    val entityLayer = Layer("entity", "timur.gilfanov.messenger.domain.entity..")

    @Test
    fun `usecase layer depends on entity layer only`() {
        Konsist
            .scopeFromProject()
            .assertArchitecture {
                usecaseLayer.dependsOn(entityLayer)
            }
    }

    @Test
    fun `entity layer depends on nothing`() {
        Konsist
            .scopeFromProject()
            .assertArchitecture {
                entityLayer.dependsOnNothing()
            }
    }

    @Test
    fun `domain layer does not depend on Hilt`() {
        Konsist
            .scopeFromProduction()
            .files
            .withPackage("timur.gilfanov.messenger.domain..")
            .assertTrue {
                !it.text.contains("import dagger.hilt") &&
                    !it.text.contains("import javax.inject")
            }
    }
}
