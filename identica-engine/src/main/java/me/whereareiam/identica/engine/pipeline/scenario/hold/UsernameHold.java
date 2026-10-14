package me.whereareiam.identica.engine.pipeline.scenario.hold;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.LoginSupersededEvent;
import me.whereareiam.identica.event.connection.UsernameHeldEvent;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.pipeline.PendingScenarioService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Decides between connections that log in under one username at once. The first connection holds the username until
 * its login ends; a connection from another address is refused meanwhile, unless a provider proves that it belongs to
 * the account. A connection that logs in, or proves the account, wins: every other connection still logging in to
 * the account or under its username is disconnected, on whichever proxy it is.
 */
@Singleton
public class UsernameHold implements EventListener {
	private final UsernameHolds holds;
	private final IdentityService identityService;
	private final PendingScenarioService pendingScenarioService;
	private final EventManager eventManager;
	private final Provider<Messages> messagesProvider;
	private final Provider<Replication> replicationProvider;

	@Inject
	public UsernameHold(
			@NotNull UsernameHolds holds,
			@NotNull IdentityService identityService,
			@NotNull PendingScenarioService pendingScenarioService,
			@NotNull EventManager eventManager,
			@NotNull Provider<Messages> messagesProvider,
			@NotNull Provider<Replication> replicationProvider
	) {
		this.holds = holds;
		this.identityService = identityService;
		this.pendingScenarioService = pendingScenarioService;
		this.eventManager = eventManager;
		this.messagesProvider = messagesProvider;
		this.replicationProvider = replicationProvider;
		eventManager.register(this);
	}

	/**
	 * Lets a connection enter its login, or refuses it while another connection holds its username.
	 *
	 * @param request request of the arriving connection
	 * @return the refusal, or {@code null} when the connection may log in
	 */
	public @Nullable ConnectionDecision enter(@NotNull ConnectionRequest request) {
		ConnectionIdentity identity = request.getIdentity();
		if (holds.findHolder(request.getConnectionUniqueId(), identity).isEmpty()) return null;

		if (isProven(request)) {
			Logger.debug("Username hold lost to proof username=%s connection=%s", identity.getUsername(), request.getConnectionUniqueId());
			supersede(identity.getAccountUniqueId(), identity.getConnectionUsername(), request.getConnectionUniqueId());
			return null;
		}

		Logger.debug("Username held refused username=%s connection=%s ip=%s", identity.getUsername(), request.getConnectionUniqueId(), identity.getIp());
		eventManager.call(new UsernameHeldEvent(request.getConnectionUniqueId(), identity.getConnectionUsername(), identity.getIp()));
		return ConnectionDecision.deny(lines(messagesProvider.get().getEngine().getUsernameHeld()));
	}

	/**
	 * A connection that logged in wins the login of its account.
	 */
	@IdenticEvent
	public void onSessionOpened(@NotNull SessionOpenedEvent event) {
		Identity winner = identityService.findByConnectionUniqueId(event.getConnectionUniqueId()).orElse(null);
		String username = winner != null ? winner.getConnectionUsername() : event.getSession().getOriginalUsername();
		supersede(event.getSession().getUniqueId(), username, event.getConnectionUniqueId());
	}

	/**
	 * Disconnects the connections of this proxy that were still logging in when another connection won.
	 */
	@IdenticEvent
	public void onLoginSuperseded(@NotNull LoginSupersededEvent event) {
		String message = lines(messagesProvider.get().getEngine().getConcurrentLoginKick());
		for (Identity identity : identityService.list()) {
			if (!isSupersededBy(identity, event)) continue;

			Logger.debug("Login superseded username=%s connection=%s", identity.getUsername(), identity.getConnectionUniqueId());
			identity.disconnect(Serializer.serialize(identity, message));
		}
	}

	private boolean isSupersededBy(@NotNull Identity identity, @NotNull LoginSupersededEvent event) {
		UUID connectionUniqueId = identity.getConnectionUniqueId();
		if (connectionUniqueId == null || isWinner(connectionUniqueId, event.getWinner())) return false;
		if (pendingScenarioService.find(connectionUniqueId).isEmpty()) return false;

		return event.getAccountUniqueId().equals(identity.getAccountUniqueId())
				|| event.getUsername().equalsIgnoreCase(identity.getConnectionUsername());
	}

	private boolean isWinner(@NotNull UUID connectionUniqueId, @NotNull SessionConnection winner) {
		return serverId().equalsIgnoreCase(winner.getServerId() == null ? "" : winner.getServerId())
				&& connectionUniqueId.equals(winner.getConnectionUniqueId());
	}

	private void supersede(@Nullable UUID accountUniqueId, @Nullable String username, @Nullable UUID connectionUniqueId) {
		if (accountUniqueId == null || username == null || username.isBlank() || connectionUniqueId == null) return;

		eventManager.call(new LoginSupersededEvent(
				accountUniqueId,
				username,
				new SessionConnection(serverId(), connectionUniqueId)
		));
	}

	/**
	 * Whether a provider proved the connection's identity: the claim that won its selection carries proof.
	 */
	private static boolean isProven(@NotNull ConnectionRequest request) {
		ProviderSelection selection = request.getSelection();
		ProviderClaim winner = selection != null ? selection.getWinner() : null;

		return winner != null && winner.hasProof() && request.getIdentity().getAccountUniqueId() != null;
	}

	private @NotNull String serverId() {
		return replicationProvider.get().getServerId();
	}

	private static @NotNull String lines(@NotNull List<String> lines) {
		return String.join("\n", lines);
	}
}
