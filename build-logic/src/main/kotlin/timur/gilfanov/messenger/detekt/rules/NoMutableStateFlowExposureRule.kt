package timur.gilfanov.messenger.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class NoMutableStateFlowExposureRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "NoMutableStateFlowExposure",
        severity = Severity.Defect,
        description = "Store classes must not expose MutableStateFlow publicly. " +
            "Use .asStateFlow() to expose a read-only StateFlow instead.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        val containingClass = property.containingClass()
        if (!isStoreClass(containingClass)) return

        if (property.isPrivate()) return

        if (bindingContext != BindingContext.EMPTY) {
            checkWithTypeResolution(property)
        } else {
            checkWithoutTypeResolution(property)
        }
    }

    private fun checkWithTypeResolution(property: KtProperty) {
        val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, property]
            as? PropertyDescriptor ?: return

        val returnType = descriptor.returnType ?: return
        val fqName = returnType.constructor.declarationDescriptor?.fqNameSafe

        if (fqName == MUTABLE_STATE_FLOW_FQ_NAME) {
            reportViolation(property)
        }
    }

    private fun checkWithoutTypeResolution(property: KtProperty) {
        val explicitType = property.typeReference?.text
        if (explicitType != null && explicitType.contains("MutableStateFlow")) {
            reportViolation(property)
            return
        }

        val initializer = property.initializer?.text ?: return
        if (initializer.contains("MutableStateFlow") && !initializer.contains(".asStateFlow()")) {
            reportViolation(property)
        }
    }

    private fun reportViolation(property: KtProperty) {
        report(
            CodeSmell(
                issue,
                Entity.from(property),
                "Property '${property.name}' exposes MutableStateFlow publicly. " +
                    "Use .asStateFlow() to expose StateFlow instead.",
            ),
        )
    }

    private fun isStoreClass(ktClass: KtClass?): Boolean {
        if (ktClass == null) return false

        val className = ktClass.name ?: return false
        if (!className.endsWith("Store")) return false

        val packageName = ktClass.containingKtFile.packageFqName.asString()
        return packageName.startsWith("timur.gilfanov.messenger.ui")
    }

    companion object {
        private val MUTABLE_STATE_FLOW_FQ_NAME = FqName("kotlinx.coroutines.flow.MutableStateFlow")
    }
}
