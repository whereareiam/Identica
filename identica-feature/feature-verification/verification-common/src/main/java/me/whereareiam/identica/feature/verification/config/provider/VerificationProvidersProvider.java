package me.whereareiam.identica.feature.verification.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.document.DocumentTypeContext;
import me.whereareiam.configura.feature.extension.ExtensionFeature;
import me.whereareiam.configura.feature.extension.api.ConfigDocumentRule;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.feature.verification.model.config.VerificationProviders;
import me.whereareiam.identica.model.config.provider.Providers;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

@Singleton
public class VerificationProvidersProvider extends ConfigProvider<Providers> {
	@Inject
	public VerificationProvidersProvider(
			@Named("providersPath") Path providersPath,
			Registry<Reloadable> reloadables
	) {
		super(providersPath, "providers", Providers.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withFeature(ExtensionFeature.rule(ConfigDocumentRule.when(
				Providers.ProviderEntry.Features.class,
				VerificationProviders.class,
				VerificationProvidersProvider::isVerificationFeaturesNode
		)));
	}

	private static boolean isVerificationFeaturesNode(@NotNull DocumentTypeContext context) {
		return "features".equals(context.getFieldName())
				&& context.getCurrentNode() != null
				&& context.getCurrentNode().has("verification");
	}
}
