package me.whereareiam.identica.provider.premium.resolver;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.eligibility.ProviderEligibilityResolver;
import me.whereareiam.identica.provider.premium.PremiumConstants;
import me.whereareiam.identica.provider.premium.config.PremiumSettings;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PremiumEligibilityResolver implements ProviderEligibilityResolver {
	private final @NotNull PremiumDetector detector;
	private final @NotNull Provider<PremiumSettings> settingsProvider;
	private final @NotNull ProviderLinkPersistenceService providerLinkPersistenceService;

	@Override
	public boolean isEligible(
			@NotNull ScenarioContext context,
			@NotNull InternalProvider provider,
			@NotNull JourneyMode journeyMode
	) {
		ProviderDescriptor descriptor = provider.getDescriptor();
		if (descriptor == null) return true;

		String providerId = descriptor.getId();
		if (!providerId.equalsIgnoreCase(PremiumConstants.PROVIDER_ID))
			return true;

		String username = context.getUsername();
		if (username == null || username.isBlank()) return false;

		UUID uniqueId = context.getAccountUniqueId();
		if (hasPremiumLinkByUniqueId(uniqueId)) {
			return true;
		}

		long timeoutMs = settingsProvider.get().getDetection().getLookup().getTimeout().toMillis();
		CompletableFuture<Boolean> future = detector.detect(username, context.getIdentity().getClaimedUniqueId())
				.thenApply(PremiumDetector.Detection::premium);
		if (timeoutMs > 0)
			future = future.completeOnTimeout(false, timeoutMs, TimeUnit.MILLISECONDS);

		return future.exceptionally(ignored -> false).join();
	}

	private boolean hasPremiumLinkByUniqueId(UUID uniqueId) {
		if (uniqueId == null) return false;

		List<AccountProviderLink> links = providerLinkPersistenceService.findByUniqueId(uniqueId);
		return containsPremiumLink(links);
	}

	private boolean containsPremiumLink(List<AccountProviderLink> links) {
		if (links.isEmpty()) return false;

		for (AccountProviderLink link : links) {
			if (link == null) continue;
			String providerId = link.getProviderId();
			if (providerId.equalsIgnoreCase(PremiumConstants.PROVIDER_ID))
				return true;
		}

		return false;
	}
}
