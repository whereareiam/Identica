package me.whereareiam.identica.provider.premium.policy;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.auth.handshake.HandshakeRequest;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.config.PremiumSettings;
import me.whereareiam.identica.provider.premium.config.defaults.PremiumMessagesDefaults;
import me.whereareiam.identica.provider.premium.handshake.PremiumHandshakeAttributes;
import me.whereareiam.identica.provider.premium.resolver.PremiumDetector;
import me.whereareiam.identica.provider.premium.resolver.PremiumProfileLookup;
import me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod.CLIENT_PROFILE;
import static me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod.LOOKUP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

	private final PremiumSettings settings = new PremiumSettings();
	private final PremiumMessages messages = new PremiumMessagesDefaults().supply(new PremiumMessages());
	private PremiumHandshakePolicy policy;

	@BeforeEach
	void setUp() {
		policy = new PremiumHandshakePolicy(new PremiumDetector(profileLookup, () -> settings), attemptStore, () -> messages);
	}

	@DisplayName("Asks for a premium login when the account prefers premium")
	@Test
	void premiumPreferredLinkForcesOnline() {
		HandshakeDecision decision = evaluate(request(null, null, link("premium", UUID.randomUUID()), JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
		verify(profileLookup, never()).findProfileId(any());
	}

	@DisplayName("Lets an account that prefers another provider join without a premium login")
	@Test
	void otherPreferredLinkAllowsWithoutPremiumLogin() {
		HandshakeDecision decision = evaluate(request(null, null, link("credential", UUID.randomUUID()), JourneyMode.SEAMLESS));

		assertEquals(HandshakeDecision.Status.ALLOW, decision.getStatus());
		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(profileLookup, never()).findProfileId(any());
	}

	@DisplayName("Forces premium when provider context is explicitly set to the premium provider")
	@Test
	void manualPremiumProviderContextForcesPremiumHandshake() {
		HandshakeDecision decision = evaluate(request(null, manualPremium(), null, JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
		verify(profileLookup, never()).findProfileId(any());
	}

	@DisplayName("Does not force premium again when a verify attempt already exists")
	@Test
	void existingVerifyAttemptSkipsManualPremiumRequeue() {
		when(attemptStore.hasAttempt("premium", "verify", USERNAME, IP)).thenReturn(true);

		HandshakeDecision decision = evaluate(request(null, manualPremium(), null, JourneyMode.SEAMLESS));

		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(profileLookup, never()).findProfileId(any());
	}

	@DisplayName("Asks an unknown premium username for a premium login when lookup is the only method")
	@Test
	void lookupForcesOnlineForAPremiumUsername() {
		when(profileLookup.findProfileId(USERNAME)).thenReturn(profile(UUID.randomUUID()));

		HandshakeDecision decision = evaluate(request(null, null, null, JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
		verify(attemptStore).markAttempt("premium", "verify", USERNAME, IP);
	}

	@DisplayName("Asks for a premium login when the client claims the premium profile of its username")
	@Test
	void claimedProfileMatchForcesOnline() {
		UUID premium = UUID.randomUUID();
		detection(CLIENT_PROFILE, LOOKUP);
		when(profileLookup.findProfileId(USERNAME)).thenReturn(profile(premium));

		HandshakeDecision decision = evaluate(request(premium, null, null, JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
		verify(attemptStore).markAttempt("premium", "verify", USERNAME, IP);
	}

	@DisplayName("Lets a client that claims another profile continue without a premium login or a verify attempt")
	@Test
	void claimedProfileMismatchSkipsThePremiumLogin() {
		detection(CLIENT_PROFILE, LOOKUP);
		when(profileLookup.findProfileId(USERNAME)).thenReturn(profile(UUID.randomUUID()));

		HandshakeDecision decision = evaluate(request(UUID.randomUUID(), null, null, JourneyMode.SEAMLESS));

		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(attemptStore, never()).markAttempt(anyString(), anyString(), anyString(), anyString());
	}

	@DisplayName("Checks a mismatch again with a fresh lookup when configured")
	@Test
	void recheckOnMismatchUsesAFreshLookup() {
		UUID claimed = UUID.randomUUID();
		detection(CLIENT_PROFILE);
		settings.getDetection().setRecheckOnMismatch(true);
		when(profileLookup.findProfileId(USERNAME)).thenReturn(profile(UUID.randomUUID()));
		when(profileLookup.fetchProfileId(USERNAME)).thenReturn(profile(claimed));

		HandshakeDecision decision = evaluate(request(claimed, null, null, JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
	}

	@DisplayName("Leaves a client that claims no profile to the next method")
	@Test
	void unclaimedProfileFallsBackToLookup() {
		detection(CLIENT_PROFILE, LOOKUP);
		when(profileLookup.findProfileId(USERNAME)).thenReturn(profile(UUID.randomUUID()));

		HandshakeDecision decision = evaluate(request(null, null, null, JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
	}

	@DisplayName("Treats a client that claims no profile as not premium when no method decides")
	@Test
	void unclaimedProfileIsNotPremiumWithoutLookup() {
		detection(CLIENT_PROFILE);

		HandshakeDecision decision = evaluate(request(null, null, null, JourneyMode.SEAMLESS));

		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(profileLookup, never()).findProfileId(any());
	}

	@DisplayName("Leaves an unknown username of an interactive journey to the provider choice")
	@Test
	void interactiveJourneyLeavesTheChoiceToThePlayer() {
		detection(CLIENT_PROFILE, LOOKUP);

		HandshakeDecision decision = evaluate(request(UUID.randomUUID(), null, null, JourneyMode.INTERACTIVE));

		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(profileLookup, never()).findProfileId(any());
	}

	@DisplayName("Refuses a client that does not own a username linked to premium")
	@Test
	void linkedUsernameRefusesAnotherClient() {
		UUID linked = UUID.randomUUID();
		detection(CLIENT_PROFILE, LOOKUP);
		when(profileLookup.findProfileId(USERNAME)).thenReturn(profile(linked));

		HandshakeDecision decision = evaluate(request(UUID.randomUUID(), null, link("premium", linked), JourneyMode.SEAMLESS));

		assertEquals(HandshakeDecision.Status.DENY, decision.getStatus());
		assertTrue(decision.getMessage().contains("This username belongs to a premium account."));
	}

	@DisplayName("Asks the owner of a linked username for a premium login")
	@Test
	void linkedUsernameForcesOnlineForItsOwner() {
		UUID linked = UUID.randomUUID();
		detection(CLIENT_PROFILE, LOOKUP);

		HandshakeDecision decision = evaluate(request(linked, null, link("premium", linked), JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
		verify(profileLookup, never()).findProfileId(any());
	}

	@DisplayName("Asks for a premium login when a linked username now belongs to the claimed profile")
	@Test
	void linkedUsernameFollowsTheCurrentOwner() {
		UUID claimed = UUID.randomUUID();
		detection(CLIENT_PROFILE, LOOKUP);
		when(profileLookup.findProfileId(USERNAME)).thenReturn(profile(claimed));

		HandshakeDecision decision = evaluate(request(claimed, null, link("premium", UUID.randomUUID()), JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
	}

	@DisplayName("Keeps asking a linked username for a premium login without the client profile method")
	@Test
	void linkedUsernameWithoutClientProfileForcesOnline() {
		HandshakeDecision decision = evaluate(request(UUID.randomUUID(), null, link("premium", UUID.randomUUID()), JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
	}

	private HandshakeDecision evaluate(HandshakeRequest request) {
		return policy.evaluate(request).toCompletableFuture().join();
	}

	private Optional<Boolean> forcesOnline(HandshakeDecision decision) {
		return decision.getAttribute(PremiumHandshakeAttributes.FORCE_ONLINE);
	}

	private void detection(PremiumDetectionMethod... methods) {
		settings.getDetection().setMethods(List.of(methods));
	}

	private CompletableFuture<Optional<UUID>> profile(UUID profileId) {
		return CompletableFuture.completedFuture(Optional.of(profileId));
	}

	private HandshakeRequest request(
			UUID claimedUniqueId,
			ProviderContext provider,
			AccountProviderLink preferredLink,
			JourneyMode journeyMode
	) {
		ConnectionIdentity identity = new ConnectionIdentity(USERNAME, IP);
		identity.setClaimedUniqueId(claimedUniqueId);

		return new HandshakeRequest(identity, provider, preferredLink, journeyMode);
	}

	private ProviderContext manualPremium() {
		return ProviderContext.of("premium", null, USERNAME, ProviderOrigin.MANUAL);
	}

	private AccountProviderLink link(String providerId, UUID subject) {
		return AccountProviderLink.builder()
				.uniqueId(UUID.randomUUID())
				.providerId(providerId)
				.providerSubject(subject.toString())
				.primaryLink(true)
				.build();
	}
}
