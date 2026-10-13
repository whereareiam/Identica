package me.whereareiam.identica.engine.pipeline.username;

import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.policy.phase.PersistAuthenticationUsernamePhase;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.identity.UsernameChangedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.shared.item.UsernameStateItem;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.PolicyState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderState;
import me.whereareiam.identica.type.provider.ProviderTrait;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Persist Username Phase")
class AbstractPersistUsernamePhaseTest {
	private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID HOLDER = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private final AccountPersistenceService accounts = mock(AccountPersistenceService.class);
	private final AccountUsernamePersistenceService usernames = mock(AccountUsernamePersistenceService.class);
	private final EventManager events = mock(EventManager.class);
	private final PersistAuthenticationUsernamePhase phase = new PersistAuthenticationUsernamePhase(
			providerManager(),
			usernames,
			accounts,
			events
	);

	@Test
	@DisplayName("Keeps the old username when the provider renames the account onto a username another account holds")
	void keepsTheUsernameWhenTheNewOneIsHeld() {
		when(accounts.findByUsername("Alice")).thenReturn(List.of(Account.builder().uniqueId(HOLDER).username("alice").build()));
		PipelineState pipelineState = pipelineState("Alice");

		run(pipelineState);

		verify(usernames, never()).rename(any());
		verify(events, never()).call(any(UsernameChangedEvent.class));
		assertEquals("Bob", pipelineState.getScenario().getUsername());
	}

	@Test
	@DisplayName("Stores the new username when no other account holds it")
	void storesAFreeUsername() {
		when(accounts.findByUsername("Alice")).thenReturn(List.of(Account.builder().uniqueId(ACCOUNT).username("Bob").build()));
		when(usernames.rename(any())).thenReturn(true);
		PipelineState pipelineState = pipelineState("Alice");

		run(pipelineState);

		ArgumentCaptor<AccountUsernameHistoryEntry> change = ArgumentCaptor.forClass(AccountUsernameHistoryEntry.class);
		verify(usernames).rename(change.capture());
		assertEquals("Bob", change.getValue().getOldUsername());
		assertEquals("Alice", change.getValue().getNewUsername());
		assertEquals("vouched", change.getValue().getProviderId());
		verify(events).call(any(UsernameChangedEvent.class));
		assertEquals("Alice", pipelineState.getScenario().getUsername());
	}

	@Test
	@DisplayName("Keeps the old username when the database refuses the rename")
	void keepsTheUsernameWhenTheRenameFails() {
		when(accounts.findByUsername("Alice")).thenReturn(List.of());
		when(usernames.rename(any())).thenReturn(false);
		PipelineState pipelineState = pipelineState("Alice");

		run(pipelineState);

		verify(events, never()).call(any(UsernameChangedEvent.class));
		assertEquals("Bob", pipelineState.getScenario().getUsername());
	}

	private void run(PipelineState pipelineState) {
		PolicyState state = new PolicyState();
		state.setResult(PipelineResult.complete());
		phase.execute(pipelineState, state).toCompletableFuture().join();
	}

	private static PipelineState pipelineState(String providerUsername) {
		PipelineState pipelineState = PipelineState.initial();
		pipelineState.setPipelineType(PipelineType.AUTHENTICATION);
		pipelineState.setScenario(AuthContext.builder()
				.identity(new ConnectionIdentity(providerUsername, "127.0.0.1"))
				.build());
		pipelineState.put(UsernameStateItem.KEY, UsernameStateItem.builder()
				.uniqueId(ACCOUNT)
				.previousUsername("Bob")
				.providerId("vouched")
				.providerSubject("subject")
				.source(AccountUsernameSource.PROVIDER)
				.build());

		return pipelineState;
	}

	private static ProviderManager providerManager() {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId("vouched");
		descriptor.setTraits(Set.of(ProviderTrait.AUTHORITATIVE_USERNAME));
		ProviderManager providerManager = mock(ProviderManager.class);
		when(providerManager.getProviders()).thenReturn(List.of(InternalProvider.builder().descriptor(descriptor).state(ProviderState.ENABLED).build()));

		return providerManager;
	}
}
