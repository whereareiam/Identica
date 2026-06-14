package me.whereareiam.identica.platform.bungeecord.listener.connection;

import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.identity.IdentityAttachment;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

@DisplayName("Bungee Player Disconnect Listener")
class PlayerDisconnectListenerTest {
	@DisplayName("Disconnect emits a disconnect lifecycle event and keeps only physical disconnect teardown")
	@Test
	void disconnectEmitsDisconnectedAndKeepsOnlyPhysicalDisconnectTeardown() {
		ConnectionLifecycleService lifecycleService = mock(ConnectionLifecycleService.class);
		IdentityService identityService = mock(IdentityService.class);
		SessionService sessionService = mock(SessionService.class);
		PlatformDeliveryAdapter deliveryAdapter = mock(PlatformDeliveryAdapter.class);
		PlayerDisconnectListener listener = new PlayerDisconnectListener(
				lifecycleService,
				identityService,
				sessionService,
				deliveryAdapter
		);
		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		ProxiedPlayer player = mock(ProxiedPlayer.class);
		when(player.getUniqueId()).thenReturn(connectionUniqueId);
		when(identityService.findAttachmentByConnectionUniqueId(connectionUniqueId)).thenReturn(Optional.of(
				IdentityAttachment.builder()
						.connectionUniqueId(connectionUniqueId)
						.accountUniqueId(accountUniqueId)
						.username("whereareiam")
						.identity(mock(me.whereareiam.identica.identity.actor.Identity.class))
						.build()
		));
		when(sessionService.close(accountUniqueId)).thenReturn(CompletableFuture.completedFuture(null));

		listener.onEvent(new PlayerDisconnectEvent(player));

		verify(lifecycleService).disconnected(connectionUniqueId, accountUniqueId, null);
		verify(sessionService).close(accountUniqueId);
		verify(identityService).detach(connectionUniqueId);
		verify(deliveryAdapter).clear(connectionUniqueId);
	}
}
