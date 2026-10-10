package me.whereareiam.identica.provider.subject;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Resolution result for provider-subject derivation.
 */
@Getter
@Builder
@SuppressWarnings("unused")
public class SubjectResolution {
	private final @NotNull String providerId;
	private final @NotNull String providerSubject;

	/**
	 * Whether the provider verified, for this connection, that the client owns the subject.
	 *
	 * <p>A subject the platform authenticated, such as the UUID of an online login, is verified. A subject
	 * derived from what the client claims, such as its username, is not, even when a later step of the
	 * provider checks it. Identica continues a pending migration only for a connection with a verified
	 * subject, so a provider that leaves this {@code false} never completes a migration by being seen.</p>
	 */
	private final boolean verified;
}
