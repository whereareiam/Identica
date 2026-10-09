package me.whereareiam.identica.feature.restriction.join.pipeline.scenario.type.registration.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionSettings;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.registration.IdentityState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.feature.restriction.RestrictionService;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;
import me.whereareiam.identica.feature.restriction.join.pipeline.phase.base.AbstractEnforceJoinRestrictionPhase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
public class EnforceRegistrationJoinRestrictionPhase extends AbstractEnforceJoinRestrictionPhase<RegistrationContext, IdentityState> {
	@Inject
	public EnforceRegistrationJoinRestrictionPhase(
			RestrictionService restrictionService,
			ProviderOperations providerOperations,
			Provider<JoinRestrictionMessages> messagesProvider,
			Provider<JoinRestrictionSettings> settingsProvider
	) {
		super(restrictionService, providerOperations, messagesProvider, settingsProvider);
	}

	@Override
	public int order() {
		return 150;
	}

	@Override
	public @NotNull Class<IdentityState> stateType() {
		return IdentityState.class;
	}

	@Override
	protected @Nullable RegistrationContext resolveContext(@NotNull PipelineState pipelineState, @NotNull IdentityState state) {
		return state.getContext();
	}

	@Override
	protected @Nullable ProviderContext resolveProvider(@Nullable RegistrationContext context, @NotNull IdentityState state) {
		return context != null ? context.getProvider() : null;
	}

	@Override
	protected boolean allowResumeBypass(@NotNull JoinRestrictionSettings settings, @NotNull PipelineState pipelineState) {
		if (!settings.getResumeBypass().isRegistration())
			return false;

		var resumeState = identityMeta(pipelineState);
		return resumeState != null && resumeState.isResumed();
	}
}
