package me.whereareiam.identica.engine.pipeline.scenario.type.registration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.engine.pipeline.AbstractPipelineGroupRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.finalize.FinalizeGroup;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.JourneyGroup;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.phase.*;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.preparation.PreparationGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.identity.IdentityGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.identity.phase.CreateAccountPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.identity.phase.EnsureNewAccountPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.identity.phase.LinkProviderPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.identity.phase.RecordRegistrationAddressPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.identity.phase.ValidateProviderPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.policy.PolicyGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.session.SessionGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.session.phase.BuildSessionPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.group.session.phase.OpenSessionPhase;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import org.jetbrains.annotations.NotNull;

@Singleton
public class RegistrationPipelineRegistry extends AbstractPipelineGroupRegistry implements PipelineRegistry {
	@Inject
	public RegistrationPipelineRegistry(
			PreparationGroup preparationGroup,
			JourneyGroup journeyGroup,
			FinalizeGroup finalizeGroup,
			IdentityGroup identityGroup,
			PolicyGroup policyGroup,
			SessionGroup sessionGroup,
			LoadContextPhase loadContextPhase,
			ResolveJourneyModePhase resolveJourneyModePhase,
			BuildExecutionPlanPhase buildExecutionPlanPhase,
			ApplyRulesPhase applyRulesPhase,
			ExecutePlanPhase executePlanPhase,
			ValidateProviderPhase validateProviderPhase,
			EnsureNewAccountPhase ensureNewAccountPhase,
			CreateAccountPhase createAccountPhase,
			LinkProviderPhase linkProviderPhase,
			RecordRegistrationAddressPhase recordRegistrationAddressPhase,
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
		registerPhase(journeyGroup.id(), resolveJourneyModePhase, PhasePlacement.after(loadContextPhase.id()));
		registerPhase(journeyGroup.id(), buildExecutionPlanPhase, PhasePlacement.after(resolveJourneyModePhase.id()));
		registerPhase(journeyGroup.id(), applyRulesPhase, PhasePlacement.after(buildExecutionPlanPhase.id()));
		registerPhase(journeyGroup.id(), executePlanPhase, PhasePlacement.last());

		registerPhase(identityGroup.id(), validateProviderPhase, PhasePlacement.first());
		registerPhase(identityGroup.id(), ensureNewAccountPhase, PhasePlacement.after(validateProviderPhase.id()));
		registerPhase(identityGroup.id(), createAccountPhase, PhasePlacement.after(ensureNewAccountPhase.id()));
		registerPhase(identityGroup.id(), linkProviderPhase, PhasePlacement.after(createAccountPhase.id()));
		registerPhase(identityGroup.id(), recordRegistrationAddressPhase, PhasePlacement.after(linkProviderPhase.id()));

		registerPhase(sessionGroup.id(), buildSessionPhase, PhasePlacement.first());
		registerPhase(sessionGroup.id(), openSessionPhase, PhasePlacement.last());
	}

	@Override
	protected @NotNull String phaseLabel() {
		return "registration pipeline phase";
	}
}
