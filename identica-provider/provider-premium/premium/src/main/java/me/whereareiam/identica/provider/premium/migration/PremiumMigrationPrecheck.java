package me.whereareiam.identica.provider.premium.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.migration.MigrationPrecheckContext;
import me.whereareiam.identica.provider.migration.MigrationPrecheckResult;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import me.whereareiam.identica.provider.premium.PremiumConstants;
import me.whereareiam.identica.provider.premium.PremiumMessageDefinitions;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.policy.PremiumHandshakeInstructions;
import org.jetbrains.annotations.NotNull;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PremiumMigrationPrecheck implements ProviderMigrationPrecheck {
	private final PremiumHandshakeInstructions instructions;
	private final ProviderAttemptStore attemptStore;
	private final Provider<PremiumMessages> messagesProvider;

	@Override
	public @NotNull MigrationPrecheckResult precheck(@NotNull MigrationPrecheckContext context) {
		queueForceOnlineHandshake(context);

		return MigrationPrecheckResult.allow(PremiumMessageDefinitions.MOVE_CONFIRMED.create(messagesProvider.get()));
	}

	private void queueForceOnlineHandshake(@NotNull MigrationPrecheckContext context) {
		String username = context.getUsername();
		String ip = context.getIp();
		if (username == null || username.isBlank() || ip == null || ip.isBlank()) return;

		instructions.forceOnline(username, ip);
		attemptStore.markAttempt(PremiumConstants.PROVIDER_ID, PremiumConstants.ATTEMPT_SCOPE_VERIFY, username, ip);
		Logger.debug(
				"Premium migration precheck marked verify attempt username=%s ip=%s provider=%s",
				username,
				ip,
				context.getProviderId()
		);
	}
}
