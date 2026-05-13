package me.whereareiam.identica.provider.password.database;

import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.model.PasswordAccountPassword;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface PasswordAccountPersistenceService {
	@NotNull Optional<PasswordAccount> findBySubject(@NotNull String providerId, @NotNull String providerSubject);

	@NotNull PasswordAccount create(@NotNull PasswordAccount account);

	void updatePassword(
			@NotNull String providerId,
			@NotNull String providerSubject,
			@NotNull String passwordHash,
			@NotNull String hashingMethod,
			long updatedAt
	);

	void delete(@NotNull String providerId, @NotNull String providerSubject);

	void recordPasswordChange(@NotNull PasswordAccountPassword change);
}
