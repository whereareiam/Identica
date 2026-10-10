package me.whereareiam.identica.provider.credential.pipeline.step.base;

import com.google.inject.Provider;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.credential.event.authentication.AuthenticationAttemptDecision;
import me.whereareiam.identica.provider.credential.event.authentication.AuthenticationAttemptFailedEvent;
import me.whereareiam.identica.provider.credential.event.authentication.AuthenticationAttemptSucceededEvent;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.model.authentication.AuthenticationAttemptContext;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractCredentialAttemptStep extends AbstractCredentialStatefulStep {
	private final EventManager eventManager;

	protected AbstractCredentialAttemptStep(
			@NotNull String name,
			@NotNull Provider<Engine> coreSettingsProvider,
			@NotNull EventManager eventManager
	) {
		super(name, coreSettingsProvider);
		this.eventManager = eventManager;
	}

	protected final @NotNull AuthenticationAttemptDecision recordBruteForceDecision(
			@NotNull CredentialAccount credential,
			@NotNull ScenarioContext context
	) {
		AuthenticationAttemptFailedEvent event = new AuthenticationAttemptFailedEvent(
				attemptContext(credential, context),
				null
		);
		eventManager.call(event);
		AuthenticationAttemptDecision decision = event.getDecision();
		return decision != null ? decision : AuthenticationAttemptDecision.allow();
	}

	protected final void clearBruteForce(
			@NotNull CredentialAccount credential,
			@NotNull ScenarioContext context
	) {
		eventManager.call(new AuthenticationAttemptSucceededEvent(attemptContext(credential, context)));
	}

	private @NotNull AuthenticationAttemptContext attemptContext(
			@NotNull CredentialAccount credential,
			@NotNull ScenarioContext context
	) {
		return new AuthenticationAttemptContext(
				credential,
				context.getConnectionUniqueId(),
				context.getAccountUniqueId(),
				context.getUsername(),
				context.getIp()
		);
	}
}
