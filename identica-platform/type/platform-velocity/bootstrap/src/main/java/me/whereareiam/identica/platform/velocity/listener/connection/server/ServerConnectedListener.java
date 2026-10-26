package me.whereareiam.identica.platform.velocity.listener.connection.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.connection.ServerSwitchingEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.util.EventUtil;

/**
 * Reports that a player's client leaves its world: Velocity fires this event once the next server accepted the player,
 * and the client then configures for that server until {@code ServerPostConnectEvent}.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ServerConnectedListener implements DynamicListener<ServerConnectedEvent> {
	private final IdentityService identityService;

	@Override
	public void onEvent(ServerConnectedEvent event) {
		identityService.findByConnectionUniqueId(event.getPlayer().getUniqueId())
				.ifPresent(identity -> EventUtil.callEvent(new ServerSwitchingEvent(identity)));
	}
}
