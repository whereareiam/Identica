package me.whereareiam.identica.feature.restriction.join.config.provider;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.annotation.merge.Merge;
import me.whereareiam.configura.feature.extension.api.annotation.ExtendableDocument;
import me.whereareiam.configura.merge.strategy.DeclaredObjectDefaults;
import me.whereareiam.identica.model.config.provider.Providers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider overrides at features.restriction.join for the restriction-join feature.
 */
@Getter
@Setter
@ToString
public class JoinRestrictionFeatures extends Providers.ProviderEntry.Features {
	@Merge(DeclaredObjectDefaults.class)
	@ExtendableDocument
	private @Nullable Restriction restriction;

	@Getter
	@Setter
	@ToString
	@ExtendableDocument
	public static class Restriction {
		@Merge(DeclaredObjectDefaults.class)
		@ExtendableDocument
		private @Nullable Join join;

		@Getter
		@Setter
		@ToString
		@ExtendableDocument
		public static class Join {
			private @Nullable Boolean enabled;
			private @Nullable List<String> allow;
		}
	}
}
