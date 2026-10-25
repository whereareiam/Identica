package me.whereareiam.identica.provider.credential.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.provider.credential.CredentialMessageDefinitions;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.migration.MigrationPrecheckContext;
import me.whereareiam.identica.provider.migration.MigrationPrecheckResult;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import org.jetbrains.annotations.NotNull;


@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CredentialMigrationPrecheck implements ProviderMigrationPrecheck {
	private final Provider<CredentialMessages> messagesProvider;

	@Override
	public @NotNull MigrationPrecheckResult precheck(@NotNull MigrationPrecheckContext context) {
		return MigrationPrecheckResult.allow(CredentialMessageDefinitions.MOVE_CONFIRMED.create(messagesProvider.get()));
	}
}
