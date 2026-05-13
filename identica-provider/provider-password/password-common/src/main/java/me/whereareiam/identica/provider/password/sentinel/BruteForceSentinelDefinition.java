package me.whereareiam.identica.provider.password.sentinel;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.provider.password.PasswordConstants;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.config.PasswordSettings;
import me.whereareiam.identica.model.sentinel.SentinelContext;
import me.whereareiam.identica.model.sentinel.SentinelPolicy;
import me.whereareiam.identica.sentinel.SentinelDefinition;
import me.whereareiam.identica.type.sentinel.SentinelMode;
import me.whereareiam.identica.type.sentinel.SentinelScope;

import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BruteForceSentinelDefinition implements SentinelDefinition {
	private static final SentinelScope[] SCOPES = new SentinelScope[]{
			SentinelScope.PROCESS,
			SentinelScope.RESUME,
			SentinelScope.ADVANCE
	};

	private final Provider<PasswordSettings> settingsProvider;
	private final Provider<PasswordMessages> messagesProvider;

	@Override
	public String id() {
		return PasswordConstants.SENTINEL.BRUTE_FORCE;
	}

	@Override
	public SentinelScope[] scopes() {
		return SCOPES;
	}

	@Override
	public SentinelMode modeFor(SentinelScope scope) {
		return SentinelMode.CHECK;
	}

	@Override
	public SentinelPolicy policy(SentinelContext ctx) {
		PasswordSettings settings = settingsProvider.get();
		PasswordSettings.Scenario.Authentication authentication = settings != null
				&& settings.getScenario() != null
				? settings.getScenario().getAuthentication()
				: null;
		PasswordSettings.Scenario.Authentication.Bruteforce bruteForce = authentication != null
				? authentication.getBruteforce()
				: null;

		SentinelPolicy policy = new SentinelPolicy();
		int maxAttempts = bruteForce != null ? bruteForce.getMaxAttempts() : 0;
		policy.setMaxAttempts(maxAttempts);
		policy.setEnabled(maxAttempts > 0);

		PasswordSettings.Scenario.Authentication.Bruteforce.Lockout lockout = bruteForce != null
				? bruteForce.getLockout()
				: null;

		SentinelPolicy.Lockout policyLockout = policy.getLockout();
		if (policyLockout == null) {
			policyLockout = new SentinelPolicy.Lockout();
			policy.setLockout(policyLockout);
		}

		policyLockout.setEnabled(lockout == null || lockout.isEnabled());
		policyLockout.setDuration(lockout != null ? lockout.getDuration() : null);
		policyLockout.setMessageSupplier((ignored, remainingSeconds) -> buildLockoutMessage(remainingSeconds));

		PasswordSettings.Scenario.Authentication.Bruteforce.Warning warning = bruteForce != null
				? bruteForce.getWarning()
				: null;

		SentinelPolicy.Warning policyWarning = policy.getWarning();
		if (policyWarning == null) {
			policyWarning = new SentinelPolicy.Warning();
			policy.setWarning(policyWarning);
		}

		policyWarning.setEnabled(warning != null && warning.isEnabled());
		policyWarning.setThresholdPercentage(warning != null ? warning.getThresholdPercentage() : 0);
		policyWarning.setMessageSupplier((ignored, remainingAttempts) -> buildWarningMessage(remainingAttempts));

		return policy;
	}

	private String buildLockoutMessage(long remainingSeconds) {
		PasswordMessages messages = messagesProvider.get();
		if (messages == null || messages.getScenario().getAuthentication().getBruteforce() == null) return "";

		List<String> lines = messages.getScenario().getAuthentication().getBruteforce().getExceeded();
		if (lines == null || lines.isEmpty()) return "";

		String seconds = String.valueOf(Math.max(0L, remainingSeconds));
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i) == null ? "" : lines.get(i);
			if (i > 0) builder.append("\n");
			builder.append(line.replace("{seconds}", seconds));
		}

		return builder.toString();
	}

	private String buildWarningMessage(int remainingAttempts) {
		PasswordMessages messages = messagesProvider.get();
		if (messages == null || messages.getScenario().getAuthentication().getBruteforce() == null) return "";

		List<String> lines = messages.getScenario().getAuthentication().getBruteforce().getRemaining();
		if (lines == null || lines.isEmpty()) return "";

		String remaining = String.valueOf(Math.max(0, remainingAttempts));
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i) == null ? "" : lines.get(i);
			if (i > 0) builder.append("\n");
			builder.append(line.replace("{remaining}", remaining));
		}

		return builder.toString();
	}
}
