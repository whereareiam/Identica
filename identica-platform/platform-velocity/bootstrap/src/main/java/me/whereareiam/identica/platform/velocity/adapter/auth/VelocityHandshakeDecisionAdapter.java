package me.whereareiam.identica.platform.velocity.adapter.auth;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import me.whereareiam.identica.common.adapter.HandshakeDecisionProcessor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeApplierRegistry;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeDecisionAdapter;
import me.whereareiam.identica.platform.velocity.api.handshake.VelocityHandshakeContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class VelocityHandshakeDecisionAdapter implements PlatformHandshakeDecisionAdapter<PreLoginEvent> {
	private final @NotNull HandshakeDecisionProcessor processor;
	private final @NotNull PlatformHandshakeApplierRegistry<VelocityHandshakeContext> applierRegistry;

	@Inject
	public VelocityHandshakeDecisionAdapter(
			@NotNull HandshakeDecisionProcessor processor,
			@NotNull PlatformHandshakeApplierRegistry<VelocityHandshakeContext> applierRegistry
	) {
		this.processor = processor;
		this.applierRegistry = applierRegistry;
	}

	public @NotNull CompletionStage<Void> process(@NotNull PreLoginEvent event) {
		Request request = request(event);
		if (request == null) return CompletableFuture.completedFuture(null);

		return CompletableFuture.completedFuture(null).thenCompose(ignored -> {
			if (!event.getResult().isAllowed())
				return CompletableFuture.completedFuture(null);

			return processor.process(toProcessorRequest(request), target(event)).toCompletableFuture();
		});
	}

	private @Nullable Request request(@NotNull PreLoginEvent event) {
		String resolvedIp = resolveIp(event);
		if (resolvedIp == null) {
			Logger.warn("PreLogin missing remote IP for %s, skipping handshake processing", event.getUsername());
			return null;
		}

		ConnectionIdentity identity = new ConnectionIdentity(event.getUsername(), resolvedIp);
		identity.setClaimedUniqueId(event.getUniqueId());
		applyOrigin(identity, event);
		VelocityHandshakeContext context = new VelocityHandshakeContext(event);
		return new Request(
				identity,
				instruction -> applierRegistry.applyAll(context, instruction)
		);
	}

	private @NotNull HandshakeDecisionProcessor.Request toProcessorRequest(@NotNull Request request) {
		return new HandshakeDecisionProcessor.Request(request.identity(), request.instructionTarget());
	}

	private @NotNull HandshakeDecisionProcessor.Target target(@NotNull PreLoginEvent event) {
		return message -> event.setResult(PreLoginEvent.PreLoginComponentResult.denied(message));
	}

	private void applyOrigin(@NotNull ConnectionIdentity identity, @NotNull PreLoginEvent event) {
		InetSocketAddress virtualHost = event.getConnection().getVirtualHost().orElse(null);
		if (virtualHost == null) return;

		identity.setOrigin(new ConnectionIdentity.Origin(
				virtualHost.getHostString(),
				virtualHost.getPort()
		));
	}

	private @Nullable String resolveIp(@NotNull PreLoginEvent event) {
		if (event.getConnection().getRemoteAddress() == null) return null;

		if (event.getConnection().getRemoteAddress().getAddress() != null) {
			String hostAddress = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
			if (hostAddress != null && !hostAddress.isBlank()) return hostAddress;
		}

		String hostString = event.getConnection().getRemoteAddress().getHostString();
		if (hostString == null || hostString.isBlank()) return null;

		return hostString;
	}

	private record Request(
			@NotNull ConnectionIdentity identity,
			@NotNull HandshakeDecisionProcessor.InstructionTarget instructionTarget
	) {
	}
}
