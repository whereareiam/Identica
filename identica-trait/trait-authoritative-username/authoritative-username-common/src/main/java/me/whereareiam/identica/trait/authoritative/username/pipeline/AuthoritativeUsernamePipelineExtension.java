package me.whereareiam.identica.trait.authoritative.username.pipeline;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.pipeline.phase.PhasePlacement;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.trait.authoritative.username.pipeline.prepare.group.policy.phase.ApplyAuthoritativeUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.authentication.group.identity.phase.SynchronizeAuthenticationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.authentication.group.policy.phase.PersistAuthenticationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.authentication.group.policy.phase.ReviewAuthenticationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.migration.group.identity.phase.SynchronizeMigrationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.registration.group.identity.phase.SynchronizeRegistrationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.registration.group.policy.phase.PersistRegistrationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.registration.group.policy.phase.ReviewRegistrationUsernamePhase;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AuthoritativeUsernamePipelineExtension implements PipelineExtension {
	private final ApplyAuthoritativeUsernamePhase applyAuthoritativeUsernamePhase;
	private final SynchronizeAuthenticationUsernamePhase synchronizeAuthenticationUsernamePhase;
	private final SynchronizeRegistrationUsernamePhase synchronizeRegistrationUsernamePhase;
	private final SynchronizeMigrationUsernamePhase synchronizeMigrationUsernamePhase;
	private final ReviewAuthenticationUsernamePhase reviewAuthenticationUsernamePhase;
	private final ReviewRegistrationUsernamePhase reviewRegistrationUsernamePhase;
	private final PersistAuthenticationUsernamePhase persistAuthenticationUsernamePhase;
	private final PersistRegistrationUsernamePhase persistRegistrationUsernamePhase;

	@Override
	public @NotNull String id() {
		return "authoritative-username:phases";
	}

	@Override
	public void apply(@NotNull PipelineExtensionBuilder builder) {
		builder.registerPreparePhase("policy", applyAuthoritativeUsernamePhase, PhasePlacement.first());
		builder.registerScenarioPhase(
				PipelineType.AUTHENTICATION,
				"identity",
				synchronizeAuthenticationUsernamePhase,
				PhasePlacement.after("refresh-provider-profile")
		);
		builder.registerScenarioPhase(
				PipelineType.REGISTRATION,
				"identity",
				synchronizeRegistrationUsernamePhase,
				PhasePlacement.after("link-provider")
		);
		builder.registerScenarioPhase(
				PipelineType.MIGRATION,
				"identity",
				synchronizeMigrationUsernamePhase,
				PhasePlacement.after("apply-provider-link")
		);
		builder.registerScenarioPhase(
				PipelineType.AUTHENTICATION,
				"policy",
				reviewAuthenticationUsernamePhase,
				PhasePlacement.first()
		);
		builder.registerScenarioPhase(
				PipelineType.AUTHENTICATION,
				"policy",
				persistAuthenticationUsernamePhase,
				PhasePlacement.last()
		);
		builder.registerScenarioPhase(
				PipelineType.REGISTRATION,
				"policy",
				reviewRegistrationUsernamePhase,
				PhasePlacement.first()
		);
		builder.registerScenarioPhase(
				PipelineType.REGISTRATION,
				"policy",
				persistRegistrationUsernamePhase,
				PhasePlacement.last()
		);
	}
}
