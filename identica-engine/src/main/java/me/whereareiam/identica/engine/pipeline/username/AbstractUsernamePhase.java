package me.whereareiam.identica.engine.pipeline.username;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.model.identity.username.AccountUsername;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.type.provider.ProviderTrait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractUsernamePhase {
	private final ProviderManager providerManager;
	protected final AccountUsernamePersistenceService usernamePersistenceService;

	protected @NotNull AccountUsernameSource resolveSource(@Nullable UUID uniqueId) {
		if (uniqueId == null) return AccountUsernameSource.PROVIDER;

		return usernamePersistenceService.find(uniqueId)
				.map(AccountUsername::getSource)
				.orElse(AccountUsernameSource.PROVIDER);
	}

	protected void saveSource(@Nullable UUID uniqueId, @NotNull AccountUsernameSource source) {
		if (uniqueId == null) return;

		usernamePersistenceService.setSource(uniqueId, source);
	}

	/**
	 * Tells whether an account other than the given one holds a username without regard to case, which a rename
	 * must not take from it.
	 */
	protected static boolean isHeldByAnother(
			@NotNull AccountPersistenceService accounts,
			@NotNull UUID uniqueId,
			@Nullable String username
	) {
		if (username == null || username.isBlank()) return false;

		return accounts.findByUsername(username).stream()
				.anyMatch(holder -> !holder.getUniqueId().equals(uniqueId));
	}

	protected boolean synchronize(
			@NotNull Account account,
			@NotNull AccountProviderLink link,
			@NotNull AccountProviderProfile profile
	) {
		String providerUsername = profile.getProviderUsername();
		if (providerUsername.isBlank()) return false;
		if (!link.isPrimaryLink()) return false;
		if (!hasAuthoritativeUsernameTrait(link.getProviderId())) return false;
		if (resolveSource(account.getUniqueId()) == AccountUsernameSource.MANUAL) return false;

		String candidate = providerUsername.trim();
		if (candidate.equals(account.getUsername())) return false;

		account.setUsername(candidate);
		return true;
	}

	protected boolean hasAuthoritativeUsernameTrait(@Nullable String providerId) {
		ProviderDescriptor descriptor = providerDescriptor(providerId);
		return descriptor != null && descriptor.hasTrait(ProviderTrait.AUTHORITATIVE_USERNAME);
	}

	private @Nullable ProviderDescriptor providerDescriptor(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) return null;

		return providerManager.getProviders().stream()
				.map(InternalProvider::getDescriptor)
				.filter(Objects::nonNull)
				.filter(descriptor -> descriptor.getId().equalsIgnoreCase(providerId))
				.findFirst()
				.orElse(null);
	}
}
