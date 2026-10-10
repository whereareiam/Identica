package me.whereareiam.identica.provider.premium.policy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.handshake.policy.ProviderScopedHandshakePolicy;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.auth.handshake.HandshakeRequest;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.premium.PremiumConstants;
import me.whereareiam.identica.provider.premium.handshake.PremiumHandshakeAttributes;
import me.whereareiam.identica.provider.premium.resolver.PremiumProfileLookup;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PremiumHandshakePolicy implements ProviderScopedHandshakePolicy {
	private final PremiumProfileLookup profileLookup;
	private final ProviderAttemptStore attemptStore;

	@Override
	public @NotNull String providerId() {
		return PremiumConstants.PROVIDER_ID;
	}

	@Override
	public CompletionStage<HandshakeDecision> evaluate(HandshakeRequest request) {
		String username = request != null ? request.getIdentity().getUsername() : null;
		String ip = request != null ? request.getIdentity().getIp() : null;
		if (username == null || username.isBlank()) return allow();

		if (attemptStore.hasAttempt(PremiumConstants.PROVIDER_ID, PremiumConstants.ATTEMPT_SCOPE_VERIFY, username, ip)) {
			Logger.debug("Premium handshake allowed existing verify attempt username=%s ip=%s", username, ip);
			return allow();
		}

		ProviderContext provider = request.getProvider();
		if (provider != null && isPremium(provider.getProviderId())) {
			Logger.debug("Premium handshake forcing online for provider context username=%s ip=%s", username, ip);
			return CompletableFuture.completedFuture(forceOnline());
		}

		AccountProviderLink preferredLink = request.getPreferredLink();
		if (preferredLink != null) {
			Logger.debug(
					"Premium handshake followed the preferred provider username=%s ip=%s provider=%s",
					username,
					ip,
					preferredLink.getProviderId()
			);
			return isPremium(preferredLink.getProviderId()) ? CompletableFuture.completedFuture(forceOnline()) : allow();
		}

		if (request.getJourneyMode() == JourneyMode.INTERACTIVE) return allow();

		return profileLookup.hasPremiumProfile(username).thenApply(hasProfile -> {
			if (!hasProfile) return HandshakeDecision.allow();

			attemptStore.markAttempt(PremiumConstants.PROVIDER_ID, PremiumConstants.ATTEMPT_SCOPE_VERIFY, username, ip);
			Logger.debug("Premium handshake marked verify attempt after profile lookup username=%s ip=%s", username, ip);

			return forceOnline();
		});
	}

	private static boolean isPremium(@Nullable String providerId) {
		return PremiumConstants.PROVIDER_ID.equalsIgnoreCase(providerId);
	}

	private static @NotNull HandshakeDecision forceOnline() {
		return HandshakeDecision.allow().withAttribute(PremiumHandshakeAttributes.FORCE_ONLINE, true);
	}

	private static @NotNull CompletionStage<HandshakeDecision> allow() {
		return CompletableFuture.completedFuture(HandshakeDecision.allow());
	}
}
