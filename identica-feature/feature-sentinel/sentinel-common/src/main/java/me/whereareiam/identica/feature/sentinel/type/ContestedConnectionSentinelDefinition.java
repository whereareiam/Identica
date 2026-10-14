package me.whereareiam.identica.feature.sentinel.type;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.sentinel.SentinelDefinition;
import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Throttles an address that keeps connecting under usernames other connections are logging in under. Each refused
 * connection is one attempt; once the address reaches the limit, its connections are refused for the lockout,
 * whatever username they use.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ContestedConnectionSentinelDefinition implements SentinelDefinition {
	/**
	 * Identifier of the sentinel's counters.
	 */
	public static final String ID = "contested-connection";
	private static final SentinelScope[] SCOPES = new SentinelScope[]{SentinelScope.PROCESS};

	private final Provider<SentinelSettings> settingsProvider;
	private final Provider<SentinelMessages> messagesProvider;

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public @NotNull SentinelScope[] scopes() {
		return SCOPES;
	}

	/**
	 * Only checks the lockout when a connection arrives; the attempts are the refusals, recorded when they happen.
	 */
	@Override
	public @NotNull SentinelMode modeFor(@NotNull SentinelScope scope) {
		return SentinelMode.CHECK;
	}

	@Override
	public @NotNull SentinelPolicy policy(@NotNull SentinelContext context) {
		if (!settingsProvider.get().isEnabled()) return new SentinelPolicy();

		SentinelPolicy policy = settingsProvider.get().getSentinels().getContestedConnection();
		if (policy.getLockout() == null) policy.setLockout(new SentinelPolicy.Lockout());

		policy.getLockout().setMessageSupplier((ignored, remainingSeconds) -> message(remainingSeconds));
		return policy;
	}

	/**
	 * Counts per address, so a client cannot spread its attempts over usernames.
	 */
	@Override
	public @NotNull SentinelKey key(@NotNull SentinelContext context) {
		String ip = context.getIp();
		return new SentinelKey(ip == null || ip.isBlank() ? null : ip.trim());
	}

	private @NotNull String message(long remainingSeconds) {
		String seconds = String.valueOf(Math.max(0L, remainingSeconds));
		return String.join("\n", messagesProvider.get().getContestedConnection().getDenied())
				.replace("{seconds}", seconds);
	}
}
