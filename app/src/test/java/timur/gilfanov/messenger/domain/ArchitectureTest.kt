package timur.gilfanov.messenger.domain

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
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
}
