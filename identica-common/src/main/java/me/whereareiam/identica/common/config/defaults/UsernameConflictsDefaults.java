package me.whereareiam.identica.common.config.defaults;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Singleton;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.common.identity.username.outcome.RefuseOutcome;
import me.whereareiam.identica.common.identity.username.outcome.RenameArrivingOutcome;
import me.whereareiam.identica.common.identity.username.outcome.RenameHolderOutcome;
import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.model.config.UsernameConflicts;
import me.whereareiam.identica.model.conflict.ConflictPartyCondition;
import me.whereareiam.identica.model.conflict.ConflictRuleOutcome;
import me.whereareiam.identica.model.identity.username.UsernameConflictRule;
import me.whereareiam.identica.type.identity.username.UsernameConflictTrigger;
import me.whereareiam.identica.type.provider.ProviderGuarantee;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * The default username conflict rules. They name guarantees only, never providers: a player whose provider owns the
 * name takes it from an account whose name nobody vouches for when joining for the first time, and is renamed when
 * the provider renames them into a name such an account holds; a player who chose a name cannot take one that is
 * held; everything else is refused. The built-in outcomes' settings are written with their defaults.
 */
@Singleton
public class UsernameConflictsDefaults implements DefaultsProvider<UsernameConflicts> {
	@Override
	public UsernameConflicts supply(UsernameConflicts config) {
		ObjectMapper mapper = Config.configured().mapper();
		LinkedHashMap<String, ObjectNode> outcomes = new LinkedHashMap<>();
		for (ConflictOutcome<?, ?, ?> outcome : List.of(new RenameHolderOutcome(), new RenameArrivingOutcome(), new RefuseOutcome()))
			outcomes.put(outcome.id(), mapper.valueToTree(outcome.defaults()));
		config.setOutcomes(outcomes);

		Set<ProviderGuarantee> owned = Set.of(ProviderGuarantee.OWNED_USERNAME);
		Set<ProviderGuarantee> none = Set.of();
		config.setRules(List.of(
				rule(owned, none, UsernameConflictTrigger.FIRST_JOIN, RenameHolderOutcome.ID),
				rule(owned, none, UsernameConflictTrigger.PROVIDER_RENAME, RenameArrivingOutcome.ID),
				rule(none, null, null, RefuseOutcome.ID),
				rule(null, null, null, RefuseOutcome.ID)
		));

		return config;
	}

	private static UsernameConflictRule rule(
			@Nullable Set<ProviderGuarantee> arriving,
			@Nullable Set<ProviderGuarantee> holder,
			@Nullable UsernameConflictTrigger trigger,
			String outcome
	) {
		UsernameConflictRule rule = new UsernameConflictRule();
		rule.setArriving(condition(arriving));
		rule.setHolder(condition(holder));
		rule.setTrigger(trigger);
		rule.setOutcome(ConflictRuleOutcome.of(outcome));

		return rule;
	}

	private static ConflictPartyCondition condition(@Nullable Set<ProviderGuarantee> guarantees) {
		ConflictPartyCondition condition = new ConflictPartyCondition();
		condition.setGuarantees(guarantees);

		return condition;
	}
}
