package me.whereareiam.identica.provider.premium;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.pipeline.completion.extension.CompletionExtensionRegistry;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.premium.command.PremiumCommand;
import me.whereareiam.identica.provider.premium.completion.PremiumCompletionStep;
import me.whereareiam.identica.provider.premium.config.PremiumCommands;
import me.whereareiam.identica.provider.premium.config.provider.PremiumCommandsProvider;
import me.whereareiam.identica.provider.premium.config.provider.PremiumMessagesProvider;
import me.whereareiam.identica.provider.premium.config.provider.PremiumSettingsProvider;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import me.whereareiam.identica.provider.premium.resolver.PremiumProfileLookup;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.provider.ProviderTrait;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PremiumModuleTest {
	@Test
	void declaresOnlyAuthoritativeUsernameCapabilityAndOptionalFeatures() {
		PremiumProvider provider = new PremiumProvider();
		assertEquals(java.util.Set.of(ProviderTrait.AUTHORITATIVE_USERNAME), provider.traits());

		assertEquals(Set.of("verification", "recognition", "restriction", "restriction-join", "sentinel"), provider.supportedFeatures());
		assertTrue(provider.libraries().toLibraryRequests().isEmpty());
	}

	@Test
	void constructsProviderWiringWithFeaturesDisabled() {
		PremiumCommandsProvider commandsProvider = mock(PremiumCommandsProvider.class);
		when(commandsProvider.get()).thenReturn(new PremiumCommands());
		Injector injector = Guice.createInjector(Modules.override(new PremiumModule()).with(binder -> {
			binder.bind(FeatureRegistry.class).toInstance(mock(FeatureRegistry.class));
			binder.bind(me.whereareiam.identica.feature.verification.VerificationService.class).toInstance(mock(me.whereareiam.identica.feature.verification.VerificationService.class));
			binder.bind(PremiumSettingsProvider.class).toInstance(mock(PremiumSettingsProvider.class));
			binder.bind(PremiumMessagesProvider.class).toInstance(mock(PremiumMessagesProvider.class));
			binder.bind(PremiumCommandsProvider.class).toInstance(commandsProvider);
			binder.bind(ProviderLinkPersistenceService.class).toInstance(mock(ProviderLinkPersistenceService.class));
			binder.bind(EventManager.class).toInstance(mock(EventManager.class));
			binder.bind(ConnectionCoordinator.class).toInstance(mock(ConnectionCoordinator.class));
			binder.bind(PipelineStateStore.class).toInstance(mock(PipelineStateStore.class));
			binder.bind(SessionService.class).toInstance(mock(SessionService.class));
			binder.bind(MigrationService.class).toInstance(mock(MigrationService.class));
			binder.bind(Engine.class).toInstance(new Engine());
			binder.bind(Messages.class).toInstance(new Messages());
			binder.bind(AccountPersistenceService.class).toInstance(mock(AccountPersistenceService.class));
			binder.bind(ProviderManager.class).toInstance(mock(ProviderManager.class));
			binder.bind(ProviderAttemptStore.class).toInstance(mock(ProviderAttemptStore.class));
			binder.bind(HandshakeStore.class).toInstance(mock(HandshakeStore.class));
			binder.bind(PremiumProfileLookup.class).toInstance(mock(PremiumProfileLookup.class));
			binder.bind(PremiumProfileStore.class).toInstance(mock(PremiumProfileStore.class));
			binder.bind(CompletionExtensionRegistry.class).toInstance(mock(CompletionExtensionRegistry.class));
			binder.bind(PipelineExtensionRegistry.class).toInstance(mock(PipelineExtensionRegistry.class));
			binder.bind(CommandService.class).toInstance(mock(CommandService.class));
		}));

		Set<Object> commands = injector.getInstance(Key.get(new TypeLiteral<Set<Object>>() {}, Names.named("premiumCommandInstances")));
		assertTrue(commands.stream().anyMatch(PremiumCommand.class::isInstance));
		assertNotNull(injector.getInstance(PremiumCompletionStep.class));
		assertNotNull(injector.getInstance(PremiumProvider.class));
		assertTrue(injector.getAllBindings().keySet().stream()
				.noneMatch(key -> key.getTypeLiteral().getRawType().getName().startsWith("me.whereareiam.identica.feature.")
						&& key.getTypeLiteral().getRawType() != FeatureRegistry.class
						&& key.getTypeLiteral().getRawType() != me.whereareiam.identica.feature.verification.VerificationService.class));
	}
}
