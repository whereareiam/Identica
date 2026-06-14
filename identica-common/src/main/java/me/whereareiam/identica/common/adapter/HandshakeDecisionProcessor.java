package me.whereareiam.identica.common.adapter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.connection.ConnectionCoordinator;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.type.PrepareStage;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class HandshakeDecisionProcessor {
	private final @NotNull ConnectionCoordinator connectionCoordinator;
	private final @NotNull PrepareStateStore prepareStateStore;
	private final @NotNull HandshakeStore handshakeStore;
	private final @NotNull Provider<Messages> messagesProvider;

	public @NotNull CompletionStage<Void> process(
			@NotNull Request request,
			@NotNull Target target
	) {
		PrepareRequest prepareRequest = PrepareRequest.builder()
				.stage(PrepareStage.HANDSHAKE)
				.connectionKey(request.identity().connectionKey())
				.identity(request.identity())
				.build();

		return connectionCoordinator.prepare(prepareRequest)
				.handle((decision, error) -> {
					if (error != null) {
						Logger.severe("Prepare handshake failed %s", error);
						return null;
					}

					PrepareDecision prepared = decision != null ? decision : PrepareDecision.allow();
					storePreparedState(prepareRequest.getConnectionKey(), prepared);

					HandshakeDecision resolved = resolveHandshakeDecision(prepared);
					apply(resolved, target);
					applyHandshakeInstruction(request, prepared);

					return null;
				});
	}

	private void apply(
			@Nullable HandshakeDecision decision,
			@NotNull Target target
	) {
		if (decision == null || decision.getStatus() == null) return;
		if (decision.getStatus() == HandshakeDecision.Status.DENY)
			target.deny(Serializer.serialize(resolveHandshakeMessage(decision.getMessage())));
	}

	private @NotNull String resolveHandshakeMessage(@Nullable String message) {
		if (message != null && !message.isBlank()) return message;
		return String.join("\n", messagesProvider.get().getEngine().getPrepare().getHandshakeDenied());
	}

	private void storePreparedState(@Nullable String connectionKey, @NotNull PrepareDecision prepared) {
		if (connectionKey == null || connectionKey.isBlank()) return;
		prepareStateStore.put(connectionKey, prepared);
	}

	private @NotNull HandshakeDecision resolveHandshakeDecision(@NotNull PrepareDecision prepared) {
		if (prepared.getHandshake() != null) return prepared.getHandshake();
		if (prepared.isDenied()) return HandshakeDecision.deny(prepared.getDenialMessage());

		return HandshakeDecision.allow();
	}

	private void applyHandshakeInstruction(
			@NotNull Request request,
			@NotNull PrepareDecision prepared
	) {
		if (prepared.isDenied()) return;

		ConnectionIdentity identity = request.identity();
		String ip = identity.getIp();
		if (ip == null || ip.isBlank()) return;

		handshakeStore.consumeInstruction(identity.getUsername(), ip)
				.ifPresent(request.instructionTarget()::apply);
	}

	public record Request(
			@NotNull ConnectionIdentity identity,
			@NotNull InstructionTarget instructionTarget
	) {
	}

	public interface InstructionTarget {
		void apply(@NotNull HandshakeInstruction instruction);
	}

	public interface Target {
		void deny(@NotNull Component message);
	}
}
