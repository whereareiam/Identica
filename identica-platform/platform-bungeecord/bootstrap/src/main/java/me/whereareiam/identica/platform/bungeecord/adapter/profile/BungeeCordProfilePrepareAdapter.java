package me.whereareiam.identica.platform.bungeecord.adapter.profile;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.common.adapter.ProfileRewriteProcessor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordProfilePrepareAdapter {
	private final @NotNull ProfileRewriteProcessor processor;

	public void prepare(@NotNull LoginEvent event) {
		PendingConnection connection = event.getConnection();
		UUID observedUniqueId = connection.getUniqueId();
		if (observedUniqueId == null) {
			Logger.debug("Bungee profile prepare skipped username=%s reason=missing-unique-id", connection.getName());
			return;
		}

		ConnectionIdentity identity = new ConnectionIdentity(connection.getName(), resolveIp(connection));
		identity.setObservedUniqueId(observedUniqueId);
		applyOrigin(identity, connection);

		processor.process(
						new ProfileRewriteProcessor.Request(identity, observedUniqueId, connection.getName()),
						target(event)
				)
				.toCompletableFuture()
				.join();
	}

	private @NotNull ProfileRewriteProcessor.Target target(
			@NotNull LoginEvent event
	) {
		return new ProfileRewriteProcessor.Target() {
			@Override
			public void apply(@NotNull ProfileRewriteProcessor.Rewrite rewrite) {
			}

			@Override
			public void deny(@NotNull PrepareDecision prepared) {
				event.setCancelled(true);
				if (prepared.getDenialMessage() != null && !prepared.getDenialMessage().isBlank())
					event.setReason(TextComponent.fromLegacy(prepared.getDenialMessage()));
			}
		};
	}

	private void applyOrigin(@NotNull ConnectionIdentity identity, @NotNull PendingConnection connection) {
		if (connection.getVirtualHost() == null) return;
		identity.setOrigin(new ConnectionIdentity.Origin(
				connection.getVirtualHost().getHostString(),
				connection.getVirtualHost().getPort()
		));
	}

	private @Nullable String resolveIp(@NotNull PendingConnection connection) {
		SocketAddress address = connection.getSocketAddress();
		if (!(address instanceof InetSocketAddress inetSocketAddress))
			return null;

		if (inetSocketAddress.getAddress() != null)
			return inetSocketAddress.getAddress().getHostAddress();

		return inetSocketAddress.getHostString();
	}
}
