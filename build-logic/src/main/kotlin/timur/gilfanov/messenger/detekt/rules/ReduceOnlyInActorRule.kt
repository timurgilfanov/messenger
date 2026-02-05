package timur.gilfanov.messenger.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ReduceOnlyInActorRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "ReduceOnlyInActor",
        severity = Severity.Defect,
        description = "Calls to `reduce` must only be made within `scan { }` block " +
            "that processes actor output. This ensures only the actor can commit " +
            "UI state updates (AR-01).",
        debt = Debt.TEN_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName != "reduce") return

        val containingClass = expression.containingClass()
        if (!isStoreClass(containingClass)) return

        if (!isInsideScanBlock(expression)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Call to `reduce` must be inside a `scan { }` block. " +
                        "Only the actor should commit UI state updates.",
                ),
            )
        }
    }

    private fun isInsideScanBlock(expression: KtCallExpression): Boolean {
        var current: KtLambdaExpression? = expression.getParentOfType(true)
        while (current != null) {
            val parentCall = current.getParentOfType<KtCallExpression>(false)
            if (parentCall?.calleeExpression?.text == "scan") {
                return true
            }
            current = current.getParentOfType(true)
        }
        return false
    }

    private fun isStoreClass(ktClass: KtClass?): Boolean {
        if (ktClass == null) return false

        val className = ktClass.name ?: return false
        if (!className.endsWith("Store")) return false

        val packageName = ktClass.containingKtFile.packageFqName.asString()
        return packageName.startsWith("timur.gilfanov.messenger.ui")
    }
}
