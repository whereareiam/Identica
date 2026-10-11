package me.whereareiam.identica.platform.bungeecord.adapter.auth;

import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.platform.bungeecord.adapter.MiniMessageSerializerEngine;
import me.whereareiam.identica.platform.common.decision.ConnectionDecisionApplier;
import me.whereareiam.identica.platform.common.stage.login.LoginDecisionProcessor;
import me.whereareiam.identica.platform.common.stage.login.PreparedRefusal;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("BungeeCord Login Decision Adapter")
class BungeeCordLoginDecisionAdapterTest {
	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(MiniMessageSerializerEngine::new);
	}

	@Test
	@DisplayName("Disconnects a player whose profile is still refused, with only the disconnect reason")
	void stillRefusedPlayerIsDisconnected() {
		UUID uniqueId = UUID.randomUUID();
		PrepareStateStore store = mock(PrepareStateStore.class);
		when(store.peek(uniqueId)).thenReturn(Optional.of(PrepareDecision.deny("<red>Joining is restricted")));
		ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
		BungeeAudiences audiences = mock(BungeeAudiences.class);
		BungeeCordLoginDecisionAdapter adapter = new BungeeCordLoginDecisionAdapter(new LoginDecisionProcessor(
				connectionCoordinator,
				mock(IdentityService.class),
				store,
				new PreparedRefusal(store, Messages::new),
				mock(ConnectionDecisionApplier.class)
		), audiences);
		ProxiedPlayer player = mock(ProxiedPlayer.class);
		when(player.getUniqueId()).thenReturn(uniqueId);
		when(player.getName()).thenReturn("Bob");
		when(player.getSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 41294));

		adapter.process(new PostLoginEvent(player, null, (result, error) -> {
		}));

		ArgumentCaptor<BaseComponent[]> reason = ArgumentCaptor.forClass(BaseComponent[].class);
		verify(player).disconnect(reason.capture());
		assertEquals("Joining is restricted", BaseComponent.toPlainText(reason.getValue()));
		verify(store).clearIf(eq(uniqueId), eq("Bob|127.0.0.1||"), any());
		verifyNoInteractions(connectionCoordinator, audiences);
	}
}
