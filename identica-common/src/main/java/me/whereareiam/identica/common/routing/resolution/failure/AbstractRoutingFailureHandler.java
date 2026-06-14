package me.whereareiam.identica.common.routing.resolution.failure;

import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.IdentityAttachment;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
	abstract class AbstractRoutingFailureHandler {
	private final Provider<Messages> messagesProvider;
	private final PipelineStateStore pipelineStateStore;
	private final IdentityService identityService;
	private final SessionService sessionService;
	private final PlatformRoutingAdapter platformRoutingAdapter;

	protected void disconnect(
			@NotNull RoutingIntent intent,
			@NotNull RoutingAttemptFailureReason failureReason,
			@Nullable String platformReason
	) {
		cleanup(intent.getConnectionUniqueId());
		platformRoutingAdapter.disconnect(
				intent.getConnectionUniqueId(),
				resolveMessage(intent, failureReason, platformReason)
		);
	}

	private void cleanup(@NotNull UUID connectionUniqueId) {
		pipelineStateStore.clear(PipelineStateReference.builder()
				.connectionUniqueId(connectionUniqueId)
				.build());

		IdentityAttachment attachment = identityService
				.findAttachmentByConnectionUniqueId(connectionUniqueId)
				.orElse(null);
		UUID accountUniqueId = attachment != null
				? attachment.getAccountUniqueId()
				: null;

		if (accountUniqueId != null) sessionService.close(accountUniqueId).join();
	}

	private @NotNull Component resolveMessage(
			@NotNull RoutingIntent intent,
			@NotNull RoutingAttemptFailureReason failureReason,
			@Nullable String platformReason
	) {
		List<String> lines = failureReason == RoutingAttemptFailureReason.MISSING_SERVER
				? resolveMissingServerLines(intent)
				: resolveUnavailableServerLines(intent);
		String message = String.join("\n", lines);
		if (message.isBlank())
			return Component.empty();

		return Serializer.serialize(Serializer.render(message, Map.of(
				"server", intent.getEndpoint().getServer(),
				"serverReason", normalizeServerReason(intent, platformReason)
		)));
	}

	private @NotNull String normalizeServerReason(
			@NotNull RoutingIntent intent,
			@Nullable String platformReason
	) {
		if (platformReason == null || platformReason.isBlank())
			return String.join("\n", resolveUnavailableServerFallbackLines(intent));

		return platformReason.trim();
	}

	private @NotNull List<String> resolveMissingServerLines(@NotNull RoutingIntent intent) {
		Messages messages = messagesProvider.get();
		List<String> scenarioLines = scenarioRouting(intent.getPipelineType(), messages).getMissingServer();
		if (!scenarioLines.isEmpty()) return scenarioLines;
		return messages.getRouting().getMissingServer();
	}

	private @NotNull List<String> resolveUnavailableServerLines(@NotNull RoutingIntent intent) {
		Messages messages = messagesProvider.get();
		List<String> scenarioLines = scenarioRouting(intent.getPipelineType(), messages).getUnavailableServer().getMessage();
		if (!scenarioLines.isEmpty()) return scenarioLines;
		return messages.getRouting().getUnavailableServer().getMessage();
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

	private @NotNull Messages.Scenarios.Scenario.ScenarioRouting scenarioRouting(
			@Nullable PipelineType pipelineType,
			@NotNull Messages messages
	) {
		if (pipelineType == PipelineType.REGISTRATION) return messages.getScenarios().getRegistration().getRouting();
		if (pipelineType == PipelineType.MIGRATION) return messages.getScenarios().getMigration().getRouting();

		return messages.getScenarios().getAuthentication().getRouting();
	}
}
