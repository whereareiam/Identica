package me.whereareiam.identica.common.routing.resolution.failure;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.routing.intent.RoutingIntentExhaustedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptFailure;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Singleton
public class RoutingUnavailableTargetHandler extends AbstractRoutingFailureHandler implements EventListener {
	private final Provider<Messages> messagesProvider;

	@Inject
	public RoutingUnavailableTargetHandler(
			@NotNull Provider<Messages> messagesProvider,
			@NotNull IdentityService identityService,
			@NotNull PlatformRoutingAdapter platformRoutingAdapter,
			@NotNull ConnectionLifecycleService connectionLifecycleService,
			@NotNull EventManager eventManager
	) {
		super(identityService, platformRoutingAdapter, connectionLifecycleService);
		this.messagesProvider = messagesProvider;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onRoutingIntentExhausted(@NotNull RoutingIntentExhaustedEvent event) {
		RoutingIntent intent = event.getIntent();
		RoutingAttemptFailure failure = intent.getAttemptState().getLastFailure();
		RoutingAttemptFailureReason failureReason = failure != null ? failure.getReason() : null;
		if (!isDisconnectOnExhausted(failureReason)) return;

		Logger.warn("Routing unavailable target exhausted connection=%s target=%s reason=%s detail=%s",
				intent.getConnectionUniqueId(),
				intent.getEndpoint().getServer(),
				failureReason,
				failure.getDetail());
		disconnect(intent, resolveMessage(intent, failure.getDetail()));
	}

	private boolean isDisconnectOnExhausted(@Nullable RoutingAttemptFailureReason failureReason) {
		if (failureReason == null) return false;
		return failureReason == RoutingAttemptFailureReason.SERVER_DISCONNECTED
				|| failureReason == RoutingAttemptFailureReason.CONNECTION_EXCEPTION
				|| failureReason == RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING;
	}

	private @NotNull Component resolveMessage(
			@NotNull RoutingIntent intent,
			@Nullable String platformReason
	) {
		Messages messages = messagesProvider.get();
		List<String> scenarioLines = scenarioRouting(intent.getPipelineType(), messages)
				.getUnavailableServer()
				.getMessage();
		List<String> lines = !scenarioLines.isEmpty()
				? scenarioLines
				: messages.getRouting().getUnavailableServer().getMessage();
		if (lines.isEmpty()) return Component.empty();

		return Serializer.serialize(Serializer.template("{message}")
				.placeholders(Map.of(
						"server", intent.getEndpoint().getServer(),
						"serverReason", normalizeServerReason(intent, platformReason)
				))
				.section("message", section -> section.lines(lines))
				.render());
	}

	private @NotNull String normalizeServerReason(
			@NotNull RoutingIntent intent,
			@Nullable String platformReason
	) {
		if (platformReason == null || platformReason.isBlank())
			return String.join("\n", resolveUnavailableServerFallbackLines(intent));

		return platformReason.trim();
	}

	private @NotNull List<String> resolveUnavailableServerFallbackLines(@NotNull RoutingIntent intent) {
		Messages messages = messagesProvider.get();
		List<String> scenarioFallbackLines = scenarioRouting(intent.getPipelineType(), messages)
				.getUnavailableServer()
				.getFallbackReason();
		if (!scenarioFallbackLines.isEmpty()) return scenarioFallbackLines;

		List<String> fallbackLines = messages.getRouting().getUnavailableServer().getFallbackReason();
		if (!fallbackLines.isEmpty()) return fallbackLines;
		return List.of();
	}
}
