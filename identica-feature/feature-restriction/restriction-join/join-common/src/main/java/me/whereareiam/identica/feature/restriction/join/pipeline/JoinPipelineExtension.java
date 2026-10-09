package me.whereareiam.identica.feature.restriction.join.pipeline;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.feature.restriction.join.pipeline.prepare.group.policy.phase.ApplyJoinRestrictionPhase;
import me.whereareiam.identica.feature.restriction.join.pipeline.scenario.type.authentication.group.identity.phase.EnforceAuthenticationJoinRestrictionPhase;
import me.whereareiam.identica.feature.restriction.join.pipeline.scenario.type.registration.group.identity.phase.EnforceRegistrationJoinRestrictionPhase;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JoinPipelineExtension implements PipelineExtension {
	private final ApplyJoinRestrictionPhase applyJoinRestrictionPhase;
	private final EnforceAuthenticationJoinRestrictionPhase enforceAuthenticationJoinRestrictionPhase;
	private final EnforceRegistrationJoinRestrictionPhase enforceRegistrationJoinRestrictionPhase;

	@Override
	public @NotNull String id() {
		return "restriction:join:phases";
	}

	@Override
	public void apply(@NotNull PipelineExtensionBuilder builder) {
		builder.registerPreparePhase("policy", applyJoinRestrictionPhase, PhasePlacement.first());
		builder.registerScenarioPhase(
				PipelineType.AUTHENTICATION,
				"identity",
				enforceAuthenticationJoinRestrictionPhase,
				PhasePlacement.after("load-identity-profile")
		);
		builder.registerScenarioPhase(
				PipelineType.REGISTRATION,
				"identity",
				enforceRegistrationJoinRestrictionPhase,
				PhasePlacement.after("validate-provider")
		);
	}
}
