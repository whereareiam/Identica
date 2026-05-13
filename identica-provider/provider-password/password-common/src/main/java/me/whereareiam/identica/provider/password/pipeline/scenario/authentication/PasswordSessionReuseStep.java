package me.whereareiam.identica.provider.password.pipeline.scenario.authentication;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.password.PasswordConstants;
import me.whereareiam.identica.provider.password.pipeline.scenario.AbstractPasswordStep;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PasswordSessionReuseStep extends AbstractPasswordStep {
	private final SessionService sessionService;

	@Inject
	public PasswordSessionReuseStep(SessionService sessionService) {
		super("password-session-reuse");
		this.sessionService = sessionService;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		String providerSubject = requireProviderSubject(context);

		Optional<Session> existingSession = sessionService
				.findByProviderSubject(PasswordConstants.PROVIDER_ID, providerSubject)
				.join();

		if (existingSession.isPresent() && matchesIp(existingSession.get(), context.getIp())) {
			return CompletableFuture.completedFuture(StepResult.complete(context));
		}

		return CompletableFuture.completedFuture(StepResult.proceed(context));
	}

	private boolean matchesIp(@NotNull Session session, String ip) {
		String sessionIp = normalizeIp(session.getIp());
		String candidateIp = normalizeIp(ip);
		if (sessionIp == null || candidateIp == null) return false;

		return sessionIp.equals(candidateIp);
	}

	private String normalizeIp(String ip) {
		if (ip == null) return null;
		String trimmed = ip.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
