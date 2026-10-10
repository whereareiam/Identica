package me.whereareiam.identica.provider.premium.resolver;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.provider.premium.config.PremiumSettings;
import me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Decides whether a joining player owns the premium account of its username by trying the configured detection
 * methods in order.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PremiumDetector {
	private final @NotNull PremiumProfileLookup profileLookup;
	private final @NotNull Provider<PremiumSettings> settingsProvider;

	/**
	 * Returns whether the configured methods include {@link PremiumDetectionMethod#CLIENT_PROFILE}.
	 *
	 * @return {@code true} when the client profile is compared
	 */
	public boolean comparesClientProfile() {
		return settingsProvider.get().getDetection().getMethods().contains(PremiumDetectionMethod.CLIENT_PROFILE);
	}

	/**
	 * Warns when the configured order leaves methods that are never tried, because {@link PremiumDetectionMethod#LOOKUP}
	 * always decides.
	 */
	public void warnAboutUnreachableMethods() {
		List<PremiumDetectionMethod> methods = settingsProvider.get().getDetection().getMethods();
		int lookup = methods.indexOf(PremiumDetectionMethod.LOOKUP);
		if (lookup < 0 || lookup == methods.size() - 1) return;

		Logger.warn(
				"Premium detection never tries %s, because LOOKUP before them always decides. Put LOOKUP last.",
				methods.subList(lookup + 1, methods.size())
		);
	}

	/**
	 * Detects whether the player owns the premium account of its username.
	 *
	 * @param username username of the player
	 * @param claimedUniqueId UUID the client claimed when it started logging in, if any
	 * @return future that completes with the outcome
	 */
	public @NotNull CompletableFuture<Detection> detect(@NotNull String username, @Nullable UUID claimedUniqueId) {
		PremiumSettings.Detection detection = settingsProvider.get().getDetection();
		return detect(username, claimedUniqueId, List.copyOf(detection.getMethods()), 0, detection);
	}

	private @NotNull CompletableFuture<Detection> detect(
			@NotNull String username,
			@Nullable UUID claimedUniqueId,
			@NotNull List<PremiumDetectionMethod> methods,
			int index,
			@NotNull PremiumSettings.Detection detection
	) {
		if (index >= methods.size()) return CompletableFuture.completedFuture(new Detection(false, null, null));

		return switch (methods.get(index)) {
			case LOOKUP -> profileLookup.findProfileId(username)
					.thenApply(profileId -> new Detection(
							profileId.isPresent(),
							profileId.orElse(null),
							PremiumDetectionMethod.LOOKUP
					));
			case CLIENT_PROFILE -> claimedUniqueId == null
					? detect(username, null, methods, index + 1, detection)
					: compareClientProfile(username, claimedUniqueId, detection.isRecheckOnMismatch());
		};
	}

	private @NotNull CompletableFuture<Detection> compareClientProfile(
			@NotNull String username,
			@NotNull UUID claimedUniqueId,
			boolean recheckOnMismatch
	) {
		return profileLookup.findProfileId(username).thenCompose(profileId -> {
			boolean mismatch = profileId.isPresent() && !profileId.get().equals(claimedUniqueId);
			if (!mismatch || !recheckOnMismatch)
				return CompletableFuture.completedFuture(compare(profileId, claimedUniqueId));

			return profileLookup.fetchProfileId(username).thenApply(fresh -> compare(fresh, claimedUniqueId));
		});
	}

	private @NotNull Detection compare(@NotNull Optional<UUID> profileId, @NotNull UUID claimedUniqueId) {
		boolean premium = profileId.isPresent() && profileId.get().equals(claimedUniqueId);
		return new Detection(premium, profileId.orElse(null), PremiumDetectionMethod.CLIENT_PROFILE);
	}

	/**
	 * Outcome of premium detection for one player.
	 *
	 * @param premium whether the player is treated as the owner of the premium account of its username
	 * @param profileId premium profile id of the username, or {@code null} when it has none or the lookup failed
	 * @param method method that decided, or {@code null} when no method decided
	 */
	public record Detection(
			boolean premium,
			@Nullable UUID profileId,
			@Nullable PremiumDetectionMethod method
	) {
	}
}
