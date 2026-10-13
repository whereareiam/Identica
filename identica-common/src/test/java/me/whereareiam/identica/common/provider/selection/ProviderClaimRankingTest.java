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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Provider Claim Ranking")
class ProviderClaimRankingTest {
	private static final List<ProviderClaimOrigin> STRONGEST_FIRST = List.of(
			ProviderClaimOrigin.MIGRATION,
			ProviderClaimOrigin.ACCOUNT_LINK,
			ProviderClaimOrigin.HOSTNAME,
			ProviderClaimOrigin.VERIFIED_SUBJECT,
			ProviderClaimOrigin.PLAYER_CHOICE,
			ProviderClaimOrigin.NAME_MATCH,
			ProviderClaimOrigin.DISCOVERY
	);

	private final ProviderManager providerManager = mock(ProviderManager.class);
	private DefaultProviderSelector selector;

	@BeforeEach
	void setUp() {
		when(providerManager.getProviders()).thenReturn(List.of(
				provider("weak", 1),
				provider("strong", 100),
				provider("alpha", 50),
				provider("beta", 50)
		));
		selector = new DefaultProviderSelector(new ProviderClaimRanking(providerManager));
	}

	static Stream<Arguments> rankPairs() {
		List<Arguments> pairs = new ArrayList<>();
		for (int stronger = 0; stronger < STRONGEST_FIRST.size(); stronger++)
			for (int weaker = stronger + 1; weaker < STRONGEST_FIRST.size(); weaker++)
				pairs.add(Arguments.of(STRONGEST_FIRST.get(stronger), STRONGEST_FIRST.get(weaker)));

		return pairs.stream();
	}

	@DisplayName("A stronger origin wins over a weaker one, even for a provider of lower priority")
	@ParameterizedTest(name = "{0} beats {1}")
	@MethodSource("rankPairs")
	void strongerOriginWins(ProviderClaimOrigin stronger, ProviderClaimOrigin weaker) {
		ProviderSelection selection = selector.claim(ProviderSelection.empty(), List.of(
				claim("strong", weaker),
				claim("weak", stronger)
		));

		assertEquals("weak", selection.getWinnerProviderId());
		assertEquals(stronger, selection.getOrigin());
		assertEquals(List.of(claim("strong", weaker)), selection.getBeaten());
		assertTrue(selector.isAtLeast(stronger, weaker));
		assertFalse(selector.isAtLeast(weaker, stronger));
	}

	@DisplayName("Claims of the same origin rank by provider priority")
	@Test
	void sameOriginRanksByPriority() {
		ProviderSelection selection = selector.claim(ProviderSelection.empty(), List.of(
				claim("weak", ProviderClaimOrigin.DISCOVERY),
				claim("strong", ProviderClaimOrigin.DISCOVERY)
		));

		assertEquals("strong", selection.getWinnerProviderId());
	}

	@DisplayName("Claims of the same origin and priority rank by provider id")
	@Test
	void sameOriginAndPriorityRanksById() {
		ProviderSelection selection = selector.claim(ProviderSelection.empty(), List.of(
				claim("beta", ProviderClaimOrigin.HOSTNAME),
				claim("alpha", ProviderClaimOrigin.HOSTNAME)
		));

		assertEquals("alpha", selection.getWinnerProviderId());
	}

	@DisplayName("An origin is at least as strong as itself")
	@Test
	void originIsAtLeastItself() {
		for (ProviderClaimOrigin origin : ProviderClaimOrigin.values())
			assertTrue(selector.isAtLeast(origin, origin));
		assertFalse(selector.isAtLeast(null, ProviderClaimOrigin.DISCOVERY));
	}

	private static ProviderClaim claim(String providerId, ProviderClaimOrigin origin) {
		return origin == ProviderClaimOrigin.VERIFIED_SUBJECT
				? ProviderClaim.verified(providerId, providerId + "-proof")
				: ProviderClaim.of(providerId, origin);
	}

	static InternalProvider provider(String id, int priority) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(id);
		return InternalProvider.builder()
				.descriptor(descriptor)
				.priority(priority)
				.state(ProviderState.ENABLED)
				.build();
	}
}
