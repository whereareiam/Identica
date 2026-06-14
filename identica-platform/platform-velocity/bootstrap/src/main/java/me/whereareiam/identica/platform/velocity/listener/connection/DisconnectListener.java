package me.whereareiam.identica.platform.velocity.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.identity.IdentityAttachment;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.listener.DynamicListener;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DisconnectListener implements DynamicListener<DisconnectEvent> {
	private final ConnectionLifecycleService connectionLifecycleService;
	private final IdentityService identityService;
	private final SessionService sessionService;

	@Override
	public void onEvent(DisconnectEvent event) {
		Player player = event.getPlayer();
		if (player == null) return;

		IdentityAttachment attachment = identityService.findAttachmentByConnectionUniqueId(player.getUniqueId()).orElse(null);
		connectionLifecycleService.disconnected(
				player.getUniqueId(),
				attachment != null ? attachment.getAccountUniqueId() : null,
				null
		);
		if (attachment != null && attachment.getAccountUniqueId() != null)
			sessionService.close(attachment.getAccountUniqueId()).join();

		identityService.detach(player.getUniqueId());
	}
}
