package me.whereareiam.identica.platform.velocity.adapter.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import me.whereareiam.identica.common.adapter.ConnectionDecisionApplier;
import me.whereareiam.identica.connection.ConnectionCoordinator;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.platform.adapter.PlatformLoginDecisionAdapter;
import me.whereareiam.identica.platform.velocity.actor.VelocityCommandPlayer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.UUID;

@Singleton
public class VelocityLoginDecisionAdapter implements PlatformLoginDecisionAdapter<LoginEvent> {
	private final @NotNull ConnectionCoordinator connectionCoordinator;
	private final @NotNull IdentityService identityService;
	private final @NotNull PrepareStateStore prepareStateStore;
	private final @NotNull ConnectionDecisionApplier decisionApplier;

	@Inject
	public VelocityLoginDecisionAdapter(
			@NotNull ConnectionCoordinator connectionCoordinator,
			@NotNull IdentityService identityService,
			@NotNull PrepareStateStore prepareStateStore,
			@NotNull ConnectionDecisionApplier decisionApplier
	) {
		this.connectionCoordinator = connectionCoordinator;
		this.identityService = identityService;
		this.prepareStateStore = prepareStateStore;
		this.decisionApplier = decisionApplier;
	}

	public void process(@NotNull LoginEvent event) {
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
				"Velocity login request player=%s username=%s ip=%s key=%s preparedUniqueId=%s preparedProvider=%s preparedSubject=%s preparedEffective=%s",
				player.getUniqueId(),
				player.getUsername(),
				ip,
				identity.connectionKey(),
				prepared != null ? prepared.getAccountUniqueId() : null,
				provider != null ? provider.getProviderId() : null,
				provider != null ? provider.getProviderSubject() : null,
				prepared != null ? prepared.getEffectiveUsername() : null
		);

		ConnectionRequest request = ConnectionRequest.builder()
				.identity(identity)
				.connectionUniqueId(player.getUniqueId())
				.provider(provider)
				.intendedServer(intendedServer)
				.build();
		ConnectionDecision decision = connectionCoordinator.process(request)
				.toCompletableFuture()
				.join();
		Logger.debug(
				"Velocity login decision player=%s status=%s provider=%s subject=%s",
				player.getUniqueId(),
				decision != null ? decision.getStatus() : null,
				provider != null ? provider.getProviderId() : null,
				provider != null ? provider.getProviderSubject() : null
		);

		decisionApplier.apply(decision, new VelocityCommandPlayer(
				player.getUniqueId(),
				identity.getAccountUniqueId(),
				player,
				identity.getUsername(),
				identity.getOrigin()
		), loginTarget(event));
		ConnectionDecision.Status status = decision != null ? decision.getStatus() : null;
		if (status == ConnectionDecision.Status.ALLOW || status == ConnectionDecision.Status.WAIT) {
			UUID accountUniqueId = identity.getAccountUniqueId();
			identityService.attach(
					player.getUniqueId(),
					accountUniqueId,
					new VelocityCommandPlayer(
							player.getUniqueId(),
							accountUniqueId,
							player,
							identity.getUsername(),
							identity.getOrigin()
					)
			);
		}
	}

	private String resolveIp(@NotNull Player player) {
		if (player.getRemoteAddress() == null)
			return null;

		if (player.getRemoteAddress().getAddress() != null)
			return player.getRemoteAddress().getAddress().getHostAddress();

		return player.getRemoteAddress().getHostString();
	}

	private @NotNull ConnectionDecisionApplier.Target loginTarget(@NotNull LoginEvent event) {
		return new ConnectionDecisionApplier.Target() {
			@Override
			public void deny(@NotNull Component message) {
				event.setResult(ResultedEvent.ComponentResult.denied(message));
			}

			@Override
			public void requireReconnect(@NotNull Component message) {
				event.setResult(ResultedEvent.ComponentResult.denied(message));
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
