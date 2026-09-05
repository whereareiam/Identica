package me.whereareiam.identica.trait.authoritative.username.pipeline.phase.base;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.model.account.AccountUsernameState;
import me.whereareiam.identica.trait.authoritative.username.type.AccountUsernameSource;
import me.whereareiam.identica.type.provider.ProviderTrait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractUsernamePhase {
	private final ProviderManager providerManager;
	private final AccountUsernameStatePersistenceService statePersistenceService;

	protected @NotNull AccountUsernameSource resolveSource(@Nullable UUID uniqueId) {
		if (uniqueId == null) return AccountUsernameSource.PROVIDER;

		return statePersistenceService.find(uniqueId)
				.map(AccountUsernameState::getSource)
				.orElse(AccountUsernameSource.PROVIDER);
	}

	protected void saveSource(@Nullable UUID uniqueId, @NotNull AccountUsernameSource source) {
		if (uniqueId == null) return;

		statePersistenceService.save(uniqueId, source);
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
