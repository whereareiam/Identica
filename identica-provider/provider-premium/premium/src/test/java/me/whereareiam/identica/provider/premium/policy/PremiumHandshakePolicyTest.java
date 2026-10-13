package me.whereareiam.identica.provider.premium.policy;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.auth.handshake.HandshakeRequest;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.config.PremiumSettings;
import me.whereareiam.identica.provider.premium.config.defaults.PremiumMessagesDefaults;
import me.whereareiam.identica.provider.premium.handshake.PremiumHandshakeAttributes;
import me.whereareiam.identica.provider.premium.resolver.PremiumDetector;
import me.whereareiam.identica.provider.premium.resolver.PremiumProfileLookup;
import me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod.CLIENT_PROFILE;
import static me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod.LOOKUP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Premium Handshake Policy")
class PremiumHandshakePolicyTest {
	private static final String USERNAME = "PlayerOne";
	private static final String IP = "127.0.0.1";
	private static final Set<ProviderClaimOrigin> ENTRYPOINT_OR_STRONGER = Set.of(
			ProviderClaimOrigin.MIGRATION,
			ProviderClaimOrigin.ACCOUNT_LINK,
			ProviderClaimOrigin.HOSTNAME
	);

	@Mock
	private PremiumProfileLookup profileLookup;
	@Mock
	private ProviderAttemptStore attemptStore;
	@Mock
	private ProviderSelector providerSelector;

	private final PremiumSettings settings = new PremiumSettings();
	private final PremiumMessages messages = new PremiumMessagesDefaults().supply(new PremiumMessages());
	private PremiumHandshakePolicy policy;

	@BeforeEach
	void setUp() {
		lenient().when(providerSelector.isAtLeast(any(), eq(ProviderClaimOrigin.HOSTNAME))).thenAnswer(invocation ->
				ENTRYPOINT_OR_STRONGER.contains(invocation.getArgument(0, ProviderClaimOrigin.class)));
		policy = new PremiumHandshakePolicy(
				new PremiumDetector(profileLookup, () -> settings),
				attemptStore,
				() -> messages,
				providerSelector
		);
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

	@DisplayName("Asks for a premium login when Premium won the selection with its hostname")
	@Test
	void premiumEntrypointForcesPremiumHandshake() {
		HandshakeDecision decision = evaluate(request(null, selection("premium", ProviderClaimOrigin.HOSTNAME), null, JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
		verify(profileLookup, never()).findProfileId(any());
	}

	@DisplayName("Asks for a premium login when a pending migration to Premium won the selection")
	@Test
	void pendingMigrationToPremiumForcesPremiumHandshake() {
		HandshakeDecision decision = evaluate(request(null, pendingMigrationToPremium(), null, JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
	}

	@DisplayName("Does not ask for a premium login only because Premium discovered a profile for the name")
	@Test
	void premiumDiscoveryDoesNotForcePremiumHandshake() {
		HandshakeDecision decision = evaluate(request(null, selection("premium", ProviderClaimOrigin.DISCOVERY), null, JourneyMode.INTERACTIVE));

		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(profileLookup, never()).findProfileId(any());
	}

	@DisplayName("Keeps asking a premium account for a premium login while its pending migration selected another provider")
	@Test
	void pendingMigrationAwayFromPremiumStillForcesOnline() {
		ProviderSelection credential = selection("credential", ProviderClaimOrigin.MIGRATION);

		HandshakeDecision decision = evaluate(request(null, credential, link("premium", UUID.randomUUID()), JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
	}

	@DisplayName("Does not force premium again when a verify attempt already exists")
	@Test
	void existingVerifyAttemptSkipsManualPremiumRequeue() {
		when(attemptStore.hasAttempt("premium", "verify", USERNAME, IP)).thenReturn(true);

		HandshakeDecision decision = evaluate(request(null, pendingMigrationToPremium(), null, JourneyMode.SEAMLESS));

		assertEquals(Optional.empty(), forcesOnline(decision));
		verify(profileLookup, never()).findProfileId(any());
	}

	@DisplayName("Keeps asking an account that prefers premium for a premium login despite an existing verify attempt")
	@Test
	void existingVerifyAttemptDoesNotSkipThePremiumLoginOfAPremiumAccount() {
		HandshakeDecision decision = evaluate(request(null, null, link("premium", UUID.randomUUID()), JourneyMode.SEAMLESS));

		assertEquals(Optional.of(true), forcesOnline(decision));
		verify(attemptStore, never()).hasAttempt(anyString(), anyString(), anyString(), anyString());
	}

	@DisplayName("Lets a client fall back from a pending move to premium once its premium login was asked for")
	@Test
	void existingVerifyAttemptFallsBackFromAPendingMoveToPremium() {
		when(attemptStore.hasAttempt("premium", "verify", USERNAME, IP)).thenReturn(true);

		HandshakeDecision decision = evaluate(request(null, pendingMigrationToPremium(), link("credential", UUID.randomUUID()), JourneyMode.SEAMLESS));

		assertEquals(HandshakeDecision.Status.ALLOW, decision.getStatus());
		assertEquals(Optional.empty(), forcesOnline(decision));
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
			ProviderSelection selection,
			AccountProviderLink preferredLink,
			JourneyMode journeyMode
	) {
		ConnectionIdentity identity = new ConnectionIdentity(USERNAME, IP);
		identity.setClaimedUniqueId(claimedUniqueId);

		return new HandshakeRequest(identity, selection != null ? selection : ProviderSelection.empty(), preferredLink, journeyMode);
	}

	private ProviderSelection pendingMigrationToPremium() {
		return selection("premium", ProviderClaimOrigin.MIGRATION);
	}

	private ProviderSelection selection(String providerId, ProviderClaimOrigin origin) {
		return ProviderSelection.of(ProviderClaim.of(providerId, origin), List.of(), List.of());
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
