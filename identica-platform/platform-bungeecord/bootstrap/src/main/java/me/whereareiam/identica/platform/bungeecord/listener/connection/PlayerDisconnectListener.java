package me.whereareiam.identica.platform.bungeecord.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.type.routing.reason.RoutingClearReason;
import me.whereareiam.identica.verification.VerificationService;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;

@Singleton
public class PlayerDisconnectListener implements DynamicListener<PlayerDisconnectEvent> {
	private final RoutingCoordinator routingCoordinator;
	private final IdentityService identityService;
	private final PrepareStateStore prepareStateStore;
	private final VerificationService verificationService;

	@Inject
	public PlayerDisconnectListener(
			RoutingCoordinator routingCoordinator,
			IdentityService identityService,
			PrepareStateStore prepareStateStore,
			VerificationService verificationService
	) {
		this.routingCoordinator = routingCoordinator;
		this.identityService = identityService;
		this.prepareStateStore = prepareStateStore;
		this.verificationService = verificationService;
	}

	@Override
	public void onEvent(PlayerDisconnectEvent event) {
		ProxiedPlayer player = event.getPlayer();
		if (player == null) return;

		routingCoordinator.clear(player.getUniqueId(), RoutingClearReason.DISCONNECT);
		identityService.detach(player.getUniqueId());
		prepareStateStore.clear(player.getUniqueId());
		verificationService.cancelPendingEnrollment(player.getUniqueId());
	}
}
