package me.whereareiam.identica.feature.recognition;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import org.jetbrains.annotations.Nullable;

/**
 * Evaluates whether a reconnecting player should be recognized strongly enough
 * to skip provider-specific authentication steps.
 */
public interface SessionRecognitionService {
	/**
	 * Returns whether the current connection matches the stored recognition
	 * snapshot for the same provider subject.
	 *
	 * @param providerId provider id
	 * @param providerSubject provider subject
	 * @param providerUsername current provider username
	 * @param ip current IP address
	 * @param origin current virtual host information
	 * @return {@code true} when recognition should apply
	 */
	boolean matches(
			@Nullable String providerId,
			@Nullable String providerSubject,
			@Nullable String providerUsername,
			@Nullable String ip,
			@Nullable ConnectionIdentity.Origin origin
	);
}
