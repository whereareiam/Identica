package me.whereareiam.identica.message;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The messages every scenario has, one set per scenario, with IDs {@code identica:<scenario>/<name>}, such as
 * {@code identica:authentication/profile-missing}. Their texts sit under {@code scenarios.<scenario>} of core's
 * {@code messages.yml}.
 *
 * <pre>{@code
 * PipelineResult.failed(ScenarioMessages.of(pipelineType).getProfileMissing().create(messages.get()));
 * }</pre>
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScenarioMessages {
	private static final ScenarioMessages AUTHENTICATION = create(PipelineType.AUTHENTICATION, messages -> messages.getScenarios().getAuthentication(),
			messages -> messages.getScenarios().getAuthentication().getAuthenticationFailed());
	private static final ScenarioMessages REGISTRATION = create(PipelineType.REGISTRATION, messages -> messages.getScenarios().getRegistration(),
			messages -> messages.getScenarios().getRegistration().getRegistrationFailed());
	private static final ScenarioMessages MIGRATION = create(PipelineType.MIGRATION, messages -> messages.getScenarios().getMigration(),
			messages -> messages.getScenarios().getMigration().getMigrationFailed());

	/**
	 * The scenario failed for a reason Identica did not foresee, such as a step that threw.
	 */
	private final @NotNull MessageDefinition<Messages> failed;
	/**
	 * The player's login waited longer than {@code pipelineTtl}.
	 */
	private final @NotNull MessageDefinition<Messages> pipelineExpired;
	private final @NotNull MessageDefinition<Messages> noCompletionPipeline;
	private final @NotNull MessageDefinition<Messages> missingServer;
	private final @NotNull MessageDefinition<Messages> preparationMissingContext;
	private final @NotNull MessageDefinition<Messages> finalizeMissingResult;
	private final @NotNull MessageDefinition<Messages> identityMissingResult;
	private final @NotNull MessageDefinition<Messages> profileMissing;
	private final @NotNull MessageDefinition<Messages> accountMissing;
	private final @NotNull MessageDefinition<Messages> providerValidationMissing;
	private final @NotNull MessageDefinition<Messages> providerLinkMissing;
	private final @NotNull MessageDefinition<Messages> policyMissingResult;
	private final @NotNull MessageDefinition<Messages> accountReviewMissing;
	private final @NotNull MessageDefinition<Messages> ensureNewAccountMissing;
	private final @NotNull MessageDefinition<Messages> accountCreationMissing;
	private final @NotNull MessageDefinition<Messages> sessionMissingResult;
	private final @NotNull MessageDefinition<Messages> sessionBuildMissing;
	private final @NotNull MessageDefinition<Messages> journeyMissingResult;
	private final @NotNull MessageDefinition<Messages> journeyMissingContext;
	private final @NotNull MessageDefinition<Messages> journeyMissingPlan;

	/**
	 * Returns the messages of a scenario.
	 *
	 * @param pipelineType the scenario
	 * @return its messages
	 */
	public static @NotNull ScenarioMessages of(@NotNull PipelineType pipelineType) {
		return switch (pipelineType) {
			case AUTHENTICATION -> AUTHENTICATION;
			case REGISTRATION -> REGISTRATION;
			case MIGRATION -> MIGRATION;
		};
	}

	/**
	 * Returns the messages of every scenario.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<Messages>> all() {
		List<MessageDefinition<Messages>> all = new ArrayList<>();
		for (PipelineType pipelineType : PipelineType.values())
			all.addAll(of(pipelineType).definitions());

		return List.copyOf(all);
	}

	/**
	 * Returns this scenario's messages.
	 *
	 * @return the definitions
	 */
	public @NotNull List<MessageDefinition<Messages>> definitions() {
		return List.of(failed, pipelineExpired, noCompletionPipeline, missingServer, preparationMissingContext, finalizeMissingResult, identityMissingResult, profileMissing, accountMissing, providerValidationMissing, providerLinkMissing, policyMissingResult, accountReviewMissing, ensureNewAccountMissing, accountCreationMissing, sessionMissingResult, sessionBuildMissing, journeyMissingResult, journeyMissingContext, journeyMissingPlan);
	}

	private static @NotNull ScenarioMessages create(
			@NotNull PipelineType pipelineType,
			@NotNull Function<Messages, Messages.Scenarios.Scenario> scenario,
			@NotNull Function<Messages, List<String>> failed
	) {
		String prefix = "identica:" + pipelineType.name().toLowerCase(Locale.ROOT) + "/";
		return new ScenarioMessages(
				definition(prefix + "failed", failed),
				definition(prefix + "pipeline-expired", scenario.andThen(Messages.Scenarios.Scenario::getPipelineExpired)),
				definition(prefix + "no-completion-pipeline", scenario.andThen(Messages.Scenarios.Scenario::getNoCompletionPipeline)),
				definition(prefix + "missing-server", scenario.andThen(s -> s.getRouting().getMissingServer())),
				definition(prefix + "preparation-missing-context", scenario.andThen(s -> s.getErrors().getPreparationMissingContext())),
				definition(prefix + "finalize-missing-result", scenario.andThen(s -> s.getErrors().getFinalizeMissingResult())),
				definition(prefix + "identity-missing-result", scenario.andThen(s -> s.getErrors().getIdentity().getGroupMissingResult())),
				definition(prefix + "profile-missing", scenario.andThen(s -> s.getErrors().getIdentity().getProfileMissing())),
				definition(prefix + "account-missing", scenario.andThen(s -> s.getErrors().getIdentity().getAccountMissing())),
				definition(prefix + "provider-validation-missing", scenario.andThen(s -> s.getErrors().getIdentity().getProvider().getValidationMissing())),
				definition(prefix + "provider-link-missing", scenario.andThen(s -> s.getErrors().getIdentity().getProvider().getLinkMissing())),
				definition(prefix + "policy-missing-result", scenario.andThen(s -> s.getErrors().getPolicy().getGroupMissingResult())),
				definition(prefix + "account-review-missing", scenario.andThen(s -> s.getErrors().getPolicy().getAccountReviewMissing())),
				definition(prefix + "ensure-new-account-missing", scenario.andThen(s -> s.getErrors().getPolicy().getEnsureNewAccountMissing())),
				definition(prefix + "account-creation-missing", scenario.andThen(s -> s.getErrors().getPolicy().getAccountCreationMissing())),
				definition(prefix + "session-missing-result", scenario.andThen(s -> s.getErrors().getSession().getGroupMissingResult())),
				definition(prefix + "session-build-missing", scenario.andThen(s -> s.getErrors().getSession().getBuildMissing())),
				definition(prefix + "journey-missing-result", scenario.andThen(s -> s.getErrors().getJourney().getMissingResult())),
				definition(prefix + "journey-missing-context", scenario.andThen(s -> s.getErrors().getJourney().getMissingContext())),
				definition(prefix + "journey-missing-plan", scenario.andThen(s -> s.getErrors().getJourney().getMissingPlan()))
		);
	}

	private static @NotNull MessageDefinition<Messages> definition(@NotNull String id, @NotNull Function<Messages, List<String>> lines) {
		return MessageDefinition.<Messages>builder()
				.id(id)
				.text(messages -> MessageDefinition.lines(lines.apply(messages)))
				.build();
	}
}
