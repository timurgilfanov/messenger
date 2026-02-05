package timur.gilfanov.messenger.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import timur.gilfanov.messenger.detekt.rules.NoMutableStateFlowExposureRule

class StoreRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "StoreRules"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(NoMutableStateFlowExposureRule(config)),
    )
}
