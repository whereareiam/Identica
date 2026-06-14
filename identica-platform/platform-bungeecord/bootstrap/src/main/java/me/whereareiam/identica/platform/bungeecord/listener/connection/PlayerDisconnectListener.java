package me.whereareiam.identica.platform.bungeecord.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.identity.IdentityAttachment;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PlayerDisconnectListener implements DynamicListener<PlayerDisconnectEvent> {
	private final ConnectionLifecycleService connectionLifecycleService;
	private final IdentityService identityService;
	private final SessionService sessionService;
	private final PlatformDeliveryAdapter deliveryAdapter;

	@Override
	public void onEvent(PlayerDisconnectEvent event) {
		ProxiedPlayer player = event.getPlayer();
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
		deliveryAdapter.clear(player.getUniqueId());
	}
}
