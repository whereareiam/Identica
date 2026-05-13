package me.whereareiam.identica.provider.password.sentinel;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.model.sentinel.SentinelContext;
import me.whereareiam.identica.model.sentinel.SentinelDecision;
import me.whereareiam.identica.provider.password.PasswordConstants;
import me.whereareiam.identica.provider.password.event.authentication.AuthenticationAttemptDecision;
import me.whereareiam.identica.provider.password.event.authentication.AuthenticationAttemptFailedEvent;
import me.whereareiam.identica.provider.password.event.authentication.AuthenticationAttemptSucceededEvent;
import me.whereareiam.identica.provider.password.model.authentication.AuthenticationAttemptContext;
import me.whereareiam.identica.sentinel.SentinelService;
import org.jetbrains.annotations.NotNull;

@Singleton
public class BruteForceSentinelLifecycle implements EventListener {
	private final SentinelService sentinelService;

	@Inject
	public BruteForceSentinelLifecycle(SentinelService sentinelService, EventManager eventManager) {
		this.sentinelService = sentinelService;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onPasswordAuthenticationFailed(@NotNull AuthenticationAttemptFailedEvent event) {
		if (event.getDecision() != null) return;

		SentinelContext context = sentinelContext(event);
		SentinelDecision decision = sentinelService.record(
				PasswordConstants.SENTINEL.BRUTE_FORCE,
				context
		);
		event.setDecision(toDecision(decision));
	}

	@IdenticEvent
	public void onPasswordAuthenticationSucceeded(@NotNull AuthenticationAttemptSucceededEvent event) {
		SentinelContext context = sentinelContext(event);
		sentinelService.clear(
				PasswordConstants.SENTINEL.BRUTE_FORCE,
				context
		);
	}

	private SentinelContext sentinelContext(@NotNull AuthenticationAttemptFailedEvent event) {
		AuthenticationAttemptContext context = event.getContext();
		return SentinelContext.builder()
				.connectionUniqueId(context.getConnectionUniqueId())
				.uniqueId(context.getIdentityUniqueId())
				.username(context.getUsername())
				.ip(context.getIp())
				.build();
	}

	private SentinelContext sentinelContext(@NotNull AuthenticationAttemptSucceededEvent event) {
		AuthenticationAttemptContext context = event.getContext();
		return SentinelContext.builder()
				.connectionUniqueId(context.getConnectionUniqueId())
				.uniqueId(context.getIdentityUniqueId())
				.username(context.getUsername())
				.ip(context.getIp())
				.build();
	}

	private AuthenticationAttemptDecision toDecision(SentinelDecision decision) {
		if (decision == null) return AuthenticationAttemptDecision.allow();

		boolean deny = decision.isLimited() && decision.isDeny();
		String denyMessage = deny ? decision.getMessage() : null;
		String warningMessage = decision.getWarningMessage();
		return new AuthenticationAttemptDecision(deny, denyMessage, warningMessage);
	}
}
