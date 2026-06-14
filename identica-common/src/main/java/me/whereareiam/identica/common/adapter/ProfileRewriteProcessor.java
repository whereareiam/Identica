package me.whereareiam.identica.common.adapter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.connection.ConnectionCoordinator;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.type.PrepareStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProfileRewriteProcessor {
	private final @NotNull ConnectionCoordinator connectionCoordinator;
	private final @NotNull PrepareStateStore prepareStateStore;

	public @NotNull CompletionStage<Void> process(
			@NotNull Request request,
			@NotNull Target target
	) {
		PrepareRequest prepareRequest = PrepareRequest.builder()
				.stage(PrepareStage.PROFILE)
				.connectionKey(request.identity().connectionKey())
				.identity(request.identity())
				.build();

		return connectionCoordinator.prepare(prepareRequest)
				.handle((decision, error) -> {
					if (error != null) {
						Logger.severe("Profile prepare failed username=%s error=%s",
								request.identity().getUsername(),
								error.getMessage());
						return null;
					}

					PrepareDecision prepared = decision != null ? decision : PrepareDecision.allow();
					if (prepared.isDenied()) {
						Logger.debug("Profile prepare denied username=%s", request.identity().getUsername());
						storePreparedState(request.observedUniqueId(), prepareRequest.getConnectionKey(), prepared);
						target.deny(prepared);
						return null;
					}

					Rewrite rewrite = resolveRewrite(request, prepared);
					storePreparedState(rewrite.uniqueId(), prepareRequest.getConnectionKey(), prepared);
					target.apply(rewrite);
					return null;
				});
	}

	private @NotNull Rewrite resolveRewrite(
			@NotNull Request request,
			@NotNull PrepareDecision prepared
	) {
		UUID uniqueId = prepared.getAccountUniqueId() != null
				? prepared.getAccountUniqueId()
				: request.observedUniqueId();
		String username = prepared.getEffectiveUsername();
		if (username == null || username.isBlank())
			username = request.currentUsername();

		return new Rewrite(uniqueId, username);
	}

	private void storePreparedState(
			@Nullable UUID uniqueId,
			@Nullable String connectionKey,
			@NotNull PrepareDecision prepared
	) {
		if (uniqueId == null) return;
		prepareStateStore.put(uniqueId, connectionKey, prepared);
	}

	public record Request(
			@NotNull ConnectionIdentity identity,
			@Nullable UUID observedUniqueId,
			@NotNull String currentUsername
	) {
	}

	public record Rewrite(
			@Nullable UUID uniqueId,
			@NotNull String username
	) {
	}

	public interface Target {
		void apply(@NotNull Rewrite rewrite);

		default void deny(@NotNull PrepareDecision prepared) {
		}
	}
}
