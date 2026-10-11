package me.whereareiam.identica.platform.common.stage.profile;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.platform.common.stage.login.PreparedRefusal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProfileRewriteProcessor {
	private final @NotNull ConnectionCoordinator connectionCoordinator;
	private final @NotNull PrepareStateStore prepareStateStore;
	private final @NotNull PreparedRefusal preparedRefusal;

	/**
	 * Prepares a connecting profile. An allowed profile is rewritten through the target before the outcome
	 * completes; a refusal or a failed run is left to the platform. A failed run clears a denial stored by an
	 * earlier attempt and keeps any other stored decision.
	 *
	 * @param request profile the connection presented
	 * @param target  where an allowed profile is rewritten
	 * @return what preparation decided
	 */
	public @NotNull CompletionStage<ProfileOutcome> process(
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
						// A denial from an earlier attempt must not be enforced on this one; this attempt's handshake
						// decision stays, so the login keeps its provider context.
						prepareStateStore.clearIf(
								request.observedUniqueId(),
								prepareRequest.getConnectionKey(),
								PrepareDecision::isDenied
						);
						return new ProfileOutcome.Failed(error);
					}

					PrepareDecision prepared = decision != null ? decision : PrepareDecision.allow();
					if (prepared.isDenied()) {
						Logger.debug("Profile prepare denied username=%s", request.identity().getUsername());
						storePreparedState(request.observedUniqueId(), prepareRequest.getConnectionKey(), prepared);
						return new ProfileOutcome.Refused(preparedRefusal.message(prepared));
					}

					ProfileOutcome.Rewritten rewrite = resolveRewrite(request, prepared);
					storePreparedState(rewrite.uniqueId(), prepareRequest.getConnectionKey(), prepared);
					target.apply(rewrite);
					return rewrite;
				});
	}

	private @NotNull ProfileOutcome.Rewritten resolveRewrite(
			@NotNull Request request,
			@NotNull PrepareDecision prepared
	) {
		UUID uniqueId = prepared.getAccountUniqueId() != null
				? prepared.getAccountUniqueId()
				: request.observedUniqueId();
		String username = prepared.getEffectiveUsername();
		if (username == null || username.isBlank())
			username = request.currentUsername();

		return new ProfileOutcome.Rewritten(uniqueId, username);
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

	/**
	 * Where a platform rewrites the connecting profile to the prepared identity.
	 */
	@FunctionalInterface
	public interface Target {
		/**
		 * Rewrites the connecting profile to the prepared identity.
		 *
		 * @param rewrite unique ID and username the connection continues with
		 */
		void apply(@NotNull ProfileOutcome.Rewritten rewrite);
	}
}
