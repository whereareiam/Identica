package me.whereareiam.identica.platform.velocity.listener.connection.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.connection.ServerJoinedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.platform.common.adapter.PlatformResumeDecisionAdapter;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.util.EventUtil;
import org.jetbrains.annotations.NotNull;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ServerPostConnectListener implements DynamicListener<ServerPostConnectEvent> {
	private final PlatformResumeDecisionAdapter<ServerPostConnectEvent> resumeDecisionAdapter;
	private final RoutingCoordinator routingCoordinator;
	private final IdentityService identityService;

	@Override
	public void onEvent(ServerPostConnectEvent event) {
		resumeDecisionAdapter.resume(event);
		joined(event);
		markRoutingReached(event);
	}

	/**
	 * Reports that the player's client is in the server's world: Velocity fires this event once the client got the
	 * server's join.
	 */
	private void joined(@NotNull ServerPostConnectEvent event) {
		String currentServer = event.getPlayer().getCurrentServer()
				.map(server -> server.getServerInfo().getName())
				.orElse(null);
		if (currentServer == null) return;

		identityService.findByConnectionUniqueId(event.getPlayer().getUniqueId())
				.ifPresent(identity -> EventUtil.callEvent(new ServerJoinedEvent(identity, currentServer)));
	}

	private void markRoutingReached(@NotNull ServerPostConnectEvent event) {
		String currentServer = event.getPlayer().getCurrentServer()
				.map(server -> server.getServerInfo().getName())
				.orElse(null);
		if (currentServer == null) {
			Logger.debug("Velocity post-connect routing reached skipped player=%s reason=no-current-server",
					event.getPlayer().getUniqueId());
			return;
		}

		Logger.debug("Velocity post-connect routing reached check player=%s current=%s previous=%s",
				event.getPlayer().getUniqueId(),
				currentServer,
				event.getPreviousServer() != null ? event.getPreviousServer().getServerInfo().getName() : null);
		routingCoordinator.markReached(event.getPlayer().getUniqueId(), currentServer);
	}
}
