package me.whereareiam.identica.provider.premium.policy;

import com.google.inject.Inject;
import com.google.inject.Provider;
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
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.handshake.PremiumHandshakeAttributes;
import me.whereareiam.identica.provider.premium.resolver.PremiumDetector;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PremiumHandshakePolicy implements ProviderScopedHandshakePolicy {
	private final PremiumDetector detector;
	private final ProviderAttemptStore attemptStore;
	private final Provider<PremiumMessages> messagesProvider;

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

		UUID claimedUniqueId = request.getIdentity().getClaimedUniqueId();
		AccountProviderLink preferredLink = request.getPreferredLink();
		if (preferredLink != null) return evaluateKnown(username, ip, preferredLink, claimedUniqueId);
		if (request.getJourneyMode() == JourneyMode.INTERACTIVE) return allow();

		return detector.detect(username, claimedUniqueId).thenApply(detection -> {
			Logger.debug(
					"Premium handshake detected username=%s ip=%s premium=%s method=%s claimed=%s profile=%s",
					username,
					ip,
					detection.premium(),
					detection.method(),
					claimedUniqueId,
					detection.profileId()
			);
			if (!detection.premium()) return HandshakeDecision.allow();

			attemptStore.markAttempt(PremiumConstants.PROVIDER_ID, PremiumConstants.ATTEMPT_SCOPE_VERIFY, username, ip);

			return forceOnline();
		});
	}

	/**
	 * Handles a username whose account is known. Only an account that prefers premium is asked for a premium login,
	 * and with the client profile compared, a client that claims another profile than the linked one is refused,
	 * unless the username now belongs to the profile that client claims.
	 */
	private CompletionStage<HandshakeDecision> evaluateKnown(
			@NotNull String username,
			@Nullable String ip,
			@NotNull AccountProviderLink preferredLink,
			@Nullable UUID claimedUniqueId
	) {
		Logger.debug(
				"Premium handshake followed the preferred provider username=%s ip=%s provider=%s claimed=%s",
				username,
				ip,
				preferredLink.getProviderId(),
				claimedUniqueId
		);
		if (!isPremium(preferredLink.getProviderId())) return allow();

		String linkedProfileId = preferredLink.getProviderSubject();
		if (claimedUniqueId == null
				|| !detector.comparesClientProfile()
				|| linkedProfileId.equalsIgnoreCase(claimedUniqueId.toString()))
			return CompletableFuture.completedFuture(forceOnline());

		return detector.detect(username, claimedUniqueId).thenApply(detection -> {
			Logger.debug(
					"Premium handshake checked a client of a linked username username=%s ip=%s claimed=%s linked=%s owner=%s",
					username,
					ip,
					claimedUniqueId,
					linkedProfileId,
					detection.premium()
			);

			return detection.premium()
					? forceOnline()
					: HandshakeDecision.deny(String.join("\n", messagesProvider.get().getVerification().getOwnedUsername()));
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
