package me.whereareiam.identica.feature.verification.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.annotation.merge.Merge;
import me.whereareiam.configura.feature.extension.api.annotation.ExtendableDocument;
import me.whereareiam.configura.merge.strategy.DeclaredObjectDefaults;
import me.whereareiam.identica.feature.verification.type.UnavailableSelectionPolicy;
import me.whereareiam.identica.model.config.provider.Providers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Verification feature provider-specific configuration extension.
 */
@Getter
@Setter
@ToString
public class VerificationProviders extends Providers.ProviderEntry.Features {
	@Merge(DeclaredObjectDefaults.class)
	@ExtendableDocument
	private @Nullable Verification verification;

	/** Provider verification policy and its permitted methods. */
	@Getter
	@Setter
	@ToString
	@ExtendableDocument
	public static class Verification {
		private @Nullable Boolean enabled;
		private @Nullable Boolean required;
		private @Nullable UnavailableSelectionPolicy unavailableSelectionPolicy;
		@Merge
		private @Nullable List<MethodEntry> methods;

		/** Method selection priority and optional overrides of the provider policy. */
		@Getter
		@Setter
		@ToString
		@ExtendableDocument
		public static class MethodEntry {
			private @NotNull String id = "";
			private @Nullable Boolean enabled;
			private @Nullable Integer priority;
			private @Nullable Boolean required;
			private @Nullable UnavailableSelectionPolicy unavailableSelectionPolicy;
		}
	}
}
