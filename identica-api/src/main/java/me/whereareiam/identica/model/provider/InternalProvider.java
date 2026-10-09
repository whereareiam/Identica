package me.whereareiam.identica.model.provider;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.identica.provider.IdenticaProvider;
import me.whereareiam.identica.feature.ProviderFeatureContribution;
import me.whereareiam.identica.provider.eligibility.ProviderEligibilityResolver;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import me.whereareiam.identica.provider.subject.SubjectResolver;
import me.whereareiam.identica.type.provider.ProviderState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Set;

/**
 * Runtime representation of a loaded provider and its resolved metadata.
 */
@Getter
@Setter
@Builder
public class InternalProvider {
	/**
	 * Provider JAR path on disk.
	 */
	private @Nullable Path path;

	/**
	 * Provider descriptor loaded from metadata.
	 */
	private @Nullable ProviderDescriptor descriptor;
	/**
	 * Provider implementation instance.
	 */
	private @Nullable IdenticaProvider provider;

	/**
	 * Dedicated class loader for the provider.
	 */
	private @Nullable ClassLoader classLoader;
	/**
	 * Working directory for the provider runtime.
	 */
	private @Nullable Path workingPath;

	/**
	 * Resolved provider priority for ordering.
	 */
	private int priority;
	/**
	 * Current lifecycle state.
	 */
	private @NotNull ProviderState state;

	/**
	 * Eligibility resolvers registered for this provider.
	 */
	private @Nullable Set<ProviderEligibilityResolver> eligibilityResolvers;
	/**
	 * Subject resolvers registered for this provider.
	 */
	private @Nullable Set<SubjectResolver> subjectResolvers;
	/**
	 * Migration prechecks registered for this provider.
	 */
	private @Nullable Set<ProviderMigrationPrecheck> migrationPrechecks;
	/**
	 * Feature contributions registered for this provider.
	 */
	private @Nullable Set<ProviderFeatureContribution> featureContributions;
}
