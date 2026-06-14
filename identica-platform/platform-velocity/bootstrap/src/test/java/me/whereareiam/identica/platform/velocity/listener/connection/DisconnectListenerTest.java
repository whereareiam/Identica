package me.whereareiam.identica.platform.velocity.listener.connection;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.identity.IdentityAttachment;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

@DisplayName("Velocity Disconnect Listener")
class DisconnectListenerTest {
	@DisplayName("Disconnect emits a disconnect lifecycle event and keeps only physical disconnect teardown")
	@Test
	void disconnectEmitsDisconnectedAndKeepsOnlyPhysicalDisconnectTeardown() {
		ConnectionLifecycleService lifecycleService = mock(ConnectionLifecycleService.class);
		IdentityService identityService = mock(IdentityService.class);
		SessionService sessionService = mock(SessionService.class);
		DisconnectListener listener = new DisconnectListener(
				lifecycleService,
				identityService,
				sessionService
		);
		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		Player player = mock(Player.class);
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

		listener.onEvent(new DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN));

		verify(lifecycleService).disconnected(connectionUniqueId, accountUniqueId, null);
		verify(sessionService).close(accountUniqueId);
		verify(identityService).detach(connectionUniqueId);
	}
}
