package me.whereareiam.identica.common.provider.selection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.identica.type.provider.ProviderState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The precedence of provider claims. Nothing else decides which claim beats another.
 *
 * <p>Claims rank by origin: a pending migration, a verified account link, an entrypoint hostname, a verified subject
 * without an account, the player's own choice, a name match, then discovery. An existing account keeps its provider,
 * so a hostname never moves an account, and a hostname is a deliberate choice that beats a proof of identity without
 * an account. Claims of the same origin rank by provider priority, the higher first, then by provider id.</p>
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProviderClaimRanking {
	private static final List<ProviderClaimOrigin> PRECEDENCE = List.of(
			ProviderClaimOrigin.MIGRATION,
			ProviderClaimOrigin.ACCOUNT_LINK,
			ProviderClaimOrigin.HOSTNAME,
			ProviderClaimOrigin.VERIFIED_SUBJECT,
			ProviderClaimOrigin.PLAYER_CHOICE,
			ProviderClaimOrigin.NAME_MATCH,
			ProviderClaimOrigin.DISCOVERY
	);

	private final ProviderManager providerManager;

	/**
	 * Returns the rank of an origin, {@code 0} for the strongest.
	 */
	public int precedence(@NotNull ProviderClaimOrigin origin) {
		return PRECEDENCE.indexOf(origin);
	}

	/**
	 * Orders claims, the strongest first.
	 */
	public @NotNull Comparator<ProviderClaim> claims() {
		return Comparator.<ProviderClaim>comparingInt(claim -> precedence(claim.getOrigin()))
				.thenComparing(ProviderClaim::getProviderId, providers());
	}

	/**
	 * Orders provider ids of equal standing: the higher priority first, then by id.
	 */
	public @NotNull Comparator<String> providers() {
		return Comparator.<String>comparingInt(this::priority)
				.reversed()
				.thenComparing(String.CASE_INSENSITIVE_ORDER);
	}

	/**
	 * Returns whether the provider is enabled and so may win.
	 */
	public boolean isEnabled(@NotNull String providerId) {
		return find(providerId)
				.map(provider -> provider.getState() == ProviderState.ENABLED)
				.orElse(false);
	}

	private int priority(@NotNull String providerId) {
		return find(providerId).map(InternalProvider::getPriority).orElse(0);
	}

	private @NotNull Optional<InternalProvider> find(@Nullable String providerId) {
		List<InternalProvider> providers = providerManager.getProviders();
		if (providerId == null || providers == null) return Optional.empty();

		return providers.stream()
				.filter(provider -> provider != null && provider.getDescriptor() != null)
				.filter(provider -> provider.getDescriptor().getId().equalsIgnoreCase(providerId))
				.findFirst();
	}
}
