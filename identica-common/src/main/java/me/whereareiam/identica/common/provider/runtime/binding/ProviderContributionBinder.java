package me.whereareiam.identica.common.provider.runtime.binding;

import me.whereareiam.identica.common.provider.runtime.binding.registration.Registration;
import org.jetbrains.annotations.NotNull;

public interface ProviderContributionBinder<T> {
	@NotNull Class<T> contributionType();

	@NotNull Registration bind(@NotNull T contribution);
}
