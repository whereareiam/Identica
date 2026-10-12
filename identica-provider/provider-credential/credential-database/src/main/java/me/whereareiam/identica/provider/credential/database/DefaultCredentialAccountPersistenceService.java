package me.whereareiam.identica.provider.credential.database;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.provider.credential.database.entity.CredentialAccountEntity;
import me.whereareiam.identica.provider.credential.database.entity.CredentialAccountHistoryEntity;
import me.whereareiam.identica.provider.credential.database.mapper.CredentialAccountHistoryMapper;
import me.whereareiam.identica.provider.credential.database.mapper.CredentialAccountMapper;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.model.CredentialAccountHistory;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultCredentialAccountPersistenceService implements CredentialAccountPersistenceService {
	private final Provider<EntityAccess<CredentialAccountEntity>> credentials;
	private final Provider<EntityAccess<CredentialAccountHistoryEntity>> passwordChanges;

	@Override
	public @NotNull Optional<CredentialAccount> findBySubject(
			@NotNull String providerId,
			@NotNull String providerSubject
	) {
		if (providerId.isBlank() || providerSubject.isBlank()) return Optional.empty();
		return credentials.get().find(providerId, providerSubject)
				.map(CredentialAccountMapper::toModel);
	}

	@Override
	public @NotNull CredentialAccount create(@NotNull CredentialAccount credential) {
		if (credentials.get().insertIfAbsent(CredentialAccountMapper.toEntity(credential))) return credential;

		return credentials.get().find(credential.getProviderId(), credential.getProviderSubject())
				.map(CredentialAccountMapper::toModel)
				.orElseThrow(() -> new IllegalStateException("Credential " + credential.getProviderId() + ":"
						+ credential.getProviderSubject() + " was removed while it was created"));
	}

	@Override
	public void updatePassword(
			@NotNull String providerId,
			@NotNull String providerSubject,
			@NotNull String passwordHash,
			@NotNull String hashingMethod,
			long updatedAt
	) {
		credentials.get().update(providerId, providerSubject)
				.set("password_hash", passwordHash)
				.set("hashing_method", hashingMethod)
				.set("updated_at", updatedAt)
				.execute();
	}

	@Override
	public void delete(@NotNull String providerId, @NotNull String providerSubject) {
		credentials.get().delete(providerId, providerSubject);
	}

	@Override
	public void recordPasswordChange(@NotNull CredentialAccountHistory history) {
		var entity = CredentialAccountHistoryMapper.toEntity(history);
		if (entity == null)
			return;
		passwordChanges.get().insert(entity);
	}
}
