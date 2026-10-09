package me.whereareiam.identica.feature.restriction.join.pipeline.scenario.type.authentication.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionSettings;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.authentication.IdentityState;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.feature.restriction.RestrictionService;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;
import me.whereareiam.identica.feature.restriction.join.pipeline.phase.base.AbstractEnforceJoinRestrictionPhase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
public class EnforceAuthenticationJoinRestrictionPhase extends AbstractEnforceJoinRestrictionPhase<AuthContext, IdentityState> {
	@Inject
	public EnforceAuthenticationJoinRestrictionPhase(
			RestrictionService restrictionService,
			ProviderOperations providerOperations,
			Provider<JoinRestrictionMessages> messagesProvider,
			Provider<JoinRestrictionSettings> settingsProvider
	) {
		super(restrictionService, providerOperations, messagesProvider, settingsProvider);
	}

	@Override
	public int order() {
		return 225;
	}

	@Override
	public @NotNull Class<IdentityState> stateType() {
		return IdentityState.class;
	}

	@Override
	protected @Nullable AuthContext resolveContext(@NotNull PipelineState pipelineState, @NotNull IdentityState state) {
		return pipelineState.getScenario(pipelineState.getPipelineType()) instanceof AuthContext authContext
				? authContext
				: null;
	}

	@Override
	protected @Nullable ProviderContext resolveProvider(@Nullable AuthContext context, @NotNull IdentityState state) {
		return state.getProvider();
	}

	@Override
	protected boolean allowResumeBypass(@NotNull JoinRestrictionSettings settings, @NotNull PipelineState pipelineState) {
		if (!settings.getResumeBypass().isAuthentication())
			return false;

		var meta = identityMeta(pipelineState);
		return meta != null && meta.isResumed();
	}
}
