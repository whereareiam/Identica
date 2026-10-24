package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.option;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One way a scenario can get done: a provider, and one of its methods when it has any.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class JourneyOption {
	private final @NotNull String providerId;
	private final @Nullable ProviderMethod method;

	/**
	 * Returns the name of the option's method.
	 *
	 * @return method name, or {@code null} for a provider without methods
	 */
	public @Nullable String methodName() {
		return method != null ? method.getName() : null;
	}

	/**
	 * Returns the option's ID: the method's ID, {@code <provider>:<name>}, or the provider's ID for a provider without
	 * methods.
	 *
	 * @return option ID
	 */
	public @NotNull String id() {
		return method != null ? method.id() : providerId;
	}
}
