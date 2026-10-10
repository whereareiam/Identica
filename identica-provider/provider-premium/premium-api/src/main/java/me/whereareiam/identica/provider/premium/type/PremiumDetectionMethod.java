package me.whereareiam.identica.provider.premium.type;

/**
 * Way of telling whether a joining player owns the premium account of its username. Methods are tried in their
 * configured order; a method either decides or leaves the player to the next one.
 */
public enum PremiumDetectionMethod {
	/**
	 * Compares the profile id the client sends when it starts logging in with the premium profile of its username.
	 * A match means premium, a different profile means not premium. Clients older than 1.19.1 send no profile id and
	 * are left to the next method.
	 */
	CLIENT_PROFILE,
	/**
	 * Treats every username that has a premium profile as premium. It always decides.
	 */
	LOOKUP
}
