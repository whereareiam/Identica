package me.whereareiam.identica.testing.environment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Official providers with the priority and entrypoint host the test network gives them. Premium outranks
 * Credential, as the installation guide recommends for mixed setups.
 */
@Getter
@RequiredArgsConstructor
public enum Provider {
	PREMIUM("premium", 200, "premium.identica.test"),
	CREDENTIAL("credential", 100, "credential.identica.test");

	private final String id;
	private final int priority;
	private final String entrypoint;
}
