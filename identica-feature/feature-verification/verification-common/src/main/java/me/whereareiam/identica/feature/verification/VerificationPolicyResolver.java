package me.whereareiam.identica.feature.verification;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.verification.config.provider.VerificationProvidersProvider;
import me.whereareiam.identica.feature.verification.model.config.VerificationProviders;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.type.UnavailableSelectionPolicy;
import me.whereareiam.identica.model.config.provider.Providers;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VerificationPolicyResolver {
	private final VerificationProvidersProvider providersProvider;
	private final Provider<VerificationSettings> settingsProvider;

	public @Nullable ResolvedProviderPolicy resolveProviderPolicy(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) return null;
		var defaults = settingsProvider.get().getDefaults();
		var override = verification(findEntry(providerId));
		return new ResolvedProviderPolicy(
				value(override != null ? override.getEnabled() : null, defaults.getEnabled()),
				value(override != null ? override.getRequired() : null, defaults.getRequired()),
				policy(override != null ? override.getUnavailableSelectionPolicy() : null, defaults.getUnavailableSelectionPolicy())
		);
	}

	public boolean hasConfiguredProvider(@Nullable String providerId) {
		return findEntry(providerId) != null;
	}

	public @Nullable ResolvedMethodPolicy resolveMethodPolicy(@Nullable String providerId, @Nullable String methodId) {
		ResolvedProviderPolicy provider = resolveProviderPolicy(providerId);
		if (provider == null || methodId == null || methodId.isBlank()) return null;
		var defaults = settingsProvider.get().getDefaults();
		var override = verification(findEntry(providerId));
		List<VerificationProviders.Verification.MethodEntry> methods = override != null && override.getMethods() != null
				? override.getMethods() : defaults.getMethods();
		var method = findMethod(methods, methodId);
		if (method == null) return null;
		var defaultMethod = findMethod(defaults.getMethods(), methodId);
		Boolean enabled = method.getEnabled() != null ? method.getEnabled()
				: defaultMethod != null ? defaultMethod.getEnabled() : true;
		Boolean required = method.getRequired() != null ? method.getRequired()
				: defaultMethod != null && defaultMethod.getRequired() != null ? defaultMethod.getRequired() : provider.required();
		return new ResolvedMethodPolicy(Boolean.TRUE.equals(enabled), required,
				policy(method.getUnavailableSelectionPolicy(),
						defaultMethod != null && defaultMethod.getUnavailableSelectionPolicy() != null
								? defaultMethod.getUnavailableSelectionPolicy() : provider.unavailableSelectionPolicy()));
	}

	private @Nullable VerificationProviders.Verification.MethodEntry findMethod(
			@Nullable List<VerificationProviders.Verification.MethodEntry> methods,
			String id
	) {
		if (methods == null) return null;
		return methods.stream().filter(method -> method.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
	}

	private @Nullable Providers.ProviderEntry findEntry(@Nullable String id) {
		if (id == null || id.isBlank()) return null;
		return providersProvider.get().getProviders().stream()
				.filter(entry -> entry.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
	}

	private @Nullable VerificationProviders.Verification verification(@Nullable Providers.ProviderEntry entry) {
		return entry != null && entry.getFeatures() instanceof VerificationProviders features ? features.getVerification() : null;
	}

	private boolean value(@Nullable Boolean override, @Nullable Boolean fallback) {
		return Boolean.TRUE.equals(override != null ? override : fallback);
	}

	private UnavailableSelectionPolicy policy(@Nullable UnavailableSelectionPolicy override, @Nullable UnavailableSelectionPolicy fallback) {
		return override != null ? override : fallback != null ? fallback : UnavailableSelectionPolicy.KEEP_LOCKED;
	}

	public record ResolvedProviderPolicy(boolean enabled, boolean required, UnavailableSelectionPolicy unavailableSelectionPolicy) {
	}

	public record ResolvedMethodPolicy(boolean enabled, boolean required, UnavailableSelectionPolicy unavailableSelectionPolicy) {
	}
}
