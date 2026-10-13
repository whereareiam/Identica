package me.whereareiam.identica.model.provider.selection;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One reason for a provider to handle a connection.
 *
 * <p>A claim names a provider and its {@link ProviderClaimOrigin origin}. A claim of
 * {@link ProviderClaimOrigin#VERIFIED_SUBJECT} also carries its proof: the subject the provider verified for this
 * connection. Proof belongs to the connection it was given for and never carries over to another one.</p>
 *
 * <pre>{@code
 * ProviderClaim hostname = ProviderClaim.of("alpha", ProviderClaimOrigin.HOSTNAME);
 * ProviderClaim proven = ProviderClaim.verified("alpha", "0b5d...");
 * }</pre>
 */
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProviderClaim {
	private @NotNull String providerId;
	private @NotNull ProviderClaimOrigin origin;
	private @Nullable String verifiedSubject;

	/**
	 * Creates a claim without proof.
	 *
	 * @param providerId id of the claimed provider
	 * @param origin why the provider claims the connection
	 * @return claim
	 * @throws IllegalArgumentException when the provider id is blank, or the origin needs proof
	 */
	public static @NotNull ProviderClaim of(@NotNull String providerId, @NotNull ProviderClaimOrigin origin) {
		if (origin == ProviderClaimOrigin.VERIFIED_SUBJECT)
			throw new IllegalArgumentException("A verified subject claim needs its subject");

		return new ProviderClaim(requireId(providerId), origin, null);
	}

	/**
	 * Creates a {@link ProviderClaimOrigin#VERIFIED_SUBJECT} claim with its proof.
	 *
	 * @param providerId id of the provider that verified the subject
	 * @param subject subject the provider verified for this connection
	 * @return claim
	 * @throws IllegalArgumentException when the provider id or the subject is blank
	 */
	public static @NotNull ProviderClaim verified(@NotNull String providerId, @NotNull String subject) {
		if (subject.isBlank()) throw new IllegalArgumentException("A verified subject must not be blank");

		return new ProviderClaim(requireId(providerId), ProviderClaimOrigin.VERIFIED_SUBJECT, subject);
	}

	/**
	 * Returns whether this claim names the provider, ignoring case.
	 *
	 * @param providerId provider id to compare with
	 * @return {@code true} when this claim names the provider
	 */
	public boolean names(@Nullable String providerId) {
		return providerId != null && this.providerId.equalsIgnoreCase(providerId.trim());
	}

	/**
	 * Returns whether this claim carries proof that the client owns a subject of the provider.
	 *
	 * @return {@code true} for a claim with a verified subject
	 */
	public boolean hasProof() {
		return verifiedSubject != null;
	}

	private static @NotNull String requireId(@NotNull String providerId) {
		if (providerId.isBlank()) throw new IllegalArgumentException("A claimed provider id must not be blank");

		return providerId.trim();
	}
}
