package me.whereareiam.identica.provider.premium.resolver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.provider.premium.PremiumConstants;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileSnapshot;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.provider.subject.SubjectResolver;
import me.whereareiam.identica.util.UniqueIdGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PremiumSubjectResolver implements SubjectResolver {
	private final PremiumProfileStore profileStore;

	@Override
	public boolean supports(@NotNull SubjectResolveContext context) {
		return resolve(context) != null;
	}

	/**
	 * Resolves the premium profile of the connection. The UUID of an online login is verified by the session
	 * service; a profile only known from a lookup of the username is not.
	 */
	@Override
	public @Nullable SubjectResolution resolve(@NotNull SubjectResolveContext context) {
		String username = context.getUsername();
		if (username == null || username.isBlank()) return null;

		UUID offlineUuid = UniqueIdGenerator.offlinePlayerUniqueId(username);
		UUID observedUniqueId = context.getIdentity().getObservedUniqueId();
		if (observedUniqueId != null) {
			if (observedUniqueId.equals(offlineUuid)) return null;
			return resolution(observedUniqueId.toString(), true);
		}

		PremiumProfileSnapshot snapshot = profileStore.find(username);
		String profileUniqueId = snapshot != null ? snapshot.getProfileId() : null;
		if (profileUniqueId == null || profileUniqueId.isBlank()) return null;

		if (offlineUuid != null && profileUniqueId.equalsIgnoreCase(offlineUuid.toString())) return null;

		return resolution(profileUniqueId.trim(), false);
	}

	@Override
	public int priority() {
		return 50;
	}

	private @NotNull SubjectResolution resolution(@NotNull String subject, boolean verified) {
		return SubjectResolution.builder()
				.providerId(PremiumConstants.PROVIDER_ID)
				.providerSubject(subject)
				.verified(verified)
				.build();
	}
}
