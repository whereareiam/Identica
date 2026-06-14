package me.whereareiam.identica.platform.bungeecord.adapter.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
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
import me.whereareiam.identica.platform.bungeecord.actor.BungeeCordCommandPlayer;
import me.whereareiam.identica.platform.bungeecord.util.BaseComponentMapper;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordLoginDecisionAdapter implements PlatformLoginDecisionAdapter<PostLoginEvent> {
	private final @NotNull ConnectionCoordinator connectionCoordinator;
	private final @NotNull IdentityService identityService;
	private final @NotNull PrepareStateStore prepareStateStore;
	private final @NotNull ConnectionDecisionApplier decisionApplier;
	private final @NotNull BungeeAudiences audiences;

	public void process(@NotNull PostLoginEvent event) {
		ProxiedPlayer player = event.getPlayer();
		String ip = resolveIp(player);
		String intendedServer = event.getTarget() != null ? event.getTarget().getName() : null;

		ConnectionIdentity identity = new ConnectionIdentity(player.getName(), ip);
		identity.setConnectionUniqueId(player.getUniqueId());
		identity.setObservedUniqueId(player.getUniqueId());
		applyOrigin(identity, player);

		PrepareDecision prepared = resolvePrepared(player, identity);
		if (prepared != null && prepared.getAccountUniqueId() != null && !prepared.getAccountUniqueId().equals(identity.getAccountUniqueId()))
			identity.setAccountUniqueId(prepared.getAccountUniqueId());

		ProviderContext provider = prepared != null ? prepared.getProvider() : null;
		if (provider != null && !provider.getProviderUsername().isBlank())
			identity.setUsername(provider.getProviderUsername());

		Logger.debug(
				"Bungee login request player=%s username=%s ip=%s key=%s preparedUniqueId=%s preparedProvider=%s preparedSubject=%s preparedEffective=%s",
				player.getUniqueId(),
				player.getName(),
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
				"Bungee login decision player=%s status=%s provider=%s subject=%s",
				player.getUniqueId(),
				decision != null ? decision.getStatus() : null,
				provider != null ? provider.getProviderId() : null,
				provider != null ? provider.getProviderSubject() : null
		);

		UUID accountUniqueId = identity.getAccountUniqueId();
		BungeeCordCommandPlayer liveIdentity = new BungeeCordCommandPlayer(
				player.getUniqueId(),
				accountUniqueId,
				player,
				audiences.player(player),
				identity.getUsername(),
				identity.getOrigin()
		);
		decisionApplier.apply(decision, liveIdentity, loginTarget(player));
		ConnectionDecision.Status status = decision != null ? decision.getStatus() : null;
		if (status == ConnectionDecision.Status.ALLOW || status == ConnectionDecision.Status.WAIT)
			identityService.attach(player.getUniqueId(), accountUniqueId, liveIdentity);
	}

	private PrepareDecision resolvePrepared(@NotNull ProxiedPlayer player, @NotNull ConnectionIdentity identity) {
		PrepareDecision prepared = prepareStateStore.peek(player.getUniqueId()).orElse(null);
		if (prepared != null) return prepared;

		String connectionKey = identity.connectionKey();
		if (connectionKey == null || connectionKey.isBlank()) return null;

		return prepareStateStore.peek(connectionKey).orElse(null);
	}

	private String resolveIp(@NotNull ProxiedPlayer player) {
		SocketAddress address = player.getSocketAddress();
		if (!(address instanceof InetSocketAddress inetSocketAddress))
			return null;

		if (inetSocketAddress.getAddress() != null)
			return inetSocketAddress.getAddress().getHostAddress();

		return inetSocketAddress.getHostString();
	}

	private @NotNull ConnectionDecisionApplier.Target loginTarget(@NotNull ProxiedPlayer player) {
		return new ConnectionDecisionApplier.Target() {
			@Override
			public void deny(@NotNull Component message) {
				audiences.player(player).sendMessage(message);
				player.disconnect(BaseComponentMapper.map(message));
			}

			@Override
			public void requireReconnect(@NotNull Component message) {
				audiences.player(player).sendMessage(message);
				player.disconnect(BaseComponentMapper.map(message));
			}
		};
	}

	private void applyOrigin(
			@NotNull ConnectionIdentity identity,
			@NotNull ProxiedPlayer player
	) {
		if (player.getPendingConnection() == null || player.getPendingConnection().getVirtualHost() == null)
			return;

		identity.setOrigin(new ConnectionIdentity.Origin(
				player.getPendingConnection().getVirtualHost().getHostString(),
				player.getPendingConnection().getVirtualHost().getPort()
		));
	}
}
