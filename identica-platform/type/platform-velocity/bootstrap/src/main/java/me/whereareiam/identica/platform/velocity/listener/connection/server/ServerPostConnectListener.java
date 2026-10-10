package me.whereareiam.identica.platform.velocity.listener.connection.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.platform.common.adapter.PlatformResumeDecisionAdapter;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import org.jetbrains.annotations.NotNull;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ServerPostConnectListener implements DynamicListener<ServerPostConnectEvent> {
	private final PlatformResumeDecisionAdapter<ServerPostConnectEvent> resumeDecisionAdapter;
	private final RoutingCoordinator routingCoordinator;
	private final IdentityService identityService;
	private final PlatformDeliveryAdapter deliveryAdapter;

	@Override
	public void onEvent(ServerPostConnectEvent event) {
		resumeDecisionAdapter.resume(event);
		markInitialReady(event);
		markRoutingReached(event);
	}

	private void markInitialReady(@NotNull ServerPostConnectEvent event) {
		String currentServer = event.getPlayer().getCurrentServer()
				.map(server -> server.getServerInfo().getName())
				.orElse(null);
		if (currentServer == null) return;

		identityService.findByConnectionUniqueId(event.getPlayer().getUniqueId())
				.ifPresent(identity -> deliveryAdapter.armInitialReady(identity, currentServer));
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
