package me.whereareiam.identica.platform.common.stage.login;

import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import me.whereareiam.identica.platform.common.stage.TextSerializerEngine;
import me.whereareiam.identica.platform.common.stage.PrepareDenialMessages;
import me.whereareiam.identica.platform.common.stage.InMemoryPrepareStateStore;
import me.whereareiam.identica.platform.common.decision.ConnectionDecisionDeliveryCoordinator;
import me.whereareiam.identica.platform.common.decision.ConnectionDecisionApplier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Login Decision Processor")
class LoginDecisionProcessorTest {
	private final UUID connectionUniqueId = UUID.randomUUID();
	private final LoginDecisionProcessor.Request request = new LoginDecisionProcessor.Request(
			connectionUniqueId,
			"Bob",
			"127.0.0.1",
			new ConnectionIdentity.Origin("play.example.com", 25565),
			"lobby"
	);
	private final String connectionKey = "Bob|127.0.0.1|play.example.com|25565";

	private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
	private final IdentityService identityService = mock(IdentityService.class);
	private final InMemoryPrepareStateStore store = new InMemoryPrepareStateStore();
	private final RecordingTarget target = new RecordingTarget();
	private final LoginDecisionProcessor processor = new LoginDecisionProcessor(
			connectionCoordinator,
			identityService,
			store,
			new PreparedRefusal(store, PrepareDenialMessages::messages),
			new ConnectionDecisionApplier(
					PrepareDenialMessages::messages,
					mock(ConnectionDecisionDeliveryCoordinator.class),
					mock(PlatformDeliveryAdapter.class)
			)
	);

	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(TextSerializerEngine::new);
	}

	@DisplayName("Refuses a login a prepare stage refused, before Identica's flow starts")
	@Test
	void preparedRefusalRefusesTheLogin() {
		store.put(connectionUniqueId, connectionKey, PrepareDecision.deny("restricted"));

		process();

		assertEquals(Component.text("restricted"), target.refused.get());
		assertNull(target.denied.get());
		verifyNoInteractions(connectionCoordinator, identityService);
	}

	@DisplayName("Finds a refusal by connection key when the login's unique ID has none")
	@Test
	void preparedRefusalIsFoundByConnectionKey() {
		store.put(UUID.randomUUID(), connectionKey, PrepareDecision.deny("restricted"));

		process();

		assertEquals(Component.text("restricted"), target.refused.get());
		verifyNoInteractions(connectionCoordinator);
	}

	@DisplayName("Processes the login with what the prepare stages resolved and attaches the allowed player")
	@Test
	void allowedLoginIsProcessedWithThePreparedIdentity() {
		UUID accountUniqueId = UUID.randomUUID();
		ProviderContext provider = ProviderContext.of("credential", "bob", "Bobby");
		ProviderSelection selection = ProviderSelection.of(null, List.of(), List.of("premium"));
		store.put(connectionUniqueId, connectionKey, PrepareDecision.builder()
				.status(PrepareDecision.Status.ALLOW)
				.accountUniqueId(accountUniqueId)
				.provider(provider)
				.selection(selection)
				.build());
		when(connectionCoordinator.process(any())).thenReturn(CompletableFuture.completedFuture(ConnectionDecision.allow()));

		process();

		ArgumentCaptor<ConnectionRequest> captor = ArgumentCaptor.forClass(ConnectionRequest.class);
		verify(connectionCoordinator).process(captor.capture());
		ConnectionRequest processed = captor.getValue();
		assertEquals(provider, processed.getProvider());
		assertEquals(selection, processed.getSelection());
		assertEquals("lobby", processed.getIntendedServer());
		assertEquals("Bobby", processed.getIdentity().getUsername());
		assertEquals(accountUniqueId, processed.getIdentity().getAccountUniqueId());
		assertEquals(connectionUniqueId, processed.getIdentity().getConnectionUniqueId());
		assertEquals(request.origin(), processed.getIdentity().getOrigin());
		assertNull(target.refused.get());
		verify(identityService).attach(connectionUniqueId, accountUniqueId, target.identity);
	}

	@DisplayName("Keeps the connection's username on the live identity when the connection flow renames the player")
	@Test
	void renamedLoginKeepsTheConnectionUsername() {
		when(connectionCoordinator.process(any())).thenAnswer(invocation -> {
			ConnectionRequest processed = invocation.getArgument(0);
			processed.getIdentity().setUsername("Bob_1");
			return CompletableFuture.completedFuture(ConnectionDecision.allow());
		});

		process();

		assertEquals("Bob_1", target.username.get());
		verify(target.identity).setConnectionUsername("Bob");
	}

	@DisplayName("Denies a login the connection flow denies, without attaching the player")
	@Test
	void deniedDecisionIsAppliedThroughTheTarget() {
		when(connectionCoordinator.process(any())).thenReturn(CompletableFuture.completedFuture(ConnectionDecision.deny("no")));

		process();

		assertEquals(Component.text("no"), target.denied.get());
		assertNull(target.refused.get());
		verify(identityService, never()).attach(any(), any(), any());
	}

	private void process() {
		processor.process(request, target).toCompletableFuture().join();
	}

	private static final class RecordingTarget implements LoginDecisionProcessor.Target {
		private final AtomicReference<Component> refused = new AtomicReference<>();
		private final AtomicReference<Component> denied = new AtomicReference<>();
		private final AtomicReference<String> username = new AtomicReference<>();
		private final Identity identity = mock(Identity.class);

		@Override
		public void refuse(@NotNull Component message) {
			refused.set(message);
		}

		@Override
		public void deny(@NotNull Component message) {
			denied.set(message);
		}

		@Override
		public void requireReconnect(@NotNull Component message) {
			denied.set(message);
		}

		@Override
		public @NotNull Identity identity(
				@Nullable UUID accountUniqueId,
				@NotNull String username,
				ConnectionIdentity.@Nullable Origin origin
		) {
			this.username.set(username);
			return identity;
		}
	}
}
