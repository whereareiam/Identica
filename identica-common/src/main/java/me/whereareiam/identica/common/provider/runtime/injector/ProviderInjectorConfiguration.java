package me.whereareiam.identica.common.provider.runtime.injector;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.provider.ProviderDescriptor;

import java.nio.file.Path;

@RequiredArgsConstructor
public class ProviderInjectorConfiguration extends AbstractModule {
	private final Path workingPath;
	private final ProviderDescriptor descriptor;

	@Override
	protected void configure() {
		bind(ProviderDescriptor.class).toInstance(descriptor);
	}

	@Provides
	@Singleton
	@Named("workingPath")
	Path provideWorkingPath() {
		return workingPath;
	}
}
