package me.whereareiam.identica.platform.velocity.adapter.auth;

import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.prepare.DefaultPrepareStateStore;
import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.platform.common.decision.ConnectionDecisionApplier;
import me.whereareiam.identica.platform.common.decision.ConnectionDecisionDeliveryCoordinator;
import me.whereareiam.identica.platform.common.stage.login.LoginDecisionProcessor;
import me.whereareiam.identica.platform.common.stage.login.PreparedRefusal;
import me.whereareiam.identica.platform.common.stage.profile.ProfileRewriteProcessor;
import me.whereareiam.identica.platform.velocity.adapter.profile.VelocityProfileRewriteAdapter;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs Velocity's profile and login adapters one after the other over the default prepare state store, the way
 * Velocity fires {@code GameProfileRequestEvent} and then {@code LoginEvent} for a connection.
 */
@DisplayName("Velocity Profile Then Login")
class VelocityProfileThenLoginTest {
	private static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 41294);
	private static final InetSocketAddress VIRTUAL_HOST = InetSocketAddress.createUnresolved("play.example.com", 25565);
	private static final String CONNECTION_KEY = "Bob|127.0.0.1|play.example.com|25565";

	private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
	private DefaultPrepareStateStore store;
	private VelocityProfileRewriteAdapter profileAdapter;
	private VelocityLoginDecisionAdapter loginAdapter;

	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(() -> TEXT_SERIALIZER);
	}

	private static final SerializerEngine TEXT_SERIALIZER = new SerializerEngine() {
		@Override
		public @NotNull String serialize(Component component) {
			return component.toString();
		}

		@Override
		public @NotNull Component serialize(SerializerContent content) {
			return Component.text(content.getMessage());
		}

		public @NotNull String renderTemplate(SerializerContent content) {
			return content.getMessage();
		}

		@Override
		public SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
			return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
		}
	};

	@BeforeEach
	void createAdapters() {
		ReplicationAdapter replication = mock(ReplicationAdapter.class);
		when(replication.isAvailable()).thenReturn(false);
		store = new DefaultPrepareStateStore(new DefaultReplicationSystem(replication), VelocityProfileThenLoginTest::engine);
		PreparedRefusal refusal = new PreparedRefusal(store, VelocityProfileThenLoginTest::messages);

		profileAdapter = new VelocityProfileRewriteAdapter(new ProfileRewriteProcessor(connectionCoordinator, store, refusal));
		loginAdapter = new VelocityLoginDecisionAdapter(new LoginDecisionProcessor(
				connectionCoordinator,
				mock(IdentityService.class),
				store,
				refusal,
				new ConnectionDecisionApplier(
						VelocityProfileThenLoginTest::messages,
						mock(ConnectionDecisionDeliveryCoordinator.class),
						mock(PlatformDeliveryAdapter.class)
				)
		));
		when(connectionCoordinator.process(any())).thenReturn(CompletableFuture.completedFuture(ConnectionDecision.allow()));
	}

	@DisplayName("Refuses the login of a connection whose profile was refused, before Identica's flow starts")
	@Test
	void refusedProfileRefusesTheLogin() {
		when(connectionCoordinator.prepare(any())).thenReturn(CompletableFuture.completedFuture(PrepareDecision.deny("restricted")));
		GameProfile profile = GameProfile.forOfflinePlayer("Bob");

		profileAdapter.apply(profileRequest(profile));
		LoginEvent login = login(profile);
		loginAdapter.process(login);

		assertFalse(login.getResult().isAllowed());
		assertEquals(Optional.of(Component.text("restricted")), login.getResult().getReasonComponent());
		verify(connectionCoordinator, never()).process(any());
		assertEquals(Optional.empty(), store.peek(profile.getId()));
	}

	@DisplayName("Refuses a profile refusal without text with the prepare denial message")
	@Test
	void refusedProfileWithoutTextRefusesWithPrepareDenialMessage() {
		when(connectionCoordinator.prepare(any())).thenReturn(CompletableFuture.completedFuture(PrepareDecision.deny(null)));
		GameProfile profile = GameProfile.forOfflinePlayer("Bob");

		profileAdapter.apply(profileRequest(profile));
		LoginEvent login = login(profile);
		loginAdapter.process(login);

		assertEquals(Optional.of(Component.text("refused")), login.getResult().getReasonComponent());
	}

	@DisplayName("Finds the refusal by connection key when the login's unique ID differs from the profile's")
	@Test
	void refusalIsFoundByConnectionKey() {
		when(connectionCoordinator.prepare(any())).thenReturn(CompletableFuture.completedFuture(PrepareDecision.deny("restricted")));
		GameProfile profile = GameProfile.forOfflinePlayer("Bob");

		profileAdapter.apply(profileRequest(profile));
		LoginEvent login = login(profile.withId(UUID.randomUUID()));
		loginAdapter.process(login);

		assertFalse(login.getResult().isAllowed());
		verify(connectionCoordinator, never()).process(any());
	}

	@DisplayName("Lets a player in on the next attempt once the profile is allowed")
	@Test
	void allowedRetryLogsIn() {
		when(connectionCoordinator.prepare(any()))
				.thenReturn(CompletableFuture.completedFuture(PrepareDecision.deny("restricted")))
				.thenReturn(CompletableFuture.completedFuture(PrepareDecision.allow()));
		GameProfile profile = GameProfile.forOfflinePlayer("Bob");

		profileAdapter.apply(profileRequest(profile));
		loginAdapter.process(login(profile));
		profileAdapter.apply(profileRequest(profile));
		LoginEvent retry = login(profile);
		loginAdapter.process(retry);

		assertTrue(retry.getResult().isAllowed());
		verify(connectionCoordinator).process(any());
	}

	@DisplayName("Does not enforce an earlier refusal after a failed profile run")
	@Test
	void failedProfileRunForgetsEarlierRefusal() {
		when(connectionCoordinator.prepare(any()))
				.thenReturn(CompletableFuture.completedFuture(PrepareDecision.deny("restricted")))
				.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("prepare failed")));
		GameProfile profile = GameProfile.forOfflinePlayer("Bob");

		profileAdapter.apply(profileRequest(profile));
		profileAdapter.apply(profileRequest(profile));
		LoginEvent login = login(profile);
		loginAdapter.process(login);

		assertTrue(login.getResult().isAllowed());
		verify(connectionCoordinator, times(1)).process(any());
	}

	@DisplayName("Keeps the handshake decision after a failed profile run, so the login keeps its provider")
	@Test
	void failedProfileRunKeepsHandshakeProvider() {
		ProviderContext provider = ProviderContext.of("credential", "bob", "Bob");
		when(connectionCoordinator.prepare(any()))
				.thenReturn(CompletableFuture.completedFuture(PrepareDecision.deny("restricted")))
				.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("prepare failed")));
		GameProfile profile = GameProfile.forOfflinePlayer("Bob");

		profileAdapter.apply(profileRequest(profile));
		store.put(CONNECTION_KEY, PrepareDecision.builder()
				.status(PrepareDecision.Status.ALLOW)
				.provider(provider)
				.build());
		profileAdapter.apply(profileRequest(profile));
		LoginEvent login = login(profile);
		loginAdapter.process(login);

		assertTrue(login.getResult().isAllowed());
		ArgumentCaptor<ConnectionRequest> request = ArgumentCaptor.forClass(ConnectionRequest.class);
		verify(connectionCoordinator).process(request.capture());
		assertEquals(provider, request.getValue().getProvider());
	}

	private static GameProfileRequestEvent profileRequest(GameProfile profile) {
		InboundConnection connection = mock(InboundConnection.class);
		when(connection.getRemoteAddress()).thenReturn(ADDRESS);
		when(connection.getVirtualHost()).thenReturn(Optional.of(VIRTUAL_HOST));
		return new GameProfileRequestEvent(connection, profile, false);
	}

	private static LoginEvent login(GameProfile profile) {
		Player player = mock(Player.class);
		when(player.getRemoteAddress()).thenReturn(ADDRESS);
		when(player.getUniqueId()).thenReturn(profile.getId());
		when(player.getUsername()).thenReturn(profile.getName());
		when(player.getCurrentServer()).thenReturn(Optional.empty());
		when(player.getVirtualHost()).thenReturn(Optional.of(VIRTUAL_HOST));
		return new LoginEvent(player, null);
	}

	private static Engine engine() {
		Engine engine = new Engine();
		Engine.Behavior behavior = new Engine.Behavior();
		behavior.setBridgeTtl(Duration.ofSeconds(30));
		engine.setBehavior(behavior);
		return engine;
	}

	private static Messages messages() {
		Messages.Engine.Prepare prepare = new Messages.Engine.Prepare();
		prepare.setHandshakeDenied(List.of("refused"));
		Messages.Engine engine = new Messages.Engine();
		engine.setPrepare(prepare);
		Messages messages = new Messages();
		messages.setEngine(engine);
		return messages;
	}
}
