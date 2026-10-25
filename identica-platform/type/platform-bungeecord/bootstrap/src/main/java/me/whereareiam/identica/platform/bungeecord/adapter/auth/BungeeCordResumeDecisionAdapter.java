package me.whereareiam.identica.platform.bungeecord.adapter.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.platform.bungeecord.actor.BungeeCordCommandPlayer;
import me.whereareiam.identica.platform.bungeecord.util.BaseComponentMapper;
import me.whereareiam.identica.platform.common.adapter.PlatformResumeDecisionAdapter;
import me.whereareiam.identica.platform.common.decision.ConnectionDecisionApplier;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordResumeDecisionAdapter implements PlatformResumeDecisionAdapter<ServerSwitchEvent> {
	private final @NotNull ConnectionCoordinator connectionCoordinator;
	private final @NotNull IdentityService identityService;
	private final @NotNull PrepareStateStore prepareStateStore;
	private final @NotNull ConnectionDecisionApplier decisionApplier;
	private final @NotNull BungeeAudiences audiences;
	private final Map<UUID, ConnectionDecision> resumedOnConnect = new ConcurrentHashMap<>();

	/**
	 * Resumes the journey of a player whom BungeeCord connected to their first server. BungeeCord does so before the
	 * client leaves its login, so the journey resumes while the client is still on the loading screen, also for a
	 * player a checkpoint then holds in that server's configuration.
	 */
	public void resume(@NotNull ServerSwitchEvent event) {
		ProxiedPlayer player = event.getPlayer();
		if (event.getFrom() != null) return;

		ConnectionDecision decision = resume(player, player.getServer() != null ? player.getServer().getInfo().getName() : null);
		if (decision != null) resumedOnConnect.put(player.getUniqueId(), decision);
	}

	/**
	 * Returns the journey's decision when BungeeCord connected the player to their first server, which a hold on the
	 * loading screen continues from.
	 *
	 * @param player the held player
	 * @return the journey's decision, or {@code null} when the journey had none
	 */
	public @Nullable ConnectionDecision resumedOnConnect(@NotNull ProxiedPlayer player) {
		return resumedOnConnect.get(player.getUniqueId());
	}

	/**
	 * Forgets a player who left.
	 *
	 * @param connectionUniqueId the player's connection
	 */
	public void forget(@NotNull UUID connectionUniqueId) {
		resumedOnConnect.remove(connectionUniqueId);
	}

	private @Nullable ConnectionDecision resume(@NotNull ProxiedPlayer player, @Nullable String intendedServer) {
		String ip = resolveIp(player);
		ConnectionIdentity identity = new ConnectionIdentity(player.getName(), ip);
		identity.setConnectionUniqueId(player.getUniqueId());
		identity.setObservedUniqueId(player.getUniqueId());
		applyOrigin(identity, player);
		PrepareDecision prepared = resolvePrepared(player, identity);
		if (prepared != null && prepared.getAccountUniqueId() != null && !prepared.getAccountUniqueId().equals(identity.getAccountUniqueId()))
			identity.setAccountUniqueId(prepared.getAccountUniqueId());
		if (prepared != null) identity.setClaimedUniqueId(prepared.getClaimedUniqueId());

		ProviderContext provider = prepared != null ? prepared.getProvider() : null;
		if (provider != null && !provider.getProviderUsername().isBlank())
			identity.setUsername(provider.getProviderUsername());
		Logger.debug(
				"Bungee resume request player=%s username=%s ip=%s key=%s preparedUniqueId=%s preparedProvider=%s preparedSubject=%s preparedEffective=%s",
				player.getUniqueId(),
				player.getName(),
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
				.selection(prepared != null ? prepared.getSelection() : null)
				.build();
		ConnectionDecision decision = connectionCoordinator.resume(request)
				.toCompletableFuture()
				.join();
		Logger.debug(
				"Bungee resume decision player=%s status=%s provider=%s subject=%s",
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
				identity.getOrigin());
		liveIdentity.setConnectionUsername(identity.getConnectionUsername());
		if (decision == null || decision.getStatus() == ConnectionDecision.Status.NO_PENDING)
			return decision;

		decisionApplier.applyOrQueueWait(
				decision,
				liveIdentity,
				resumeTarget(player),
				identity
		);

		ConnectionDecision.Status status = decision.getStatus();
		if (status == ConnectionDecision.Status.DENY || status == ConnectionDecision.Status.REQUIRE_RECONNECT)
			return decision;

		if (status == ConnectionDecision.Status.ALLOW || status == ConnectionDecision.Status.WAIT)
			identityService.attach(player.getUniqueId(), accountUniqueId, liveIdentity);
		return decision;
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

	private @NotNull ConnectionDecisionApplier.Target resumeTarget(@NotNull ProxiedPlayer player) {
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
