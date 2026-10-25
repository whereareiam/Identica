package me.whereareiam.identica.common.routing;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.routing.RoutingTargetMissingEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.message.ConnectionMessages;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
public class RoutingTargetMissingListener implements EventListener {
	private final Provider<Messages> messagesProvider;

	@Inject
	public RoutingTargetMissingListener(
			Provider<Messages> messagesProvider,
			EventManager eventManager
	) {
		this.messagesProvider = messagesProvider;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onTargetMissing(RoutingTargetMissingEvent event) {
		RoutingIntent intent = event.getIntent();
		String server = intent.getEndpoint().getServer();
		if (server == null || server.isBlank()) return;

		String actor = event.getUsername() != null && !event.getUsername().isBlank()
				? event.getUsername()
				: event.getConnectionUniqueId().toString();
		Logger.warn("Routing target server %s not found for %s", server, actor);

		event.setDisconnect(true);
		if (event.getMessage() == null || event.getMessage().isEmpty())
			event.setMessage(missingServerMessage(intent.getPipelineType()));
	}

	/**
	 * Builds the reason from the scenario's own message, or from the shared one when the scenario
	 * has none or the intent belongs to no scenario.
	 */
	private @NotNull PlayerMessage missingServerMessage(@Nullable PipelineType scenario) {
		Messages messages = messagesProvider.get();
		if (scenario != null) {
			PlayerMessage message = ScenarioMessages.of(scenario).getMissingServer().create(messages);
			if (!message.isEmpty()) return message;
		}

		return ConnectionMessages.MISSING_SERVER.create(messages);
	}
}
