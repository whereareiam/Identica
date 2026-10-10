package me.whereareiam.identica.platform.velocity.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.identity.IdentityAttachment;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.type.routing.reason.RoutingClearReason;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DisconnectListener implements DynamicListener<DisconnectEvent> {
	private final RoutingCoordinator routingCoordinator;
	private final IdentityService identityService;
	private final SessionService sessionService;
	private final PrepareStateStore prepareStateStore;
	private final VerificationService verificationService;

	@Override
	public void onEvent(DisconnectEvent event) {
		Player player = event.getPlayer();
		if (player == null) return;

		identityService.findAttachmentByConnectionUniqueId(player.getUniqueId())
				.map(IdentityAttachment::getAccountUniqueId)
				.ifPresent(uniqueId -> sessionService.close(SessionCloseRequest.builder()
						.uniqueId(uniqueId)
						.connection(SessionConnection.of(player.getUniqueId()))
						.build()).join());
		routingCoordinator.clear(player.getUniqueId(), RoutingClearReason.DISCONNECT);
		identityService.detach(player.getUniqueId());
		prepareStateStore.clear(player.getUniqueId());
		verificationService.cancelPendingEnrollment(player.getUniqueId());
	}
}
