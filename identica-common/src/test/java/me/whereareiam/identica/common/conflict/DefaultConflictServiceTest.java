package me.whereareiam.identica.common.conflict;

import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.model.conflict.ConflictKind;
import me.whereareiam.identica.model.conflict.ConflictSituation;
import me.whereareiam.identica.model.identity.username.UsernameConflict;
import me.whereareiam.identica.model.identity.username.UsernameConflictRule;
import me.whereareiam.identica.model.conflict.NoSettings;
import me.whereareiam.identica.model.identity.username.UsernameConflictDecision;
import me.whereareiam.identica.type.identity.username.UsernameConflictTrigger;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static me.whereareiam.identica.common.conflict.ConflictRules.NONE;
import static me.whereareiam.identica.common.conflict.ConflictRules.OWNED;
import static me.whereareiam.identica.common.conflict.ConflictRules.guarantees;
import static me.whereareiam.identica.common.conflict.ConflictRules.providers;
import static me.whereareiam.identica.common.conflict.ConflictRules.rule;
import static me.whereareiam.identica.common.conflict.ConflictRules.situation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Default Conflict Service")
class DefaultConflictServiceTest {
	private final DefaultConflictService service = new DefaultConflictService(Set::of);
	private final ConflictSituation<UsernameConflictTrigger> ownedOverChosen =
			situation("alpha", OWNED, "beta", NONE, UsernameConflictTrigger.FIRST_JOIN, ProviderClaimOrigin.NAME_MATCH);

	@DisplayName("A rule naming provider ids beats one naming guarantees, which beats one naming neither")
	@Test
	void prefersProviderIdsOverGuaranteesOverNothing() {
		UsernameConflictRule fallback = rule("refuse");
		UsernameConflictRule byGuarantees = guarantees(OWNED, NONE, "rename-holder");
		UsernameConflictRule byProviders = providers(List.of("ALPHA"), List.of("beta"), "rename-arriving");

		assertSame(byProviders, match(List.of(fallback, byGuarantees, byProviders)));
		assertSame(byGuarantees, match(List.of(fallback, byGuarantees)));
		assertSame(fallback, match(List.of(fallback)));
	}

	@DisplayName("Among equally specific rules the first one in the list wins")
	@Test
	void breaksTiesByOrder() {
		UsernameConflictRule first = guarantees(OWNED, null, "rename-holder");
		UsernameConflictRule second = guarantees(null, NONE, "refuse");

		assertSame(first, match(List.of(first, second)));
		assertSame(second, match(List.of(second, first)));
	}

	@DisplayName("A trigger or the way the player arrived makes a rule more specific, and must match")
	@Test
	void matchesTriggerAndArrival() {
		UsernameConflictRule anyTrigger = guarantees(OWNED, NONE, "refuse");
		UsernameConflictRule firstJoin = guarantees(OWNED, NONE, "rename-holder");
		firstJoin.setTrigger(UsernameConflictTrigger.FIRST_JOIN);
		UsernameConflictRule viaHostname = guarantees(OWNED, NONE, "rename-arriving");
		viaHostname.setTrigger(UsernameConflictTrigger.FIRST_JOIN);
		viaHostname.setArrivedVia(ProviderClaimOrigin.HOSTNAME);

		assertSame(firstJoin, match(List.of(anyTrigger, viaHostname, firstJoin)));
		ConflictSituation<UsernameConflictTrigger> throughHostname = ownedOverChosen.toBuilder().arrivedVia(ProviderClaimOrigin.HOSTNAME).build();
		assertSame(viaHostname, service.match(List.of(anyTrigger, viaHostname, firstJoin), throughHostname).orElseThrow());
		ConflictSituation<UsernameConflictTrigger> renamed = ownedOverChosen.toBuilder().trigger(UsernameConflictTrigger.PROVIDER_RENAME).build();
		assertSame(anyTrigger, service.match(List.of(anyTrigger, viaHostname, firstJoin), renamed).orElseThrow());
	}

	@DisplayName("Guarantees match exactly: an empty list asks for a provider that guarantees nothing")
	@Test
	void matchesGuaranteesExactly() {
		UsernameConflictRule chosenArriving = guarantees(NONE, null, "refuse");

		assertTrue(service.match(List.of(chosenArriving), ownedOverChosen).isEmpty());
		ConflictSituation<UsernameConflictTrigger> chosen = situation("beta", NONE, "alpha", OWNED, UsernameConflictTrigger.FIRST_JOIN, null);
		assertSame(chosenArriving, service.match(List.of(chosenArriving), chosen).orElseThrow());
	}

	@DisplayName("Writes a registered outcome's defaults into its kind's document")
	@Test
	void includesRegisteredOutcomesInTheirDocument() {
		List<ConflictOutcome<?, ?, ?>> included = new ArrayList<>();
		ConflictDocument document = new ConflictDocument() {
			@Override
			public @NotNull ConflictKind<?, ?> kind() {
				return UsernameConflict.KIND;
			}

			@Override
			public void include(@NotNull ConflictOutcome<?, ?, ?> outcome) {
				included.add(outcome);
			}
		};
		Outcome outcome = new Outcome();

		new DefaultConflictService(() -> Set.of(document)).register(outcome);

		assertEquals(List.of(outcome), included);
	}

	@DisplayName("Finds outcomes by kind and id without regard to case, and forgets unregistered ones")
	@Test
	void registersOutcomes() {
		Outcome outcome = new Outcome();
		service.register(outcome);

		assertEquals(Optional.of(outcome), service.findOutcome(UsernameConflict.KIND, "LONGEST-PLAYTIME"));
		assertEquals(List.of(outcome), service.outcomes(UsernameConflict.KIND));
		assertTrue(service.findOutcome(ConflictKind.of("other", Object.class, Object.class), "longest-playtime").isEmpty());

		service.unregister(outcome);
		assertTrue(service.findOutcome(UsernameConflict.KIND, "longest-playtime").isEmpty());
	}

	private UsernameConflictRule match(List<UsernameConflictRule> rules) {
		return service.match(rules, ownedOverChosen).orElseThrow();
	}

	private static final class Outcome implements ConflictOutcome<UsernameConflict, UsernameConflictDecision, NoSettings> {
		@Override
		public @NotNull ConflictKind<UsernameConflict, UsernameConflictDecision> kind() {
			return UsernameConflict.KIND;
		}

		@Override
		public @NotNull String id() {
			return "longest-playtime";
		}

		@Override
		public @NotNull Class<NoSettings> settingsType() {
			return NoSettings.class;
		}

		@Override
		public @NotNull NoSettings defaults() {
			return new NoSettings();
		}

		@Override
		public @NotNull UsernameConflictDecision decide(@NotNull UsernameConflict conflict, @NotNull NoSettings settings) {
			return UsernameConflictDecision.refuse();
		}
	}
}
