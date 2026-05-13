package me.whereareiam.identica.provider.password.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.migration.MigrationPrecheckContext;
import me.whereareiam.identica.provider.migration.MigrationPrecheckResult;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PasswordMigrationPrecheck implements ProviderMigrationPrecheck {
	private final Provider<PasswordMessages> messagesProvider;

	@Override
	public @NotNull MigrationPrecheckResult precheck(@NotNull MigrationPrecheckContext context) {
		PasswordMessages.Commands.Password password = messagesProvider.get().getCommands().getPassword();
		List<String> kick = password.getConfirmed();
		String message = String.join("\n", kick);
		return MigrationPrecheckResult.allow(message);
	}
}
