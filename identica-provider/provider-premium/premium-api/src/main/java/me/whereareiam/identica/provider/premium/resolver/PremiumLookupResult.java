package me.whereareiam.identica.provider.premium.resolver;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

/**
 * Answer of a premium profile lookup for one username, as the lookup caches it.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PremiumLookupResult {
	/**
	 * Premium profile id of the username, or {@code null} when the username has no premium profile.
	 */
	private @Nullable String profileId;
}
