package me.whereareiam.identica.engine.pipeline.scenario.type.authentication;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.engine.pipeline.AbstractPipelineGroupRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.finalize.FinalizeGroup;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.JourneyGroup;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.phase.*;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.preparation.PreparationGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity.IdentityGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity.phase.LoadIdentityProfilePhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity.phase.RefreshProviderProfilePhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity.phase.ResolveIdentityPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.policy.PolicyGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.session.SessionGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.session.phase.BuildSessionPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.session.phase.OpenSessionPhase;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import org.jetbrains.annotations.NotNull;

@Singleton
public class AuthenticationPipelineRegistry extends AbstractPipelineGroupRegistry implements PipelineRegistry {
	@Inject
	public AuthenticationPipelineRegistry(
			PreparationGroup preparationGroup,
			JourneyGroup journeyGroup,
			FinalizeGroup finalizeGroup,
			IdentityGroup identityGroup,
			PolicyGroup policyGroup,
			SessionGroup sessionGroup,
			LoadContextPhase loadContextPhase,
			BuildExecutionPlanPhase buildExecutionPlanPhase,
			ApplyRulesPhase applyRulesPhase,
			ExecutePlanPhase executePlanPhase,
			ResolveIdentityPhase resolveIdentityPhase,
			LoadIdentityProfilePhase loadIdentityProfilePhase,
			RefreshProviderProfilePhase refreshProviderProfilePhase,
			BuildSessionPhase buildSessionPhase,
			OpenSessionPhase openSessionPhase
	) {
		register(preparationGroup);
		register(journeyGroup);
		register(identityGroup);
		register(policyGroup);
		register(sessionGroup);
		register(finalizeGroup);

		registerPhase(journeyGroup.id(), loadContextPhase, PhasePlacement.first());
		registerPhase(journeyGroup.id(), buildExecutionPlanPhase, PhasePlacement.after(loadContextPhase.id()));
		registerPhase(journeyGroup.id(), applyRulesPhase, PhasePlacement.after(buildExecutionPlanPhase.id()));
		registerPhase(journeyGroup.id(), executePlanPhase, PhasePlacement.last());

		registerPhase(identityGroup.id(), resolveIdentityPhase, PhasePlacement.first());
		registerPhase(identityGroup.id(), loadIdentityProfilePhase, PhasePlacement.after(resolveIdentityPhase.id()));
		registerPhase(identityGroup.id(), refreshProviderProfilePhase, PhasePlacement.after(loadIdentityProfilePhase.id()));

		registerPhase(sessionGroup.id(), buildSessionPhase, PhasePlacement.first());
		registerPhase(sessionGroup.id(), openSessionPhase, PhasePlacement.last());
	}

	@Override
	protected @NotNull String phaseLabel() {
		return "authentication pipeline phase";
	}
}
