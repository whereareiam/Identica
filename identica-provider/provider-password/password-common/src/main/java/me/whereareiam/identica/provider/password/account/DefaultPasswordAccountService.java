package me.whereareiam.identica.provider.password.account;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.provider.password.PasswordConstants;
import me.whereareiam.identica.provider.password.database.PasswordAccountPersistenceService;
import me.whereareiam.identica.provider.password.event.account.AccountRegisteredEvent;
import me.whereareiam.identica.provider.password.event.account.password.PasswordChangedEvent;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.model.PasswordAccountPassword;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultPasswordAccountService implements PasswordAccountService {
	private final PasswordAccountPersistenceService persistenceService;
	private final EventManager eventManager;

	@Override
	public @NotNull Optional<PasswordAccount> find(@Nullable String providerSubject) {
		if (providerSubject == null || providerSubject.isBlank())
			return Optional.empty();
		return persistenceService.findBySubject(PasswordConstants.PROVIDER_ID, providerSubject);
	}

	@Override
	public @NotNull Optional<PasswordAccount> register(
			@NotNull String providerSubject,
			@NotNull String passwordHash,
			@NotNull String hashingMethod,
			@NotNull PasswordChangeReason reason
	) {
		if (providerSubject.isBlank())
			return Optional.empty();

		PasswordAccount existing = find(providerSubject).orElse(null);
		if (existing != null)
			return Optional.empty();

		long now = System.currentTimeMillis();
		PasswordAccount account = PasswordAccount.builder()
				.providerId(PasswordConstants.PROVIDER_ID)
				.providerSubject(providerSubject)
				.passwordHash(passwordHash)
				.hashingMethod(hashingMethod)
				.createdAt(now)
				.updatedAt(now)
				.build();

		persistenceService.create(account);
		recordChange(providerSubject, hashingMethod, reason, now);
		eventManager.call(new AccountRegisteredEvent(account, reason));
		return Optional.of(account);
	}

	@Override
	public boolean updatePassword(
			@NotNull PasswordAccount account,
			@NotNull String passwordHash,
			@NotNull String hashingMethod,
			@NotNull PasswordChangeReason reason
	) {
		long now = System.currentTimeMillis();
		persistenceService.updatePassword(
				account.getProviderId(),
				account.getProviderSubject(),
				passwordHash,
				hashingMethod,
				now
		);

		recordChange(account.getProviderSubject(), hashingMethod, reason, now);
		account.setPasswordHash(passwordHash);
		account.setHashingMethod(hashingMethod);
		account.setUpdatedAt(now);
		eventManager.call(new PasswordChangedEvent(account, reason, now));
		return true;
	}

	@Override
	public void delete(@NotNull String providerSubject) {
		if (providerSubject.isBlank()) return;
		persistenceService.delete(PasswordConstants.PROVIDER_ID, providerSubject);
	}

	private void recordChange(
			@NotNull String providerSubject,
			@NotNull String hashingMethod,
			@NotNull PasswordChangeReason reason,
			long now
	) {
		persistenceService.recordPasswordChange(PasswordAccountPassword.builder()
				.providerId(PasswordConstants.PROVIDER_ID)
				.providerSubject(providerSubject)
				.hashingMethod(hashingMethod)
				.changeReason(reason)
				.changedAt(now)
				.build());
	}
}
