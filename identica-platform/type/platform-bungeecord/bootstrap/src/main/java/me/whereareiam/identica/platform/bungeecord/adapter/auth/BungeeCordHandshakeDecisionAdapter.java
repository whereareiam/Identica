package me.whereareiam.identica.platform.bungeecord.adapter.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.platform.common.decision.HandshakeDecisionProcessor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeApplierRegistry;
import me.whereareiam.identica.platform.common.adapter.PlatformHandshakeDecisionAdapter;
import me.whereareiam.identica.platform.bungeecord.api.handshake.BungeeCordHandshakeContext;
import me.whereareiam.identica.platform.bungeecord.util.BaseComponentMapper;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.PreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordHandshakeDecisionAdapter implements PlatformHandshakeDecisionAdapter<PreLoginEvent> {
	private final @NotNull HandshakeDecisionProcessor processor;
	private final @NotNull PlatformHandshakeApplierRegistry<BungeeCordHandshakeContext> applierRegistry;

	private volatile boolean clientProfileUnavailable;

	public @NotNull CompletionStage<Void> process(@NotNull PreLoginEvent event) {
		Request request = request(event);
		if (request == null) return CompletableFuture.completedFuture(null);

		return processor.process(toProcessorRequest(request), message -> {
			event.setCancelled(true);
			event.setReason(TextComponent.fromArray(BaseComponentMapper.map(message)));
		}).toCompletableFuture();
	}

	private @Nullable Request request(@NotNull PreLoginEvent event) {
		String username = event.getConnection().getName();
		String resolvedIp = resolveIp(event);
		if (username == null || username.isBlank() || resolvedIp == null) {
			Logger.debug("Bungee handshake missing connection data username=%s ip=%s, skipping handshake processing", username, resolvedIp);
			return null;
		}

		ConnectionIdentity identity = new ConnectionIdentity(username, resolvedIp);
		identity.setClaimedUniqueId(resolveClaimedUniqueId(event.getConnection()));
		applyOrigin(identity, event);
		BungeeCordHandshakeContext context = new BungeeCordHandshakeContext(event.getConnection());
		return new Request(
				identity,
				instruction -> applierRegistry.applyAll(context, instruction)
		);
	}

	private @NotNull HandshakeDecisionProcessor.Request toProcessorRequest(@NotNull Request request) {
		return new HandshakeDecisionProcessor.Request(request.getIdentity(), request.getInstructionTarget());
	}

	private void applyOrigin(@NotNull ConnectionIdentity identity, @NotNull PreLoginEvent event) {
		if (event.getConnection().getVirtualHost() == null) return;
		identity.setOrigin(new ConnectionIdentity.Origin(
				event.getConnection().getVirtualHost().getHostString(),
				event.getConnection().getVirtualHost().getPort()
		));
	}

	/**
	 * Reads the UUID the client claimed when it started logging in. BungeeCord keeps it in its login request,
	 * which its API does not expose, so it is read from the proxy's own connection class.
	 */
	private @Nullable UUID resolveClaimedUniqueId(@NotNull PendingConnection connection) {
		if (clientProfileUnavailable) return null;

		try {
			Object loginRequest = connection.getClass().getMethod("getLoginRequest").invoke(connection);
			if (loginRequest == null) return null;

			Object profileId = loginRequest.getClass().getMethod("getUuid").invoke(loginRequest);
			return profileId instanceof UUID uniqueId ? uniqueId : null;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			clientProfileUnavailable = true;
			Logger.debug("BungeeCord does not expose the UUID clients claim, so it is not used: %s", exception);
			return null;
		}
	}

	private @Nullable String resolveIp(@NotNull PreLoginEvent event) {
		SocketAddress address = event.getConnection().getSocketAddress();
		if (!(address instanceof InetSocketAddress inetSocketAddress))
			return null;
		if (inetSocketAddress.getAddress() != null)
			return inetSocketAddress.getAddress().getHostAddress();

		return inetSocketAddress.getHostString();
	}

	@Getter
	@RequiredArgsConstructor
	private static final class Request {
		private final @NotNull ConnectionIdentity identity;
		private final @NotNull HandshakeDecisionProcessor.InstructionTarget instructionTarget;
	}
}
