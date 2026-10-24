package me.whereareiam.identica.engine.pipeline.scenario.type.migration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.engine.pipeline.AbstractPipelineGroupRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.finalize.FinalizeGroup;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.JourneyGroup;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.phase.*;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.preparation.PreparationGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.identity.IdentityGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.identity.phase.ApplyProviderLinkPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.identity.phase.ResolveAccountPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.identity.phase.ValidateProviderPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.session.SessionGroup;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.session.phase.BuildSessionPhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.session.phase.OpenSessionPhase;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import org.jetbrains.annotations.NotNull;

@Singleton
public class MigrationPipelineRegistry extends AbstractPipelineGroupRegistry implements PipelineRegistry {
	@Inject
	public MigrationPipelineRegistry(
			PreparationGroup preparationGroup,
			JourneyGroup journeyGroup,
			FinalizeGroup finalizeGroup,
			IdentityGroup identityGroup,
			SessionGroup sessionGroup,
			LoadContextPhase loadContextPhase,
			BuildExecutionPlanPhase buildExecutionPlanPhase,
			ApplyRulesPhase applyRulesPhase,
			ExecutePlanPhase executePlanPhase,
			ResolveAccountPhase resolveAccountPhase,
			ValidateProviderPhase validateProviderPhase,
			ApplyProviderLinkPhase applyProviderLinkPhase,
			BuildSessionPhase buildSessionPhase,
			OpenSessionPhase openSessionPhase
	) {
		register(preparationGroup);
		register(journeyGroup);
		register(identityGroup);
		register(sessionGroup);
		register(finalizeGroup);

		registerPhase(journeyGroup.id(), loadContextPhase, PhasePlacement.first());
		registerPhase(journeyGroup.id(), buildExecutionPlanPhase, PhasePlacement.after(loadContextPhase.id()));
		registerPhase(journeyGroup.id(), applyRulesPhase, PhasePlacement.after(buildExecutionPlanPhase.id()));
		registerPhase(journeyGroup.id(), executePlanPhase, PhasePlacement.last());

		registerPhase(identityGroup.id(), resolveAccountPhase, PhasePlacement.first());
		registerPhase(identityGroup.id(), validateProviderPhase, PhasePlacement.after(resolveAccountPhase.id()));
		registerPhase(identityGroup.id(), applyProviderLinkPhase, PhasePlacement.after(validateProviderPhase.id()));

		registerPhase(sessionGroup.id(), buildSessionPhase, PhasePlacement.first());
		registerPhase(sessionGroup.id(), openSessionPhase, PhasePlacement.last());
	}

	@Override
	protected @NotNull String phaseLabel() {
		return "migration pipeline phase";
	}
}
