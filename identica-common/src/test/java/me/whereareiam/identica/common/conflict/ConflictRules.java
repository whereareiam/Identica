package me.whereareiam.identica.common.conflict;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.model.conflict.ConflictParty;
import me.whereareiam.identica.model.conflict.ConflictPartyCondition;
import me.whereareiam.identica.model.conflict.ConflictRuleOutcome;
import me.whereareiam.identica.model.conflict.ConflictSituation;
import me.whereareiam.identica.model.identity.username.UsernameConflictRule;
import me.whereareiam.identica.type.identity.username.UsernameConflictTrigger;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.identica.type.provider.ProviderGuarantee;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds conflict rules and situations for tests, with neutral provider ids.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConflictRules {
	static final Set<ProviderGuarantee> OWNED = Set.of(ProviderGuarantee.OWNED_USERNAME);
	static final Set<ProviderGuarantee> NONE = Set.of();

	static UsernameConflictRule rule(String outcome) {
		UsernameConflictRule rule = new UsernameConflictRule();
		rule.setOutcome(ConflictRuleOutcome.of(outcome));
		return rule;
	}

	static UsernameConflictRule guarantees(Set<ProviderGuarantee> arriving, Set<ProviderGuarantee> holder, String outcome) {
		UsernameConflictRule rule = rule(outcome);
		rule.setArriving(condition(arriving, null));
		rule.setHolder(condition(holder, null));
		return rule;
	}

	static UsernameConflictRule providers(List<String> arriving, List<String> holder, String outcome) {
		UsernameConflictRule rule = rule(outcome);
		rule.setArriving(condition(null, arriving));
		rule.setHolder(condition(null, holder));
		return rule;
	}

	static ConflictPartyCondition condition(Set<ProviderGuarantee> guarantees, List<String> providers) {
		ConflictPartyCondition condition = new ConflictPartyCondition();
		condition.setGuarantees(guarantees);
		condition.setProviders(providers);
		return condition;
	}

	static ConflictSituation<UsernameConflictTrigger> situation(
			String arrivingProvider,
			Set<ProviderGuarantee> arriving,
			String holderProvider,
			Set<ProviderGuarantee> holder,
			UsernameConflictTrigger trigger,
			ProviderClaimOrigin arrivedVia
	) {
		return ConflictSituation.<UsernameConflictTrigger>builder()
				.arriving(ConflictParty.builder().accountUniqueId(UUID.randomUUID()).providerId(arrivingProvider).guarantees(arriving).build())
				.holder(ConflictParty.builder().accountUniqueId(UUID.randomUUID()).providerId(holderProvider).guarantees(holder).build())
				.trigger(trigger)
				.arrivedVia(arrivedVia)
				.build();
	}
}
