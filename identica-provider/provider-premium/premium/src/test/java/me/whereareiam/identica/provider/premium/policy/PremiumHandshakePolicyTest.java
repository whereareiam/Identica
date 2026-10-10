package me.whereareiam.identica.provider.premium.policy;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.auth.handshake.HandshakeRequest;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.premium.handshake.PremiumHandshakeAttributes;
import me.whereareiam.identica.provider.premium.resolver.PremiumProfileLookup;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Premium Handshake Policy")
class PremiumHandshakePolicyTest {
	private static final String USERNAME = "PlayerOne";
	private static final String IP = "127.0.0.1";

	@Mock
	private PremiumProfileLookup profileLookup;
	@Mock
	private ProviderAttemptStore attemptStore;

	private PremiumHandshakePolicy policy;

	@BeforeEach
	void setUp() {
		policy = new PremiumHandshakePolicy(profileLookup, attemptStore);
	}

	@DisplayName("Asks for a premium login when the account prefers premium")
	@Test
	void premiumPreferredLinkForcesOnline() {
		HandshakeDecision decision = evaluate(request(null, link("premium"), JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
		verify(profileLookup, never()).hasPremiumProfile(any());
	}

	@DisplayName("Lets an account that prefers another provider join without a premium login")
	@Test
	void otherPreferredLinkAllowsWithoutPremiumLogin() {
		HandshakeDecision decision = evaluate(request(null, link("credential"), JourneyMode.SEAMLESS));

		assertEquals(HandshakeDecision.Status.ALLOW, decision.getStatus());
		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(profileLookup, never()).hasPremiumProfile(any());
	}

	@DisplayName("Forces premium when provider context is explicitly set to the premium provider")
	@Test
	void manualPremiumProviderContextForcesPremiumHandshake() {
		HandshakeDecision decision = evaluate(request(manualPremium(), null, JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
		verify(profileLookup, never()).hasPremiumProfile(any());
	}

	@DisplayName("Does not force premium again when a verify attempt already exists")
	@Test
	void existingVerifyAttemptSkipsManualPremiumRequeue() {
		when(attemptStore.hasAttempt("premium", "verify", USERNAME, IP)).thenReturn(true);

		HandshakeDecision decision = evaluate(request(manualPremium(), null, JourneyMode.SEAMLESS));

		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(profileLookup, never()).hasPremiumProfile(any());
	}

	@DisplayName("Asks an unknown premium username for a premium login and marks a verify attempt")
	@Test
	void unknownPremiumUsernameForcesOnline() {
		when(profileLookup.hasPremiumProfile(USERNAME)).thenReturn(CompletableFuture.completedFuture(true));

		HandshakeDecision decision = evaluate(request(null, null, JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
		verify(attemptStore).markAttempt("premium", "verify", USERNAME, IP);
	}

	@DisplayName("Lets an unknown username without a premium profile join without a premium login")
	@Test
	void unknownUsernameWithoutPremiumProfileAllows() {
		when(profileLookup.hasPremiumProfile(USERNAME)).thenReturn(CompletableFuture.completedFuture(false));

		HandshakeDecision decision = evaluate(request(null, null, JourneyMode.SEAMLESS));

		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(attemptStore, never()).markAttempt(anyString(), anyString(), anyString(), anyString());
	}

	@DisplayName("Leaves an unknown username of an interactive journey to the provider choice")
	@Test
	void interactiveJourneyLeavesTheChoiceToThePlayer() {
		HandshakeDecision decision = evaluate(request(null, null, JourneyMode.INTERACTIVE));

		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(profileLookup, never()).hasPremiumProfile(any());
	}

	private HandshakeDecision evaluate(HandshakeRequest request) {
		return policy.evaluate(request).toCompletableFuture().join();
	}

	private Optional<Boolean> forcesOnline(HandshakeDecision decision) {
		return decision.getAttribute(PremiumHandshakeAttributes.FORCE_ONLINE);
	}

	private HandshakeRequest request(ProviderContext provider, AccountProviderLink preferredLink, JourneyMode journeyMode) {
		return new HandshakeRequest(new ConnectionIdentity(USERNAME, IP), provider, preferredLink, journeyMode);
	}

	private ProviderContext manualPremium() {
		return ProviderContext.of("premium", null, USERNAME, ProviderOrigin.MANUAL);
	}

	private AccountProviderLink link(String providerId) {
		return AccountProviderLink.builder()
				.uniqueId(UUID.randomUUID())
				.providerId(providerId)
				.providerSubject(providerId + "-subject")
				.primaryLink(true)
				.build();
	}
}
