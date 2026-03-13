package timur.gilfanov.messenger.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import timur.gilfanov.messenger.detekt.rules.NoMutableStateFlowExposureRule
import timur.gilfanov.messenger.detekt.rules.ReduceOnlyInActorRule

class MviRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "MviRules"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            NoMutableStateFlowExposureRule(config),
            ReduceOnlyInActorRule(config),
        ),
    )
}
