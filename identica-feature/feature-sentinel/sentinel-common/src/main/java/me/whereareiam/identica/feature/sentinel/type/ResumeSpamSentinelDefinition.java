package me.whereareiam.identica.feature.sentinel.type;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;
import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
import me.whereareiam.identica.feature.sentinel.SentinelDefinition;
import me.whereareiam.identica.feature.sentinel.type.SentinelMode;
import me.whereareiam.identica.feature.sentinel.type.SentinelScope;

import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ResumeSpamSentinelDefinition implements SentinelDefinition {
	private static final SentinelScope[] SCOPES = new SentinelScope[]{
			SentinelScope.PROCESS,
			SentinelScope.RESUME,
			SentinelScope.ADVANCE
	};

	private final Provider<SentinelSettings> settingsProvider;
	private final Provider<SentinelMessages> messagesProvider;

	@Override
	public String id() {
		return "resume-spam";
	}

	@Override
	public SentinelScope[] scopes() {
		return SCOPES;
	}

	@Override
	public SentinelMode modeFor(SentinelScope scope) {
		return SentinelMode.RECORD;
	}

	@Override
	public SentinelPolicy policy(SentinelContext ctx) {
		if (!settingsProvider.get().isEnabled()) return new SentinelPolicy();
		SentinelPolicy policy = settingsProvider.get().getSentinels().getResumeSpam();
		if (policy.getLockout() == null) {
			policy.setLockout(new SentinelPolicy.Lockout());
		}

		policy.getLockout().setMessageSupplier((ignored, remainingSeconds) -> buildMessage(remainingSeconds));
		return policy;
	}

	private String buildMessage(long remainingSeconds) {
		SentinelMessages messages = messagesProvider.get();
		List<String> lines = messages.getResumeSpam().getDenied();

		String seconds = String.valueOf(Math.max(0L, remainingSeconds));
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i) == null ? "" : lines.get(i);
			if (i > 0) {
				builder.append("\n");
			}
			builder.append(line.replace("{seconds}", seconds));
		}

		return builder.toString();
	}
}
