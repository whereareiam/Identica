package me.whereareiam.identica.feature.sentinel;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionAdvanceAttemptEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionAttemptEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionProcessAttemptEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionResumeAttemptEvent;
import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelDecision;
import me.whereareiam.identica.feature.sentinel.type.SentinelScope;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import org.jetbrains.annotations.NotNull;

@Singleton
public class ConnectionAttemptSentinelLifecycle implements EventListener {
	private final SentinelService sentinelService;
	private final PipelineStateStore states;

	@Inject
	public ConnectionAttemptSentinelLifecycle(SentinelService sentinelService, PipelineStateStore states) {
		this.states = states;
		this.sentinelService = sentinelService;
	}

	@IdenticEvent
	public void onConnectionAttempt(@NotNull ConnectionAttemptEvent event) {
		if (event.getDecision() != null) return;

		SentinelScope scope = toScope(event);
		if (scope == null) return;

		SentinelContext context = SentinelContext.builder()
				.providerId(providerId(event))
				.connectionUniqueId(event.getConnectionUniqueId())
				.uniqueId(event.getAccountUniqueId())
				.username(event.getUsername())
				.ip(event.getIp())
				.build();

		SentinelDecision decision = sentinelService.evaluate(scope, context).orElse(null);
		if (decision == null || !decision.isLimited() || !decision.isDeny()) return;

		event.setDecision(ConnectionDecision.deny(decision.getMessage()));
	}

	private String providerId(ConnectionAttemptEvent event) {
		PipelineStateReference reference = PipelineStateReference.of(event.getUsername(), event.getIp());
		if (reference.isEmpty()) return null;

		return states.find(reference)
				.map(state -> state.getScenario(state.getPipelineType()))
				.map(ScenarioContext::getProvider)
				.map(ProviderContext::getProviderId)
				.orElse(null);
	}

	private SentinelScope toScope(@NotNull ConnectionAttemptEvent event) {
		return switch (event) {
			case ConnectionProcessAttemptEvent ignored -> SentinelScope.PROCESS;
			case ConnectionResumeAttemptEvent ignored -> SentinelScope.RESUME;
			case ConnectionAdvanceAttemptEvent ignored -> SentinelScope.ADVANCE;
			default -> null;
		};
	}
}
