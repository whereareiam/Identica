package me.whereareiam.identica.platform.bungeecord.adapter.profile;

import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.platform.bungeecord.adapter.MiniMessageSerializerEngine;
import me.whereareiam.identica.platform.common.stage.login.PreparedRefusal;
import me.whereareiam.identica.platform.common.stage.profile.ProfileRewriteProcessor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BungeeCord Profile Prepare Adapter")
class BungeeCordProfilePrepareAdapterTest {
	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(MiniMessageSerializerEngine::new);
	}

	@Test
	@DisplayName("Updates both Bungee pending connection UUID fields")
	void applyUniqueIdRewriteUpdatesUniqueIdAndRewriteId() {
		UUID originalUniqueId = UUID.randomUUID();
		UUID originalRewriteId = UUID.randomUUID();
		UUID rewrittenUniqueId = UUID.randomUUID();
		TestPendingConnectionState state = new TestPendingConnectionState(originalUniqueId, originalRewriteId);

		boolean updated = BungeeCordProfilePrepareAdapter.applyUniqueIdRewrite(state, rewrittenUniqueId);

		assertTrue(updated);
		assertEquals(rewrittenUniqueId, state.uniqueId);
		assertEquals(rewrittenUniqueId, state.rewriteId);
	}

	@Test
	@DisplayName("Cancels the login of a refused profile with the rendered message")
	void refusedProfileCancelsTheLogin() {
		LoginEvent event = loginEvent();

		adapter(PrepareDecision.deny("<red>Joining is restricted")).apply(event);

		assertTrue(event.isCancelled());
		assertEquals("Joining is restricted", event.getReason().toPlainText());
		assertTrue(event.getReason().toLegacyText().contains(ChatColor.RED + "Joining is restricted"));
	}

	@Test
	@DisplayName("Lets an allowed profile log in")
	void allowedProfileIsNotCancelled() {
		LoginEvent event = loginEvent();

		adapter(PrepareDecision.allow()).apply(event);

		assertFalse(event.isCancelled());
	}

	private static BungeeCordProfilePrepareAdapter adapter(PrepareDecision decision) {
		ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
		when(connectionCoordinator.prepare(any())).thenReturn(CompletableFuture.completedFuture(decision));
		PrepareStateStore store = mock(PrepareStateStore.class);
		return new BungeeCordProfilePrepareAdapter(new ProfileRewriteProcessor(
				connectionCoordinator,
				store,
				new PreparedRefusal(store, Messages::new)
		));
	}

	private static LoginEvent loginEvent() {
		PendingConnection connection = mock(PendingConnection.class);
		when(connection.getName()).thenReturn("Bob");
		when(connection.getUniqueId()).thenReturn(UUID.randomUUID());
		when(connection.getSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 41294));
		return new LoginEvent(connection, (result, error) -> {
		});
	}

	@RequiredArgsConstructor
	private static final class TestPendingConnectionState {
		private final UUID uniqueId;
		private final UUID rewriteId;
	}
}
