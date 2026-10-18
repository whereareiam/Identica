package me.whereareiam.identica.platform.bungeecord.listener.connection.server;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.platform.bungeecord.join.BungeeCordJoinReporter;
import me.whereareiam.identica.platform.bungeecord.listener.routing.BungeeCordRoutingIntentListener;
import me.whereareiam.identica.routing.RoutingCoordinator;
import net.md_5.bungee.api.event.ServerConnectedEvent;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ServerConnectedListener implements DynamicListener<ServerConnectedEvent> {
	private final RoutingCoordinator routingCoordinator;
	private final BungeeCordJoinReporter joinReporter;
	private final BungeeCordRoutingIntentListener routingIntentListener;

	@Override
	public void onEvent(ServerConnectedEvent event) {
		String currentServer = event.getServer() != null ? event.getServer().getInfo().getName() : null;
		if (currentServer == null) return;

		joinReporter.joining(event.getPlayer(), currentServer);
		routingIntentListener.entersWorld(event.getPlayer().getUniqueId());
		routingCoordinator.markReached(event.getPlayer().getUniqueId(), currentServer);
	}
}
