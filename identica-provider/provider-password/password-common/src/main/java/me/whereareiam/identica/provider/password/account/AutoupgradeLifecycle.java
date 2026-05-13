package me.whereareiam.identica.provider.password.account;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.provider.password.config.PasswordSettings;
import me.whereareiam.identica.provider.password.cryptography.CryptographyService;
import me.whereareiam.identica.provider.password.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.password.event.account.password.PasswordVerifiedEvent;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

@Singleton
public class AutoupgradeLifecycle implements EventListener {
	private final Provider<PasswordSettings> settingsProvider;
	private final CryptographyService cryptographyService;
	private final PasswordAccountService accountService;

	@Inject
	public AutoupgradeLifecycle(
			Provider<PasswordSettings> settingsProvider,
			CryptographyService cryptographyService,
			PasswordAccountService accountService,
			EventManager eventManager
	) {
		this.settingsProvider = settingsProvider;
		this.cryptographyService = cryptographyService;
		this.accountService = accountService;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onPasswordVerified(@NotNull PasswordVerifiedEvent event) {
		PasswordSettings.Cryptography cryptography = resolveCryptography();
		if (cryptography == null || !cryptography.isAutoupgrade())
			return;

		String configuredId = normalize(cryptography.getAlgorithm());
		String currentId = normalize(event.getAccount().getHashingMethod());
		if (configuredId == null || currentId == null || configuredId.equals(currentId))
			return;

		PasswordCandidate candidate = cryptographyService.hash(event.getPassword());
		if (candidate == null)
			return;

		accountService.updatePassword(
				event.getAccount(),
				candidate.getPasswordHash(),
				candidate.getHashingMethod(),
				PasswordChangeReason.REHASH
		);
	}

	private @Nullable PasswordSettings.Cryptography resolveCryptography() {
		PasswordSettings settings = settingsProvider.get();
		return settings != null ? settings.getCryptography() : null;
	}

	private @Nullable String normalize(@Nullable String value) {
		if (value == null || value.isBlank()) return null;
		return value.trim().toLowerCase(Locale.ROOT);
	}
}
