package me.whereareiam.identica.provider.password.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.model.PasswordAccountPassword;
import me.whereareiam.identica.provider.password.database.mapper.PasswordAccountMapper;
import me.whereareiam.identica.provider.password.database.mapper.PasswordAccountPasswordMapper;
import me.whereareiam.identica.provider.password.database.repository.PasswordAccountPasswordRepository;
import me.whereareiam.identica.provider.password.database.repository.PasswordAccountRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultPasswordAccountPersistenceService implements PasswordAccountPersistenceService {
	private final PasswordAccountRepository accountRepository;
	private final PasswordAccountPasswordRepository passwordRepository;

	@Override
	public @NotNull Optional<PasswordAccount> findBySubject(
			@NotNull String providerId,
			@NotNull String providerSubject
	) {
		if (providerId.isBlank() || providerSubject.isBlank())
			return Optional.empty();
		return accountRepository.findBySubject(providerId, providerSubject)
				.map(PasswordAccountMapper::toModel);
	}

	@Override
	public @NotNull PasswordAccount create(@NotNull PasswordAccount account) {
		PasswordAccount existing = findBySubject(account.getProviderId(), account.getProviderSubject())
				.orElse(null);
		if (existing != null)
			return existing;

		accountRepository.insert(
				account.getProviderId(),
				account.getProviderSubject(),
				account.getPasswordHash(),
				account.getHashingMethod(),
				account.getCreatedAt(),
				account.getUpdatedAt()
		);
		return account;
	}

	@Override
	public void updatePassword(
			@NotNull String providerId,
			@NotNull String providerSubject,
			@NotNull String passwordHash,
			@NotNull String hashingMethod,
			long updatedAt
	) {
		accountRepository.updatePassword(providerId, providerSubject, passwordHash, hashingMethod, updatedAt);
	}

	@Override
	public void delete(@NotNull String providerId, @NotNull String providerSubject) {
		accountRepository.delete(providerId, providerSubject);
	}

	@Override
	public void recordPasswordChange(@NotNull PasswordAccountPassword change) {
		var entity = PasswordAccountPasswordMapper.toEntity(change);
		if (entity == null)
			return;
		passwordRepository.insert(
				entity.getProviderId(),
				entity.getProviderSubject(),
				entity.getHashingMethod(),
				entity.getChangeReason(),
				entity.getChangedAt()
		);
	}
}
