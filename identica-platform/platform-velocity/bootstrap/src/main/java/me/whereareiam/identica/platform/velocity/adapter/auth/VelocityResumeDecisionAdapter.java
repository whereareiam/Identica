package me.whereareiam.identica.platform.velocity.adapter.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.common.adapter.ConnectionDecisionApplier;
import me.whereareiam.identica.connection.ConnectionCoordinator;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.platform.adapter.PlatformResumeDecisionAdapter;
import me.whereareiam.identica.platform.velocity.actor.VelocityCommandPlayer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VelocityResumeDecisionAdapter implements PlatformResumeDecisionAdapter<ServerPostConnectEvent> {
	private final @NotNull ConnectionCoordinator connectionCoordinator;
	private final @NotNull IdentityService identityService;
	private final @NotNull PrepareStateStore prepareStateStore;
	private final @NotNull ConnectionDecisionApplier decisionApplier;

	public void resume(@NotNull ServerPostConnectEvent event) {
		if (event.getPreviousServer() != null) return;

		Player player = event.getPlayer();
		String ip = resolveIp(player);
		String intendedServer = player.getCurrentServer()
				.map(server -> server.getServerInfo().getName())
				.orElse(null);
		PrepareDecision prepared = prepareStateStore.peek(player.getUniqueId()).orElse(null);

		ProviderContext provider = prepared != null ? prepared.getProvider() : null;
		String providerUsername = provider != null && !provider.getProviderUsername().isBlank()
				? provider.getProviderUsername()
				: player.getUsername();
		ConnectionIdentity identity = new ConnectionIdentity(providerUsername, ip);
		identity.setConnectionUniqueId(player.getUniqueId());
		identity.setObservedUniqueId(player.getUniqueId());
		if (prepared != null && prepared.getAccountUniqueId() != null && !prepared.getAccountUniqueId().equals(identity.getAccountUniqueId()))
			identity.setAccountUniqueId(prepared.getAccountUniqueId());
		applyOrigin(identity, player);
		Logger.debug(
				"Velocity resume request player=%s username=%s ip=%s key=%s preparedUniqueId=%s preparedProvider=%s preparedSubject=%s preparedEffective=%s",
				player.getUniqueId(),
				player.getUsername(),
				ip,
				identity.connectionKey(),
				prepared != null ? prepared.getAccountUniqueId() : null,
				provider != null ? provider.getProviderId() : null,
				provider != null ? provider.getProviderSubject() : null,
				prepared != null ? prepared.getEffectiveUsername() : null
		);

		ResumeRequest request = ResumeRequest.builder()
				.connectionUniqueId(player.getUniqueId())
				.identity(identity)
				.intendedServer(intendedServer)
				.provider(provider)
				.build();
		ConnectionDecision decision = connectionCoordinator.resume(request)
				.toCompletableFuture()
				.join();
		Logger.debug(
				"Velocity resume decision player=%s status=%s provider=%s subject=%s",
				player.getUniqueId(),
				decision != null ? decision.getStatus() : null,
				provider != null ? provider.getProviderId() : null,
				provider != null ? provider.getProviderSubject() : null
		);

		UUID accountUniqueId = identity.getAccountUniqueId();
		VelocityCommandPlayer liveIdentity = new VelocityCommandPlayer(
				player.getUniqueId(),
				accountUniqueId,
				player,
				identity.getUsername(),
				identity.getOrigin()
		);
		if (decision == null || decision.getStatus() == ConnectionDecision.Status.NO_PENDING)
			return;

		decisionApplier.applyOrQueueWait(
				decision,
				liveIdentity,
				resumeTarget(player),
				player.getUniqueId(),
				accountUniqueId
		);

		ConnectionDecision.Status status = decision.getStatus();
		if (status == ConnectionDecision.Status.DENY || status == ConnectionDecision.Status.REQUIRE_RECONNECT)
			return;

		if (status == ConnectionDecision.Status.ALLOW || status == ConnectionDecision.Status.WAIT)
			identityService.attach(player.getUniqueId(), accountUniqueId, liveIdentity);
	}

	private String resolveIp(@NotNull Player player) {
		if (player.getRemoteAddress() == null)
			return null;

		if (player.getRemoteAddress().getAddress() != null)
			return player.getRemoteAddress().getAddress().getHostAddress();

		return player.getRemoteAddress().getHostString();
	}

	private @NotNull ConnectionDecisionApplier.Target resumeTarget(@NotNull Player player) {
		return new ConnectionDecisionApplier.Target() {
			@Override
			public void deny(@NotNull Component message) {
				player.disconnect(message);
			}

			@Override
			public void requireReconnect(@NotNull Component message) {
				player.disconnect(message);
			}
		};
	}

	private void applyOrigin(@NotNull ConnectionIdentity identity, @NotNull Player player) {
		InetSocketAddress virtualHost = player.getVirtualHost().orElse(null);
		if (virtualHost == null) return;

		identity.setOrigin(new ConnectionIdentity.Origin(
				virtualHost.getHostString(),
				virtualHost.getPort()
		));
	}
}
