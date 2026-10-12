package me.whereareiam.identica.adapter.database.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderProfileEntity;
import me.whereareiam.identica.adapter.database.mapper.account.AccountProviderProfileMapper;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DefaultProviderProfilePersistenceService implements ProviderProfilePersistenceService {
	private final EntityAccess<AccountProviderProfileEntity> profiles;

	@Override
	public @NotNull Optional<AccountProviderProfile> findBySubject(
			@NotNull String providerId,
			@NotNull String providerSubject
	) {
		if (providerId.isBlank() || providerSubject.isBlank()) return Optional.empty();
		return profiles.find(providerId, providerSubject).map(AccountProviderProfileMapper::toModel);
	}

	@Override
	public @NotNull AccountProviderProfile upsert(@NotNull AccountProviderProfile profile) {
		if (profile.getProviderId().isBlank())
			throw new IllegalArgumentException("Provider resolver provider id is required");
		if (profile.getProviderSubject().isBlank())
			throw new IllegalArgumentException("Provider resolver provider subject is required");

		AccountProviderProfileEntity entity = AccountProviderProfileMapper.toEntity(profile);
		Optional<AccountProviderProfileEntity> existing = profiles.find(
				profile.getProviderId(),
				profile.getProviderSubject()
		);
		if (existing.isPresent()) {
			profiles.update(entity);
			return AccountProviderProfileMapper.toModel(entity);
		}

		profiles.insert(entity);

		return AccountProviderProfileMapper.toModel(entity);
	}

	@Override
	public void delete(@NotNull String providerId, @NotNull String providerSubject) {
		if (providerId.isBlank() || providerSubject.isBlank()) return;
		profiles.delete(providerId, providerSubject);
	}
}
