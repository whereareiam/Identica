package me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase;

import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.engine.pipeline.prepare.runtime.ConnectionProviderContextResolver;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.ResolvedEntrypoint;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Resolve Profile Phase")
class ResolveProfilePhaseTest {
	private static final String USERNAME = "PlayerOne";
	private static final String HOST = "credential.example.test";
	private static final SubjectResolution DISCOVERED = subject("premium", "premium-subject");
	private static final SubjectResolution SELECTED = subject("credential", "credential-subject");

	@Mock
	private ProviderOperations providerOperations;
	@Mock
	private ProviderLinkPersistenceService providerLinkPersistenceService;
	@Mock
	private AccountPersistenceService accountPersistenceService;

	@BeforeEach
	void joinsWithoutAnAccount() {
		when(providerOperations.discoverSubject(any())).thenReturn(DISCOVERED);
		when(providerOperations.resolveEntrypoint(eq(HOST), anyInt())).thenReturn(ResolvedEntrypoint.builder()
				.providerId("credential")
				.host(HOST)
				.build());
		when(providerOperations.resolveSelectedSubject(eq("credential"), any())).thenReturn(SELECTED);
		when(providerLinkPersistenceService.findBySubject(anyString(), anyString())).thenReturn(Optional.empty());
		when(accountPersistenceService.findByUsername(anyString())).thenReturn(List.of());
	}

	@DisplayName("Carries a subject its provider verified into the provider context")
	@Test
	void carriesAVerifiedSubject() {
		assertTrue(resolved(true).isSubjectVerified());
	}

	@DisplayName("Keeps a subject its provider only derived unverified")
	@Test
	void keepsADerivedSubjectUnverified() {
		assertFalse(resolved(false).isSubjectVerified());
	}

	@DisplayName("A new player who joins through a provider's hostname continues with that provider")
	@Test
	void newPlayerContinuesWithTheHostnameProvider() {
		ProviderContext provider = throughHostname();

		assertEquals("credential", provider.getProviderId());
		assertEquals("credential-subject", provider.getProviderSubject());
		assertEquals(ProviderOrigin.ENTRYPOINT, provider.getSource());
	}

	@DisplayName("A player whose account uses the hostname's provider continues with it")
	@Test
	void accountOfTheHostnameProviderContinuesWithIt() {
		when(providerLinkPersistenceService.findBySubject("credential", "credential-subject")).thenReturn(Optional.of(link("credential")));
		when(providerLinkPersistenceService.findBySubject("premium", "premium-subject")).thenReturn(Optional.of(link("premium")));

		assertEquals("credential", throughHostname().getProviderId());
	}

	@DisplayName("A hostname does not move an existing account to a provider it does not use")
	@Test
	void existingAccountKeepsItsProvider() {
		when(providerLinkPersistenceService.findBySubject("premium", "premium-subject")).thenReturn(Optional.of(link("premium")));

		assertEquals("premium", throughHostname().getProviderId());
	}

	@DisplayName("A hostname does not create a second account for a username that already has one")
	@Test
	void usernameWithAnAccountKeepsTheDiscoveredProvider() {
		when(accountPersistenceService.findByUsername(USERNAME)).thenReturn(List.of(Account.builder()
				.uniqueId(UUID.randomUUID())
				.username(USERNAME)
				.build()));

		assertEquals("premium", throughHostname().getProviderId());
	}

	@DisplayName("A hostname whose provider resolves no subject leaves the discovered one")
	@Test
	void hostnameWithoutSubjectKeepsTheDiscoveredProvider() {
		when(providerOperations.resolveSelectedSubject(eq("credential"), any())).thenReturn(null);

		assertEquals("premium", throughHostname().getProviderId());
	}

	@DisplayName("A hostname does not override a provider chosen otherwise, such as by a pending migration")
	@Test
	void pendingMigrationKeepsTheDiscoveredProvider() {
		PrepareContextItem context = new PrepareContextItem();
		context.setProvider(ProviderContext.builder()
				.providerId("premium")
				.providerUsername(USERNAME)
				.source(ProviderOrigin.MANUAL)
				.build());

		assertEquals("premium", throughHostname(context).getProviderId());
	}

	private ProviderContext throughHostname() {
		return throughHostname(null);
	}

	private ProviderContext throughHostname(PrepareContextItem context) {
		ConnectionIdentity identity = new ConnectionIdentity(USERNAME, "127.0.0.1");
		identity.setOrigin(new ConnectionIdentity.Origin(HOST, 25565));
		return run(identity, context);
	}

	private ProviderContext resolved(boolean verified) {
		when(providerOperations.discoverSubject(any())).thenReturn(SubjectResolution.builder()
				.providerId("token")
				.providerSubject("token-subject")
				.verified(verified)
				.build());

		ProviderContext provider = run(new ConnectionIdentity(USERNAME, "127.0.0.1"), null);
		assertEquals("token", provider.getProviderId());
		assertEquals("token-subject", provider.getProviderSubject());
		return provider;
	}

	private ProviderContext run(ConnectionIdentity identity, PrepareContextItem context) {
		PrepareGroupState groupState = new PrepareGroupState();
		groupState.setRequest(PrepareRequest.builder()
				.connectionKey("PlayerOne|127.0.0.1||")
				.stage(PrepareStage.PROFILE)
				.identity(identity)
				.build());

		PipelineState state = PipelineState.initial();
		if (context != null) state.put(PrepareContextItem.KEY, context);
		new ResolveProfilePhase(
				providerOperations,
				new ConnectionProviderContextResolver(providerOperations),
				providerLinkPersistenceService,
				accountPersistenceService
		).execute(state, groupState).toCompletableFuture().join();

		ProviderContext provider = state.get(PrepareContextItem.KEY).orElseThrow().getProvider();
		assertNotNull(provider);
		return provider;
	}

	private static SubjectResolution subject(String providerId, String subject) {
		return SubjectResolution.builder().providerId(providerId).providerSubject(subject).build();
	}

	private static AccountProviderLink link(String providerId) {
		return AccountProviderLink.builder()
				.uniqueId(UUID.randomUUID())
				.providerId(providerId)
				.providerSubject(providerId + "-subject")
				.primaryLink(true)
				.build();
	}
}
