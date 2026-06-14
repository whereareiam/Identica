package me.whereareiam.identica.feature.verification;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import me.whereareiam.identica.feature.verification.command.VerificationCommandRegistrar;
import me.whereareiam.identica.feature.verification.command.executor.VerificationCommand;
import me.whereareiam.identica.feature.verification.command.executor.VerificationConfirmCommand;
import me.whereareiam.identica.feature.verification.command.executor.VerificationEnrollmentCommand;
import me.whereareiam.identica.feature.verification.command.executor.VerificationSelectionCommand;
import me.whereareiam.identica.feature.verification.command.executor.admin.VerificationResetCommand;
import me.whereareiam.identica.feature.verification.config.VerificationCommands;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.config.provider.VerificationCommandsProvider;
import me.whereareiam.identica.feature.verification.config.provider.VerificationMessagesProvider;
import me.whereareiam.identica.feature.verification.config.provider.VerificationSettingsProvider;
import me.whereareiam.identica.feature.verification.enrollment.VerificationEnrollmentStore;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.type.totp.TotpVerificationMethod;

public class VerificationCommonConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(VerificationCommands.class).toProvider(VerificationCommandsProvider.class);
		bind(VerificationMessages.class).toProvider(VerificationMessagesProvider.class);
		bind(VerificationSettings.class).toProvider(VerificationSettingsProvider.class);
		bind(VerificationEnrollmentStore.class).asEagerSingleton();
		bind(VerificationChallengeStore.class).asEagerSingleton();
		bind(VerificationService.class).to(DefaultVerificationService.class).asEagerSingleton();
		bind(VerificationRegistry.class).to(DefaultVerificationRegistry.class).asEagerSingleton();
		bind(VerificationDisconnectLifecycle.class).asEagerSingleton();
		Multibinder.newSetBinder(binder(), VerificationMethod.class)
				.addBinding()
				.to(TotpVerificationMethod.class);

		Multibinder<Object> commands = Multibinder.newSetBinder(
				binder(),
				new TypeLiteral<>() {},
				Names.named("verificationCommandInstances")
		);
		commands.addBinding().to(VerificationCommand.class);
		commands.addBinding().to(VerificationEnrollmentCommand.class);
		commands.addBinding().to(VerificationConfirmCommand.class);
		commands.addBinding().to(VerificationSelectionCommand.class);
		commands.addBinding().to(VerificationResetCommand.class);

		bind(VerificationCommandRegistrar.class).asEagerSingleton();
	}

	@Inject
	void registerVerificationCommands(VerificationCommandRegistrar registrar) {
		registrar.registerCommands();
	}
}
