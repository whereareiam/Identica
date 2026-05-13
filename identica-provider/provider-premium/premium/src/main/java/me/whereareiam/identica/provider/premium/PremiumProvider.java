package me.whereareiam.identica.provider.premium;

import com.google.inject.Inject;
import com.google.inject.Module;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.pipeline.completion.extension.CompletionExtensionRegistry;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.provider.IdenticaProvider;
import me.whereareiam.identica.provider.ProviderPlatformExtension;
import me.whereareiam.identica.provider.premium.command.CommandRegistrar;
import me.whereareiam.identica.provider.premium.completion.PremiumCompletionExtension;
import me.whereareiam.identica.provider.premium.completion.PremiumCompletionStep;
import me.whereareiam.identica.provider.premium.pipeline.PremiumPipelineExtension;
import me.whereareiam.identica.provider.premium.platform.bungeecord.PremiumBungeeCordExtension;
import me.whereareiam.identica.provider.premium.platform.velocity.PremiumVelocityExtension;
import me.whereareiam.identica.provider.premium.step.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@NoArgsConstructor
@SuppressWarnings("unused")
@AllArgsConstructor(onConstructor_ = @Inject)
public class PremiumProvider extends IdenticaProvider {
	private CommandRegistrar commandRegistrar;
	private PipelineExtensionRegistry pipelineExtensionRegistry;
	private CompletionExtensionRegistry completionExtensionRegistry;

	// Steps
	private ProfilePresenceStep profilePresenceStep;
	private OfflineCheckStep offlineCheckStep;
	private FinalizeProfileStep finalizeProfileStep;
	private PremiumMigrationCompleteStep premiumMigrationCompleteStep;
	private PremiumVerificationStep premiumVerificationStep;
	private PremiumCompletionStep premiumCompletionStep;

	@Override
	public @NotNull List<Module> modules() {
		return List.of(new PremiumModule());
	}

	@Override
	public @NotNull List<Class<? extends ProviderPlatformExtension>> platformExtensions() {
		return List.of(
				PremiumBungeeCordExtension.class,
				PremiumVelocityExtension.class
		);
	}

	@Override
	public void onEnable() {
		commandRegistrar.registerCommands();
		pipelineExtensionRegistry.register(new PremiumPipelineExtension(
				descriptor.getId(),
				profilePresenceStep,
				offlineCheckStep,
				finalizeProfileStep,
				premiumMigrationCompleteStep,
				premiumVerificationStep
		));
		completionExtensionRegistry.register(new PremiumCompletionExtension(
				descriptor.getId(),
				premiumCompletionStep
		));
		if (platformExtension != null)
			platformExtension.onEnable();
	}

	@Override
	public void onDisable() {
		if (platformExtension != null) platformExtension.onDisable();
		pipelineExtensionRegistry.unregister(PremiumPipelineExtension.extensionIdFor(descriptor.getId()));
		completionExtensionRegistry.unregister(PremiumCompletionExtension.extensionIdFor(descriptor.getId()));
	}
}
