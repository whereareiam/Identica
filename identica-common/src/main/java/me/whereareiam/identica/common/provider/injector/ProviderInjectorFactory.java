package me.whereareiam.identica.common.provider.injector;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.provider.IdenticaProvider;
import me.whereareiam.identica.provider.ProviderPlatformExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProviderInjectorFactory {
	private final Injector injector;

	public Injector create(
			Path workingPath,
			ProviderDescriptor descriptor,
			IdenticaProvider probeProvider,
			@Nullable ProviderPlatformExtension probePlatformExtension,
			@NotNull List<Module> featureModules
	) {
		List<Module> modules = new ArrayList<>();
		modules.add(new ProviderInjectorConfiguration(workingPath, descriptor));

		List<Module> providerModules = probeProvider != null ? probeProvider.modules() : List.of();
		if (!providerModules.isEmpty())
			modules.addAll(providerModules);

		if (probeProvider != null)
			modules.addAll(probeProvider.featureModules(injector.getInstance(FeatureRegistry.class)));

		List<Module> platformModules = probePlatformExtension != null ? probePlatformExtension.modules() : List.of();
		if (!platformModules.isEmpty()) modules.addAll(platformModules);
		if (!featureModules.isEmpty()) modules.addAll(featureModules);

		return injector.createChildInjector(modules);
	}
}
