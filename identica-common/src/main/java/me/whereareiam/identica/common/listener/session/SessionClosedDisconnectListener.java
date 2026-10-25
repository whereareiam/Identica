package me.whereareiam.identica.common.listener.session;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.session.SessionClosedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.message.PlayerMessage;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Disconnects the player whose session a close request ends. A request naming a connection reaches only that
 * connection, on the proxy that holds it; a request for the account reaches its connection on every proxy.
 */
@Singleton
public class SessionClosedDisconnectListener implements EventListener {
	private final @NotNull IdentityService identityService;
	private final @NotNull Provider<Replication> replicationProvider;
	private final @NotNull MessageService messageService;

	@Inject
	public SessionClosedDisconnectListener(
			@NotNull IdentityService identityService,
			@NotNull Provider<Replication> replicationProvider,
			@NotNull MessageService messageService,
			@NotNull EventManager eventManager
	) {
		this.identityService = identityService;
		this.replicationProvider = replicationProvider;
		this.messageService = messageService;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onSessionClosed(@NotNull SessionClosedEvent event) {
		SessionCloseRequest request = event.getRequest();
		if (!request.isDisconnect()) return;

		PlayerMessage disconnectMessage = request.getDisconnectMessage();
		target(event).ifPresent(identity -> identity.disconnect(disconnectMessage != null
				? messageService.disconnectReason(identity, disconnectMessage)
				: Component.empty()));
	}

	private @NotNull Optional<Identity> target(@NotNull SessionClosedEvent event) {
		SessionConnection connection = event.getRequest().getConnection();
		if (connection == null)
			return identityService.findByAccountUniqueId(event.getUniqueId());

		String serverId = connection.getServerId();
		String localServerId = replicationProvider.get().getServerId();
		if (serverId != null && !serverId.trim().equalsIgnoreCase(localServerId.trim()))
			return Optional.empty();

		return identityService.findByConnectionUniqueId(connection.getConnectionUniqueId());
	}
}
