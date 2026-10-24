package me.whereareiam.identica.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Option shown where the player picks how a scenario gets done: a provider, and one of its methods when it offers
 * several. The player answers with {@link #getOptionId()}.
 */
@Getter
@ToString
@Builder(toBuilder = true)
@AllArgsConstructor
public class EnrollmentEntry {
	private final @NotNull String providerId;
	private final @NotNull String providerName;
	private final @NotNull List<String> description;
	/**
	 * Name of the provider method the option runs, or {@code null} when the provider runs without methods.
	 */
	private final @Nullable String method;
	/**
	 * Text naming the method, or {@code null} to name the option by its provider only.
	 */
	private final @Nullable String methodName;

	/**
	 * Returns the ID the player answers with: the provider's ID, followed by {@code :<method>} for a provider that
	 * offers several methods.
	 *
	 * @return option ID
	 */
	public @NotNull String getOptionId() {
		return method == null || method.isBlank() ? providerId : providerId + ":" + method;
	}
}
