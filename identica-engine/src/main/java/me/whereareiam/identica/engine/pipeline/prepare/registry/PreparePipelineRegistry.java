package me.whereareiam.identica.engine.pipeline.prepare.registry;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.engine.pipeline.AbstractPipelineGroupRegistry;
import me.whereareiam.identica.engine.pipeline.prepare.group.context.ContextGroup;
import me.whereareiam.identica.engine.pipeline.prepare.group.context.phase.ResolveEntrypointPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.context.phase.ResolveKnownAccountPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.context.phase.ResolvePendingMigrationContextPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.context.phase.RestorePrepareStatePhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.finalize.FinalizeGroup;
import me.whereareiam.identica.engine.pipeline.prepare.group.finalize.phase.StorePrepareDecisionPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.handshake.HandshakeGroup;
import me.whereareiam.identica.engine.pipeline.prepare.group.handshake.phase.EvaluateHandshakePhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.handshake.phase.FinalizeHandshakePhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.policy.PolicyGroup;
import me.whereareiam.identica.engine.pipeline.prepare.group.profile.ProfileGroup;
import me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase.LoadPrepareAccountPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase.ResolvePendingMigrationAccountPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase.ResolvePreparedAccountPhase;
import me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase.ResolveProfilePhase;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import org.jetbrains.annotations.NotNull;

@Singleton
public class PreparePipelineRegistry extends AbstractPipelineGroupRegistry implements PipelineRegistry {
	@Inject
	public PreparePipelineRegistry(
			ContextGroup contextGroup,
			HandshakeGroup handshakeGroup,
			ProfileGroup profileGroup,
			PolicyGroup policyGroup,
			FinalizeGroup finalizeGroup,
			RestorePrepareStatePhase restorePrepareStatePhase,
			ResolveEntrypointPhase resolveEntrypointPhase,
			ResolvePendingMigrationContextPhase resolvePendingMigrationContextPhase,
			ResolveKnownAccountPhase resolveKnownAccountPhase,
			EvaluateHandshakePhase evaluateHandshakePhase,
			FinalizeHandshakePhase finalizeHandshakePhase,
			ResolveProfilePhase resolveProfilePhase,
			ResolvePendingMigrationAccountPhase resolvePendingMigrationAccountPhase,
			ResolvePreparedAccountPhase resolvePreparedAccountPhase,
			LoadPrepareAccountPhase loadPrepareAccountPhase,
			StorePrepareDecisionPhase storePrepareDecisionPhase
	) {
		register(contextGroup);
		register(handshakeGroup);
		register(profileGroup);
		register(policyGroup);
		register(finalizeGroup);

		registerPhase(contextGroup.id(), restorePrepareStatePhase, PhasePlacement.first());
		registerPhase(contextGroup.id(), resolveEntrypointPhase, PhasePlacement.last());
		registerPhase(contextGroup.id(), resolvePendingMigrationContextPhase, PhasePlacement.after(resolveEntrypointPhase.id()));
		registerPhase(contextGroup.id(), resolveKnownAccountPhase, PhasePlacement.after(resolvePendingMigrationContextPhase.id()));

		registerPhase(handshakeGroup.id(), evaluateHandshakePhase, PhasePlacement.first());
		registerPhase(handshakeGroup.id(), finalizeHandshakePhase, PhasePlacement.last());

		registerPhase(profileGroup.id(), resolveProfilePhase, PhasePlacement.first());
		registerPhase(profileGroup.id(), resolvePendingMigrationAccountPhase, PhasePlacement.after(resolveProfilePhase.id()));
		registerPhase(profileGroup.id(), resolvePreparedAccountPhase, PhasePlacement.after(resolvePendingMigrationAccountPhase.id()));
		registerPhase(profileGroup.id(), loadPrepareAccountPhase, PhasePlacement.last());

		registerPhase(finalizeGroup.id(), storePrepareDecisionPhase, PhasePlacement.first());
	}

	@Override
	protected @NotNull String phaseLabel() {
		return "prepare pipeline phase";
	}
}
