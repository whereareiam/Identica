package me.whereareiam.identica.provider;

import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ResolvedEntrypoint;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Runtime provider operations used during authentication and resolver resolution.
 */
public interface ProviderOperations {
	/**
	 * Resolves the entrypoint hostname to a provider id.
	 *
	 * @param host entrypoint host
	 * @param port entrypoint port or {@code -1} when unknown
	 * @return resolved entrypoint or {@code null} when not found
	 */
	@Nullable ResolvedEntrypoint resolveEntrypoint(@Nullable String host, int port);

	/**
	 * Returns the first configured entrypoint for the provider.
	 *
	 * @param providerId provider id to look up
	 * @return entrypoint string or {@code null} when missing
	 */
	@Nullable String displayEntrypoint(@Nullable String providerId);

	/**
	 * Returns the configured or resolved display name for the provider.
	 *
	 * @param providerId provider id to look up
	 * @return display name or the raw provider id when no better label exists
	 */
	@Nullable String displayProviderName(@Nullable String providerId);

	/**
	 * Checks whether the provider has entrypoints configured.
	 *
	 * @param providerId provider id to look up
	 * @return {@code true} if entrypoints exist
	 */
	boolean hasEntrypoints(@Nullable String providerId);

	/**
	 * Returns eligible providers for the context and journeyMode.
	 *
	 * @param context authentication context
	 * @param journeyMode journeyMode type
	 * @return eligible providers
	 */
	@NotNull List<InternalProvider> eligibleProviders(
			@NotNull ScenarioContext context,
			@NotNull JourneyMode journeyMode
	);

	/**
	 * Returns eligible providers for the context, pipeline, and journeyMode.
	 *
	 * @param context authentication context
	 * @param pipelineType pipeline type
	 * @param journeyMode journeyMode type
	 * @return eligible providers
	 */
	default @NotNull List<InternalProvider> eligibleProviders(
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode
	) {
		return eligibleProviders(context, journeyMode);
	}

	/**
	 * Checks whether the provider can handle the context for the journeyMode.
	 *
	 * @param context authentication context
	 * @param provider provider instance
	 * @param journeyMode journeyMode type
	 * @return {@code true} if eligible
	 */
	boolean isEligible(
			@NotNull ScenarioContext context,
			@NotNull InternalProvider provider,
			@NotNull JourneyMode journeyMode
	);

	/**
	 * Checks whether the provider can handle the context for the pipeline and journeyMode.
	 *
	 * @param context authentication context
	 * @param provider provider instance
	 * @param pipelineType pipeline type
	 * @param journeyMode journeyMode type
	 * @return {@code true} if eligible
	 */
	default boolean isEligible(
			@NotNull ScenarioContext context,
			@NotNull InternalProvider provider,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode
	) {
		return isEligible(context, provider, journeyMode);
	}

	/**
	 * Discovers provider-subject data using loaded providers.
	 *
	 * @param context subject resolve context
	 * @return subject resolution or {@code null} when no resolver applies
	 */
	@Nullable SubjectResolution discoverSubject(@NotNull SubjectResolveContext context);

	/**
	 * Resolves subject data for the currently selected provider using only that
	 * provider's registered subject resolvers.
	 *
	 * @param providerId provider id to resolve for
	 * @param context subject resolve context
	 * @return subject resolution or {@code null} when the provider cannot resolve one
	 */
	default @Nullable SubjectResolution resolveSelectedSubject(
			@Nullable String providerId,
			@NotNull SubjectResolveContext context
	) {
		SubjectResolution resolution = discoverSubject(context);
		if (resolution == null)
			return null;
		if (providerId == null || providerId.isBlank())
			return resolution;
		return providerId.equalsIgnoreCase(resolution.getProviderId())
				? resolution
				: null;
	}

	/**
	 * Selects the provider link an account prefers: a primary link before the others, then the link whose provider
	 * has the highest priority, then the link whose provider id sorts last.
	 *
	 * @param links provider links of one account
	 * @return preferred link, or {@code null} when the account has no usable link
	 */
	@Nullable AccountProviderLink selectPreferredLink(@NotNull List<AccountProviderLink> links);
}
