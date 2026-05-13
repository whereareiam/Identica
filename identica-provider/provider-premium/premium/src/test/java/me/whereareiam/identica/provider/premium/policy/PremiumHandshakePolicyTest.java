package me.whereareiam.identica.provider.premium.policy;

import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.auth.handshake.HandshakeRequest;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileSnapshot;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import me.whereareiam.identica.provider.premium.resolver.PremiumProfileLookup;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import me.whereareiam.identica.type.provider.ProviderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Premium Handshake Policy")
class PremiumHandshakePolicyTest {
	@Mock
	private AccountPersistenceService accountPersistenceService;
	@Mock
	private PremiumProfileLookup profileLookup;
	@Mock
	private ProviderManager providerManager;
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;
	@Mock
	private PremiumProfileStore profileStore;
	@Mock
	private ProviderAttemptStore attemptStore;
	@Mock
	private HandshakeStore handshakeStore;

	private PremiumHandshakePolicy policy;

	@BeforeEach
	void setUp() {
		policy = new PremiumHandshakePolicy(
				accountPersistenceService,
				profileLookup,
				providerManager,
				providerLinkPersistenceService,
				profileStore,
				attemptStore,
				this::settings,
				handshakeStore
		);
	}

	@DisplayName("Prefers a primary link over provider priority during premium handshake decisions")
	@Test
	void primaryLinkWinsOverProviderPriority() {
		UUID uniqueId = UUID.randomUUID();
		String username = "PlayerOne";

		when(attemptStore.hasAttempt("premium", "verify", username, "127.0.0.1")).thenReturn(false);
		when(profileStore.find(username)).thenReturn(new PremiumProfileSnapshot("premium-subject", System.currentTimeMillis()));
		when(providerLinkPersistenceService.findBySubject("premium", "premium-subject"))
				.thenReturn(Optional.of(link(uniqueId, "premium", false)));
		when(providerLinkPersistenceService.findByUniqueId(uniqueId)).thenReturn(List.of(
				link(uniqueId, "premium", false),
				link(uniqueId, "password", true)
		));

		HandshakeDecision decision = policy.evaluate(request(username)).toCompletableFuture().join();

		assertEquals(HandshakeDecision.Status.ALLOW, decision.getStatus());
		verify(handshakeStore, never()).putInstruction(any());
		verify(profileLookup, never()).hasPremiumProfile(username);
	}

	@DisplayName("Falls back to provider priority when no primary link exists")
	@Test
	void providerPriorityBreaksTiesWhenNoPrimaryLinkExists() {
		UUID uniqueId = UUID.randomUUID();
		String username = "PlayerOne";

		when(attemptStore.hasAttempt("premium", "verify", username, "127.0.0.1")).thenReturn(false);
		when(providerManager.getProviders()).thenReturn(List.of(
				provider("premium", 100),
				provider("password", 50)
		));
		when(profileStore.find(username)).thenReturn(new PremiumProfileSnapshot("premium-subject", System.currentTimeMillis()));
		when(providerLinkPersistenceService.findBySubject("premium", "premium-subject"))
				.thenReturn(Optional.of(link(uniqueId, "premium", false)));
		when(providerLinkPersistenceService.findByUniqueId(uniqueId)).thenReturn(List.of(
				link(uniqueId, "premium", false),
				link(uniqueId, "password", false)
		));

		HandshakeDecision decision = policy.evaluate(request(username)).toCompletableFuture().join();

		assertEquals(HandshakeDecision.Status.ALLOW, decision.getStatus());
		verify(handshakeStore).putInstruction(any());
		verify(profileLookup, never()).hasPremiumProfile(username);
	}

	@DisplayName("Forces premium when provider context is explicitly set to the premium provider")
	@Test
	void manualPremiumProviderContextForcesPremiumHandshake() {
		String username = "PlayerOne";

		HandshakeDecision decision = policy.evaluate(request(username, ProviderContext.of(
				"premium",
				null,
				username,
				ProviderOrigin.MANUAL
		))).toCompletableFuture().join();

		assertEquals(HandshakeDecision.Status.ALLOW, decision.getStatus());
		verify(handshakeStore).putInstruction(any());
		verify(profileLookup, never()).hasPremiumProfile(username);
	}

	private HandshakeRequest request(String username) {
		return request(username, null);
	}

	private HandshakeRequest request(String username, ProviderContext provider) {
		return new HandshakeRequest(new ConnectionIdentity(username, "127.0.0.1"), provider);
	}

	private Settings settings() {
		Settings.AuthenticationScenario authentication = new Settings.AuthenticationScenario();
		authentication.setJourneyMode(JourneyMode.SEAMLESS);

		Settings.Connection connection = new Settings.Connection();
		connection.setHandshakeInstructionTtl(Duration.ofSeconds(30));
		connection.setAuthentication(authentication);

		Settings settings = new Settings();
		settings.setConnection(connection);
		return settings;
	}

	private AccountProviderLink link(UUID uniqueId, String providerId, boolean primary) {
		return AccountProviderLink.builder()
				.uniqueId(uniqueId)
				.providerId(providerId)
				.providerSubject(providerId + "-subject")
				.primaryLink(primary)
				.build();
	}

	private InternalProvider provider(String providerId, int priority) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(providerId);
		return InternalProvider.builder()
				.descriptor(descriptor)
				.priority(priority)
				.state(ProviderState.ENABLED)
				.build();
	}
}
