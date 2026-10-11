package me.whereareiam.identica.platform.common.stage.login;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import me.whereareiam.identica.platform.common.decision.ConnectionDecisionApplier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Decides a player's login. A refusal stored by a prepare stage is enforced first and Identica's flow never starts
 * for that player; otherwise the connection is processed with what the prepare stages resolved for it.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LoginDecisionProcessor {
	private final @NotNull ConnectionCoordinator connectionCoordinator;
	private final @NotNull IdentityService identityService;
	private final @NotNull PrepareStateStore prepareStateStore;
	private final @NotNull PreparedRefusal preparedRefusal;
	private final @NotNull ConnectionDecisionApplier decisionApplier;

	/**
	 * Decides a player's login.
	 *
	 * @param request player logging in
	 * @param target  where the platform refuses the player or applies the decision
	 * @return completion stage that finishes once the decision is applied
	 */
	public @NotNull CompletionStage<Void> process(
			@NotNull Request request,
			@NotNull Target target
	) {
		ConnectionIdentity observed = new ConnectionIdentity(request.username(), request.ip());
		observed.setOrigin(request.origin());
		String connectionKey = observed.connectionKey();

		Component refusal = preparedRefusal.find(request.connectionUniqueId(), connectionKey).orElse(null);
		if (refusal != null) {
			Logger.debug("Login refused by prepare connection=%s username=%s", request.connectionUniqueId(), request.username());
			target.refuse(refusal);
			return CompletableFuture.completedFuture(null);
		}

		PrepareDecision prepared = findPrepared(request.connectionUniqueId(), connectionKey);
		ProviderContext provider = prepared != null ? prepared.getProvider() : null;
		ConnectionIdentity identity = identity(request, prepared, provider);

		Logger.debug(
				"Login request connection=%s username=%s ip=%s key=%s preparedUniqueId=%s preparedProvider=%s preparedSubject=%s preparedEffective=%s",
				request.connectionUniqueId(),
				request.username(),
				request.ip(),
				identity.connectionKey(),
				prepared != null ? prepared.getAccountUniqueId() : null,
				provider != null ? provider.getProviderId() : null,
				provider != null ? provider.getProviderSubject() : null,
				prepared != null ? prepared.getEffectiveUsername() : null
		);

		ConnectionRequest connectionRequest = ConnectionRequest.builder()
				.identity(identity)
				.connectionUniqueId(request.connectionUniqueId())
				.provider(provider)
				.selection(prepared != null ? prepared.getSelection() : null)
				.intendedServer(request.intendedServer())
				.build();

		return connectionCoordinator.process(connectionRequest)
				.thenAccept(decision -> apply(request, identity, provider, decision, target));
	}

	private void apply(
			@NotNull Request request,
			@NotNull ConnectionIdentity identity,
			@Nullable ProviderContext provider,
			@Nullable ConnectionDecision decision,
			@NotNull Target target
	) {
		Logger.debug(
				"Login decision connection=%s status=%s provider=%s subject=%s",
				request.connectionUniqueId(),
				decision != null ? decision.getStatus() : null,
				provider != null ? provider.getProviderId() : null,
				provider != null ? provider.getProviderSubject() : null
		);

		UUID accountUniqueId = identity.getAccountUniqueId();
		Identity live = target.identity(accountUniqueId, identity.getUsername(), identity.getOrigin());
		live.setConnectionUsername(identity.getConnectionUsername());
		decisionApplier.apply(decision, live, target);

		ConnectionDecision.Status status = decision != null ? decision.getStatus() : null;
		if (status == ConnectionDecision.Status.ALLOW || status == ConnectionDecision.Status.WAIT)
			identityService.attach(request.connectionUniqueId(), accountUniqueId, live);
	}

	private @Nullable PrepareDecision findPrepared(@NotNull UUID connectionUniqueId, @Nullable String connectionKey) {
		PrepareDecision prepared = prepareStateStore.peek(connectionUniqueId).orElse(null);
		if (prepared != null) return prepared;
		if (connectionKey == null || connectionKey.isBlank()) return null;

		return prepareStateStore.peek(connectionKey).orElse(null);
	}

	private @NotNull ConnectionIdentity identity(
			@NotNull Request request,
			@Nullable PrepareDecision prepared,
			@Nullable ProviderContext provider
	) {
		String username = provider != null && !provider.getProviderUsername().isBlank()
				? provider.getProviderUsername()
				: request.username();

		ConnectionIdentity identity = new ConnectionIdentity(username, request.ip());
		identity.setConnectionUniqueId(request.connectionUniqueId());
		identity.setObservedUniqueId(request.connectionUniqueId());
		identity.setOrigin(request.origin());
		if (prepared != null) {
			if (prepared.getAccountUniqueId() != null) identity.setAccountUniqueId(prepared.getAccountUniqueId());
			identity.setClaimedUniqueId(prepared.getClaimedUniqueId());
		}

		return identity;
	}

	/**
	 * Player logging in, as the platform's login event shows it.
	 *
	 * @param connectionUniqueId unique ID the player logs in with
	 * @param username           username the player logs in with; with the address and origin it forms the
	 *                           connection key the profile stage stored its decision under
	 * @param ip                 player's address, if known
	 * @param origin             host and port the player connected to, if known
	 * @param intendedServer     server the player is headed for, if the platform knows it
	 */
	public record Request(
			@NotNull UUID connectionUniqueId,
			@NotNull String username,
			@Nullable String ip,
			ConnectionIdentity.@Nullable Origin origin,
			@Nullable String intendedServer
	) {
	}

	/**
	 * What a platform does with a login.
	 */
	public interface Target extends ConnectionDecisionApplier.Target {
		/**
		 * Refuses the player because a prepare stage refused the connection. Identica's flow has not started for
		 * the player.
		 *
		 * @param message reason shown to the player
		 */
		void refuse(@NotNull Component message);

		/**
		 * Creates the live identity the decision is applied to and attached as.
		 *
		 * @param accountUniqueId account the player resolved to, if any
		 * @param username        username the player continues with
		 * @param origin          host and port the player connected to, if known
		 * @return live identity of the player
		 */
		@NotNull Identity identity(
				@Nullable UUID accountUniqueId,
				@NotNull String username,
				ConnectionIdentity.@Nullable Origin origin
		);
	}
}
