package me.whereareiam.identica.common.provider.capability;

import com.google.inject.*;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import me.whereareiam.identica.common.provider.runtime.classloader.SharedCapabilityClassLoaderFactory;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.capability.ProviderCapabilityDeclaration;
import me.whereareiam.identica.model.provider.capability.ProviderCapabilityInstallation;
import me.whereareiam.identica.provider.capability.ProviderCapabilityCoordinator;
import me.whereareiam.identica.provider.capability.ProviderCapabilityRegistry;
import me.whereareiam.identica.provider.capability.bootstrap.ProviderCapabilityBootstrap;
import me.whereareiam.identica.provider.capability.bootstrap.ProviderCapabilityGlobalInstallContext;
import me.whereareiam.identica.provider.capability.bootstrap.ProviderCapabilityInitializationContext;
import me.whereareiam.identica.provider.capability.bootstrap.ProviderCapabilityLocalInstallContext;
import me.whereareiam.identica.provider.capability.contribution.ProviderCapabilityContribution;
import me.whereareiam.identica.type.provider.ProviderState;
import me.whereareiam.identica.type.provider.capability.ProviderCapability;
import me.whereareiam.identica.type.provider.capability.ProviderCapabilityScope;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DefaultProviderCapabilityCoordinatorTest {
	private static final ProviderCapability SAMPLE_CAPABILITY = ProviderCapability.of("sample");
	private static final AtomicInteger GLOBAL_INITIALIZE_COUNT = new AtomicInteger();

	@Test
	void installsGlobalRuntimeOnceAndResolvesLocalModules(@TempDir Path tempDir) {
		GLOBAL_INITIALIZE_COUNT.set(0);
		Injector injector = Guice.createInjector(new CapabilityTestModule(tempDir));
		ProviderCapabilityCoordinator coordinator = injector.getInstance(ProviderCapabilityCoordinator.class);
		ProviderCapabilityRegistry registry = injector.getInstance(ProviderCapabilityRegistry.class);

		InternalProvider provider = provider("provider-a", tempDir);
		List<ProviderCapabilityBootstrap> bootstraps = coordinator.resolveBootstraps(
				provider.getDescriptor(),
				List.of(SampleCapabilityBootstrap.INSTANCE)
		);
		assertEquals(List.of(SAMPLE_CAPABILITY.getId()), provider.getDescriptor().getDeclaredCapabilityIds());
		assertTrue(provider.getDescriptor().hasCapability(SAMPLE_CAPABILITY));

		coordinator.installGlobalCapabilities(provider, bootstraps);

		assertNotNull(registry.findInstallation(SAMPLE_CAPABILITY));
		assertEquals(1, GLOBAL_INITIALIZE_COUNT.get());

		List<Module> localModules = coordinator.resolveLocalModules(provider, bootstraps);
		Injector providerInjector = injector.createChildInjector(localModules);
		ProviderCapabilityInstallation installation = registry.findInstallation(SAMPLE_CAPABILITY);
		assertNotNull(installation);
		assertNotNull(installation.getGlobalInjector());
		assertSame(
				installation.getGlobalInjector().getInstance(SampleGlobalService.class),
				providerInjector.getInstance(SampleGlobalService.class)
		);
		assertEquals("provider-a", providerInjector.getInstance(SampleLocalService.class).providerId());

		InternalProvider nextProvider = provider("provider-b", tempDir);
		coordinator.installGlobalCapabilities(nextProvider, bootstraps);
		assertEquals(1, registry.installations().size());
		assertEquals(1, GLOBAL_INITIALIZE_COUNT.get());
	}

	@Test
	void rejectsMissingRequiredContribution() {
		Injector injector = Guice.createInjector(new CapabilityTestModule(Path.of("build", "tmp", "capability-test")));
		ProviderCapabilityCoordinator coordinator = injector.getInstance(ProviderCapabilityCoordinator.class);
		ProviderDescriptor descriptor = descriptor("provider-a");
		List<ProviderCapabilityBootstrap> bootstraps = coordinator.resolveBootstraps(
				descriptor,
				List.of(RequiredContributionCapabilityBootstrap.INSTANCE)
		);

		IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
				coordinator.validateCapabilityContributions(descriptor, bootstraps, Set.of()));

		assertTrue(exception.getMessage().contains("sample"));
	}

	@Test
	void resolvesContributionsFromProviderInjector() {
		Injector injector = Guice.createInjector(
				new CapabilityTestModule(Path.of("build", "tmp", "capability-test")),
				new ContributionModule()
		);
		ProviderCapabilityCoordinator coordinator = injector.getInstance(ProviderCapabilityCoordinator.class);

		Set<ProviderCapabilityContribution> contributions = coordinator.resolveCapabilityContributions(injector);

		assertEquals(1, contributions.size());
		assertEquals(SAMPLE_CAPABILITY, contributions.iterator().next().capability());
	}

	private static @NotNull InternalProvider provider(@NotNull String id, @NotNull Path workingPath) {
		return InternalProvider.builder()
				.descriptor(descriptor(id))
				.workingPath(workingPath)
				.state(ProviderState.DISCOVERED)
				.build();
	}

	private static @NotNull ProviderDescriptor descriptor(@NotNull String id) {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId(id);
		descriptor.setName(id);
		descriptor.setVersion("1.0.0");
		descriptor.setMain("ignored.Main");
		descriptor.setSupportedPlatforms(List.of("ANY"));
		return descriptor;
	}

	private static final class CapabilityTestModule extends AbstractModule {
		private final Path rootPath;

		private CapabilityTestModule(@NotNull Path rootPath) {
			this.rootPath = rootPath;
		}

		@Override
		protected void configure() {
			bind(ProviderCapabilityCoordinator.class).to(DefaultProviderCapabilityCoordinator.class).asEagerSingleton();
			bind(ProviderCapabilityRegistry.class).to(DefaultProviderCapabilityRegistry.class).asEagerSingleton();
			bind(SharedCapabilityClassLoaderFactory.class).toInstance(new SharedCapabilityClassLoaderFactory());
		}

		@Provides
		@Named("capabilitiesPath")
		Path provideCapabilitiesPath() {
			return rootPath.resolve("providers").resolve("capabilities");
		}
	}

	private static final class ContributionModule extends AbstractModule {
		@Override
		protected void configure() {
			Multibinder.newSetBinder(binder(), ProviderCapabilityContribution.class)
					.addBinding()
					.to(SampleContribution.class);
		}
	}

	public interface SampleGlobalService {
		@NotNull String value();
	}

	public interface SampleLocalService {
		@NotNull String providerId();
	}

	private static final class SampleCapabilityBootstrap implements ProviderCapabilityBootstrap {
		private static final SampleCapabilityBootstrap INSTANCE = new SampleCapabilityBootstrap();

		@Override
		public @NotNull ProviderCapabilityDeclaration declaration() {
			return ProviderCapabilityDeclaration.builder()
					.capability(SAMPLE_CAPABILITY)
					.scopes(Set.of(ProviderCapabilityScope.GLOBAL, ProviderCapabilityScope.LOCAL))
					.build();
		}

		@Override
		public @NotNull List<Module> globalModules(@NotNull ProviderCapabilityGlobalInstallContext context) {
			return List.of(new SampleGlobalModule());
		}

		@Override
		public void initialize(@NotNull ProviderCapabilityInitializationContext context) {
			Injector globalInjector = context.getGlobalInjector();
			assertNotNull(globalInjector);

			GLOBAL_INITIALIZE_COUNT.incrementAndGet();
		}

		@Override
		public @NotNull List<Module> localModules(@NotNull ProviderCapabilityLocalInstallContext context) {
			return List.of(new SampleLocalModule(context.getProviderId()));
		}
	}

	private static final class RequiredContributionCapabilityBootstrap implements ProviderCapabilityBootstrap {
		private static final RequiredContributionCapabilityBootstrap INSTANCE = new RequiredContributionCapabilityBootstrap();

		@Override
		public @NotNull ProviderCapabilityDeclaration declaration() {
			return ProviderCapabilityDeclaration.builder()
					.capability(SAMPLE_CAPABILITY)
					.requiresContribution(true)
					.build();
		}
	}

	private static final class SampleGlobalModule extends AbstractModule {
		@Override
		protected void configure() {
			bind(SampleGlobalService.class).toInstance(() -> "global");
		}
	}

	private static final class SampleLocalModule extends AbstractModule {
		private final String providerId;

		private SampleLocalModule(String providerId) {
			this.providerId = providerId;
		}

		@Override
		protected void configure() {
			bind(SampleLocalService.class).toInstance(() -> providerId);
		}
	}

	@Singleton
	private static final class SampleContribution implements ProviderCapabilityContribution {
		@Override
		public @NotNull ProviderCapability capability() {
			return SAMPLE_CAPABILITY;
		}
	}
}
