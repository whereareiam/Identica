package me.whereareiam.identica.common.routing.resolution.failure;

import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.identity.IdentityAttachment;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.type.pipeline.PipelineType;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@RequiredArgsConstructor
abstract class AbstractRoutingFailureHandler {
	private final IdentityService identityService;
	private final PlatformRoutingAdapter platformRoutingAdapter;
	private final ConnectionLifecycleService connectionLifecycleService;

	protected void disconnect(
			@NotNull RoutingIntent intent,
			@NotNull Component message
	) {
		connectionLifecycleService.terminated(
				intent.getConnectionUniqueId(),
				resolveAccountUniqueId(intent.getConnectionUniqueId()),
				intent.getPipelineType()
		);
		platformRoutingAdapter.disconnect(
				intent.getConnectionUniqueId(),
				message
		);
	}

	private @Nullable UUID resolveAccountUniqueId(@NotNull UUID connectionUniqueId) {
		IdentityAttachment attachment = identityService
				.findAttachmentByConnectionUniqueId(connectionUniqueId)
				.orElse(null);

		return attachment != null
				? attachment.getAccountUniqueId()
				: null;
	}

	protected @NotNull Messages.Scenarios.Scenario.ScenarioRouting scenarioRouting(
			@Nullable PipelineType pipelineType,
			@NotNull Messages messages
	) {
		if (pipelineType == PipelineType.REGISTRATION) return messages.getScenarios().getRegistration().getRouting();
		if (pipelineType == PipelineType.MIGRATION) return messages.getScenarios().getMigration().getRouting();

		return messages.getScenarios().getAuthentication().getRouting();
	}
}
