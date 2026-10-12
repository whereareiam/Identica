package me.whereareiam.identica.common;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.name.Names;
import me.whereareiam.identica.database.schema.SchemaInstaller;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.listener.ListenerRegistrar;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.logging.LoggingHelper;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.AccountReservationPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.pipeline.journey.registry.type.AuthenticationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.platform.adapter.PlatformForcedHostAdapter;
import me.whereareiam.identica.service.Scheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("Common configuration")
class CommonConfigurationTest {
	/** What the engine, platform, and database modules bind on a real start-up. */
	private static final List<Class<?>> BOUNDARIES = List.of(
			AccountPersistenceService.class,
			AccountReservationPersistenceService.class,
			ProviderLinkPersistenceService.class,
			ProviderProfilePersistenceService.class,
			SchemaInstaller.class,
			AuthenticationJourneyRegistry.class,
			MigrationJourneyRegistry.class,
			RegistrationJourneyRegistry.class,
			PipelineStateStore.class,
			FeatureRegistry.class,
			ListenerRegistrar.class,
			PlatformForcedHostAdapter.class,
			Scheduler.class
	);
	private static final List<String> logged = new ArrayList<>();

	@TempDir
	Path dataPath;

	@AfterEach
	void tearDown() {
		Logger.init(null);
		logged.clear();
	}

	@DisplayName("Hands Identica's Logger its backend before the eager singletons are created")
	@Test
	void loggerIsReadyForEagerSingletons() {
		Guice.createInjector(new CommonConfiguration(dataPath), new AbstractModule() {
			@Override
			protected void configure() {
				for (Class<?> boundary : BOUNDARIES) bindMock(boundary);
				bind(ClassLoader.class).annotatedWith(Names.named("providerLibraryParent"))
						.toInstance(CommonConfigurationTest.class.getClassLoader());
				bind(LoggingHelper.class).to(RecordingLoggingHelper.class);
				bind(StartsWithALogLine.class).asEagerSingleton();
			}

			private <T> void bindMock(Class<T> type) {
				bind(type).toInstance(mock(type));
			}
		});

		assertEquals(List.of("Started"), logged);
	}

	static final class StartsWithALogLine {
		StartsWithALogLine() {
			Logger.info("Started");
		}
	}

	static final class RecordingLoggingHelper implements LoggingHelper {
		@Override
		public void info(String message, Object... objects) {
			logged.add(message.formatted(objects));
		}

		@Override
		public void warn(String message, Object... objects) {
			logged.add(message.formatted(objects));
		}

		@Override
		public void severe(String message, Object... objects) {
			logged.add(message.formatted(objects));
		}

		@Override
		public void debug(String message, Object... objects) {
		}

		@Override
		public void trace(String message, Object... objects) {
		}
	}
}
