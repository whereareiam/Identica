package me.whereareiam.identica.provider.credential;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.credential.command.CredentialCommand;
import me.whereareiam.identica.provider.credential.completion.CredentialCompletionStep;
import me.whereareiam.identica.provider.credential.config.provider.CredentialCommandsProvider;
import me.whereareiam.identica.provider.credential.config.provider.CredentialMessagesProvider;
import me.whereareiam.identica.provider.credential.config.provider.CredentialSettingsProvider;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.database.CredentialAccountPersistenceService;
import me.whereareiam.identica.provider.credential.pipeline.CredentialPipelineExtension;
import me.whereareiam.identica.service.MigrationService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CommonConfigurationTest {
	@Test
	void constructsProviderWiringWithFeaturesDisabled() {
		Injector injector = Guice.createInjector(Modules.override(new CommonConfiguration()).with(binder -> {
			binder.bind(FeatureRegistry.class).toInstance(mock(FeatureRegistry.class));
			binder.bind(me.whereareiam.identica.feature.verification.VerificationService.class).toInstance(mock(me.whereareiam.identica.feature.verification.VerificationService.class));
			binder.bind(CredentialSettingsProvider.class).toInstance(mock(CredentialSettingsProvider.class));
			binder.bind(CredentialMessagesProvider.class).toInstance(mock(CredentialMessagesProvider.class));
			binder.bind(CredentialCommandsProvider.class).toInstance(mock(CredentialCommandsProvider.class));
			binder.bind(ProviderLinkPersistenceService.class).toInstance(mock(ProviderLinkPersistenceService.class));
			binder.bind(EventManager.class).toInstance(mock(EventManager.class));
			binder.bind(ConnectionCoordinator.class).toInstance(mock(ConnectionCoordinator.class));
			binder.bind(PipelineStateStore.class).toInstance(mock(PipelineStateStore.class));
			binder.bind(SessionService.class).toInstance(mock(SessionService.class));
			binder.bind(MigrationService.class).toInstance(mock(MigrationService.class));
			binder.bind(Engine.class).toInstance(new Engine());
			binder.bind(Messages.class).toInstance(new Messages());
			binder.bind(CryptographyService.class).toInstance(mock(CryptographyService.class));
			binder.bind(CredentialAccountPersistenceService.class).toInstance(mock(CredentialAccountPersistenceService.class));
		}));

		Set<Object> commands = injector.getInstance(Key.get(new TypeLiteral<Set<Object>>() {}, Names.named("credentialCommandInstances")));
		assertTrue(commands.stream().anyMatch(CredentialCommand.class::isInstance));
		assertNotNull(injector.getInstance(CredentialCompletionStep.class));
		assertNotNull(injector.getInstance(CredentialPipelineExtension.class));
		assertTrue(injector.getAllBindings().keySet().stream()
				.noneMatch(key -> key.getTypeLiteral().getRawType().getName().startsWith("me.whereareiam.identica.feature.")
						&& key.getTypeLiteral().getRawType() != FeatureRegistry.class
						&& key.getTypeLiteral().getRawType() != me.whereareiam.identica.feature.verification.VerificationService.class));
	}
}
