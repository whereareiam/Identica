package me.whereareiam.identica.addon.dialogs.platform.velocity.adapter;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.platform.velocity.listener.VelocityConnectionListener;
import me.whereareiam.identica.checkpoint.Checkpoints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VelocityDialogPlatformTest {
	private static final UUID ALICE = UUID.randomUUID();

	private final ProxyServer proxy = mock(ProxyServer.class);
	private final EventManager events = mock(EventManager.class);
	private final Object plugin = new Object();
	private final VelocityDialogPlatform platform = new VelocityDialogPlatform(proxy, plugin, mock(Checkpoints.class));
	private final Player alice = mock(Player.class);
	private VelocityConnectionListener connections;

	@BeforeEach
	void start() {
		when(proxy.getEventManager()).thenReturn(events);
		when(alice.getUniqueId()).thenReturn(ALICE);
		when(alice.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_6);

		try (MockedStatic<PacketEvents> packetEvents = mockStatic(PacketEvents.class)) {
			packetEvents.when(PacketEvents::getAPI).thenReturn(mock(PacketEventsAPI.class, RETURNS_DEEP_STUBS));
			platform.start(mock(DialogPlatform.Listener.class));
		}

		ArgumentCaptor<Object> listeners = ArgumentCaptor.forClass(Object.class);
		verify(events, times(2)).register(same(plugin), listeners.capture());
		connections = listeners.getAllValues().stream()
				.filter(VelocityConnectionListener.class::isInstance)
				.map(VelocityConnectionListener.class::cast)
				.findFirst()
				.orElseThrow();
	}

	@Test
	void reachesOnlyAClientWithDialogsThatFinishedConnectingToAServer() {
		when(proxy.getPlayer(ALICE)).thenReturn(Optional.empty());
		assertFalse(platform.supports(ALICE));

		when(proxy.getPlayer(ALICE)).thenReturn(Optional.of(alice));
		assertFalse(platform.supports(ALICE));

		connections.onConnected(postConnect());
		assertTrue(platform.supports(ALICE));

		when(alice.getProtocolVersion()).thenReturn(ProtocolVersion.MINECRAFT_1_21_5);
		assertFalse(platform.supports(ALICE));
	}

	@Test
	void waitsForThePostConnectEventWhileSwitchingServers() {
		when(proxy.getPlayer(ALICE)).thenReturn(Optional.of(alice));
		connections.onConnected(postConnect());

		ServerConnectedEvent connecting = mock(ServerConnectedEvent.class);
		when(connecting.getPlayer()).thenReturn(alice);
		connections.onConnecting(connecting);
		assertFalse(platform.supports(ALICE));

		connections.onConnected(postConnect());
		assertTrue(platform.supports(ALICE));
	}

	private ServerPostConnectEvent postConnect() {
		ServerPostConnectEvent event = mock(ServerPostConnectEvent.class);
		when(event.getPlayer()).thenReturn(alice);

		return event;
	}
}
