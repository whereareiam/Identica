package me.whereareiam.identica.addon.dialogs.platform.velocity;

import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.identica.addon.dialogs.display.packetevents.PacketEventsDialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocityDialogPlatformTest {
	private static final UUID ALICE = UUID.randomUUID();

	private final ProxyServer proxy = mock(ProxyServer.class);
	private final EventManager events = mock(EventManager.class);
	private final Object plugin = new Object();
	private final DialogPlatform.Listener listener = mock(DialogPlatform.Listener.class);
	private final VelocityDialogPlatform platform = new VelocityDialogPlatform(proxy, plugin);
	private final Player alice = mock(Player.class);
	private VelocityConnectionListener connections;

	@BeforeEach
	void start() {
		when(proxy.getEventManager()).thenReturn(events);
		when(alice.getUniqueId()).thenReturn(ALICE);

		platform.start(listener);

		ArgumentCaptor<Object> registered = ArgumentCaptor.forClass(Object.class);
		verify(events).register(same(plugin), registered.capture());
		connections = (VelocityConnectionListener) registered.getValue();
	}

	@Test
	void reachesOnlyAnOnlineClientWithDialogs() {
		when(proxy.getPlayer(ALICE)).thenReturn(Optional.empty());
		assertFalse(platform.supports(ALICE));

		when(proxy.getPlayer(ALICE)).thenReturn(Optional.of(alice));
		when(alice.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_6);
		assertTrue(platform.supports(ALICE));

		when(alice.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_5);
		assertFalse(platform.supports(ALICE));
	}

	@Test
	void reportsAServerSwitchAndLeaving() {
		ServerConnectedEvent connecting = mock(ServerConnectedEvent.class);
		when(connecting.getPlayer()).thenReturn(alice);
		connections.onConnecting(connecting);
		verify(listener).switchingServer(ALICE);

		DisconnectEvent leaving = mock(DisconnectEvent.class);
		when(leaving.getPlayer()).thenReturn(alice);
		connections.onDisconnect(leaving);
		verify(listener).left(ALICE);
	}

	@Test
	void showsDialogsThroughPacketEvents() {
		assertInstanceOf(PacketEventsDialogDisplay.class, platform.display());
	}
}
