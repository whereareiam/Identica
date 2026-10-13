package me.whereareiam.identica.engine.pipeline.username;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.engine.pipeline.prepare.group.policy.phase.ApplyUsernamePhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.group.identity.phase.AssignAuthenticationUsernamePhase;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.group.identity.phase.AssignMigrationUsernamePhase;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the username phases to the pipelines: the username a player plays under is decided while preparing the
 * connection, and written when an account logs in or moves to another provider. A new account is created with its
 * username by the registration scenario itself.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UsernamePipelineExtension implements PipelineExtension {
	private final ApplyUsernamePhase applyUsernamePhase;
	private final AssignAuthenticationUsernamePhase assignAuthenticationUsernamePhase;
	private final AssignMigrationUsernamePhase assignMigrationUsernamePhase;

	@Override
	public @NotNull String id() {
		return "username:phases";
	}

	@Override
	public void apply(@NotNull PipelineExtensionBuilder builder) {
		builder.registerPreparePhase("policy", applyUsernamePhase, PhasePlacement.first());
		builder.registerScenarioPhase(
				PipelineType.AUTHENTICATION,
				"identity",
				assignAuthenticationUsernamePhase,
				PhasePlacement.before("refresh-provider-profile")
		);
		builder.registerScenarioPhase(
				PipelineType.MIGRATION,
				"identity",
				assignMigrationUsernamePhase,
				PhasePlacement.after("validate-provider")
		);
	}
}
