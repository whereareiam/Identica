package me.whereareiam.identica.common.provider.selection;

import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.identica.type.provider.ProviderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Default Provider Selector")
class DefaultProviderSelectorTest {
	private final ProviderManager providerManager = mock(ProviderManager.class);
	private DefaultProviderSelector selector;

	@BeforeEach
	void setUp() {
		InternalProvider disabled = ProviderClaimRankingTest.provider("disabled", 100);
		disabled.setState(ProviderState.DISABLED);
		when(providerManager.getProviders()).thenReturn(List.of(
				ProviderClaimRankingTest.provider("alpha", 30),
				ProviderClaimRankingTest.provider("beta", 20),
				ProviderClaimRankingTest.provider("gamma", 10),
				disabled
		));
		selector = new DefaultProviderSelector(new ProviderClaimRanking(providerManager));
	}

	@DisplayName("Adds a later claim and ranks again instead of replacing the winner")
	@Test
	void addsALaterClaimAndRanksAgain() {
		ProviderSelection hostname = selector.claim(ProviderSelection.empty(), List.of(
				ProviderClaim.of("alpha", ProviderClaimOrigin.HOSTNAME)
		));

		ProviderSelection chosen = selector.claim(hostname, List.of(ProviderClaim.of("beta", ProviderClaimOrigin.PLAYER_CHOICE)));

		assertEquals("alpha", chosen.getWinnerProviderId());
		assertTrue(chosen.hasClaim("beta", ProviderClaimOrigin.PLAYER_CHOICE));
	}

	@DisplayName("Replaces a claim of the same provider and origin")
	@Test
	void replacesAClaimOfTheSameProviderAndOrigin() {
		ProviderSelection first = selector.claim(ProviderSelection.empty(), List.of(ProviderClaim.verified("alpha", "old")));

		ProviderSelection second = selector.claim(first, List.of(ProviderClaim.verified("ALPHA", "new")));

		assertEquals(1, second.claims().size());
		assertEquals("new", second.findVerifiedSubject("alpha").orElseThrow());
	}

	@DisplayName("A disabled provider cannot win, its claim stays among the beaten")
	@Test
	void disabledProviderCannotWin() {
		ProviderSelection selection = selector.claim(ProviderSelection.empty(), List.of(
				ProviderClaim.of("disabled", ProviderClaimOrigin.MIGRATION),
				ProviderClaim.of("gamma", ProviderClaimOrigin.DISCOVERY)
		));

		assertEquals("gamma", selection.getWinnerProviderId());
		assertTrue(selection.hasClaim("disabled", ProviderClaimOrigin.MIGRATION));
	}

	@DisplayName("An excluded provider loses the selection to the next claim")
	@Test
	void excludedProviderLosesTheSelection() {
		ProviderSelection selection = selector.claim(ProviderSelection.empty(), List.of(
				ProviderClaim.of("alpha", ProviderClaimOrigin.HOSTNAME),
				ProviderClaim.of("beta", ProviderClaimOrigin.DISCOVERY)
		));

		ProviderSelection excluded = selector.exclude(selection, "Alpha");

		assertEquals("beta", excluded.getWinnerProviderId());
		assertTrue(excluded.isExcluded("alpha"));
		assertTrue(excluded.hasClaim("alpha", ProviderClaimOrigin.HOSTNAME));
	}

	@DisplayName("Revoking an origin lets the next claim win")
	@Test
	void revokingAnOriginLetsTheNextClaimWin() {
		ProviderSelection selection = selector.claim(ProviderSelection.empty(), List.of(
				ProviderClaim.of("alpha", ProviderClaimOrigin.MIGRATION),
				ProviderClaim.of("beta", ProviderClaimOrigin.NAME_MATCH)
		));

		ProviderSelection revoked = selector.revoke(selection, ProviderClaimOrigin.MIGRATION);

		assertEquals("beta", revoked.getWinnerProviderId());
		assertTrue(revoked.findClaim(ProviderClaimOrigin.MIGRATION).isEmpty());
	}

	@DisplayName("Ranks candidates by their strongest claim, then the unclaimed ones by priority")
	@Test
	void ranksCandidates() {
		ProviderSelection selection = selector.claim(ProviderSelection.empty(), List.of(
				ProviderClaim.of("gamma", ProviderClaimOrigin.NAME_MATCH),
				ProviderClaim.of("gamma", ProviderClaimOrigin.ACCOUNT_LINK),
				ProviderClaim.of("beta", ProviderClaimOrigin.HOSTNAME)
		));

		assertEquals(List.of("gamma", "beta", "alpha"), selector.rank(selection, List.of("alpha", "beta", "gamma")));
		assertEquals(List.of("gamma", "alpha"), selector.rank(selector.exclude(selection, "beta"), List.of("alpha", "beta", "gamma")));
	}

	@DisplayName("Finds the strongest claim among the candidates only")
	@Test
	void findsTheWinnerAmongCandidates() {
		ProviderSelection selection = selector.claim(ProviderSelection.empty(), List.of(
				ProviderClaim.of("alpha", ProviderClaimOrigin.HOSTNAME),
				ProviderClaim.of("beta", ProviderClaimOrigin.NAME_MATCH)
		));

		assertEquals(ProviderClaim.of("beta", ProviderClaimOrigin.NAME_MATCH), selector.winner(selection, List.of("beta", "gamma")));
		assertNull(selector.winner(selection, List.of("gamma")));
	}

	@DisplayName("An empty selection has no winner")
	@Test
	void emptySelectionHasNoWinner() {
		ProviderSelection selection = selector.claim(ProviderSelection.empty(), List.of());

		assertNull(selection.getWinner());
		assertNull(selection.getOrigin());
	}
}
