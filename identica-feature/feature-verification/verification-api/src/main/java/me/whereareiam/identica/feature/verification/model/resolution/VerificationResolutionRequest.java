package me.whereareiam.identica.feature.verification.model.resolution;

import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Request used to resolve the provider verification requirement. A request with a {@code code} first submits the
 * code a player typed to the active challenge, then resolves the requirement.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class VerificationResolutionRequest {
	private @NotNull UUID uniqueId;
	private @NotNull String providerId;
	private @Nullable String purpose;
	/**
	 * Code the player typed for the active challenge, or {@code null} when the request only resolves the requirement.
	 */
	private @Nullable String code;
}
