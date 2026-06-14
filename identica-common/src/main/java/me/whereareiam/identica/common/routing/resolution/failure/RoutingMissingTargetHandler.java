package me.whereareiam.identica.common.routing.resolution.failure;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.routing.attempt.RoutingAttemptFinishedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptFailure;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Singleton
public class RoutingMissingTargetHandler extends AbstractRoutingFailureHandler implements EventListener {
	private final Provider<Messages> messagesProvider;

	@Inject
	public RoutingMissingTargetHandler(
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
	public void onRoutingAttemptFinished(@NotNull RoutingAttemptFinishedEvent event) {
		RoutingAttemptReport report = event.getReport();
		RoutingAttemptFailure failure = report.getFailure();
		if (report.isAccepted()) return;
		if (failure == null || failure.getReason() != RoutingAttemptFailureReason.MISSING_SERVER) return;

		Logger.warn("Routing missing target connection=%s target=%s",
				event.getIntent().getConnectionUniqueId(),
				event.getIntent().getEndpoint().getServer());
		disconnect(event.getIntent(), resolveMessage(event.getIntent()));
	}

	private @NotNull Component resolveMessage(@NotNull RoutingIntent intent) {
		Messages messages = messagesProvider.get();
		List<String> scenarioLines = scenarioRouting(intent.getPipelineType(), messages).getMissingServer();
		List<String> lines = !scenarioLines.isEmpty()
				? scenarioLines
				: messages.getRouting().getMissingServer();
		if (lines.isEmpty()) return Component.empty();

		return Serializer.serialize(Serializer.template("{message}")
				.placeholders(Map.of("server", intent.getEndpoint().getServer()))
				.section("message", section -> section.lines(lines))
				.render());
	}
}
