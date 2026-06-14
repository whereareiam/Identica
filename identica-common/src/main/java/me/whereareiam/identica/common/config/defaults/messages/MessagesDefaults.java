package me.whereareiam.identica.common.config.defaults.messages;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.type.DateTimePattern;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Singleton
public class MessagesDefaults implements DefaultsProvider<Messages> {
	@Override
	public Messages supply(@NotNull Messages messages) {
		applyGeneral(messages);
		applyCommands(messages);
		applyProviders(messages);
		applyEngine(messages);
		applyRouting(messages);
		applyScenarios(messages);
		return messages;
	}

	private void applyGeneral(Messages messages) {
		messages.setPrefix("<green>Identica</green> <dark_gray>| ");
		Messages.Format format = new Messages.Format();
		Messages.Format.Temporal temporal = new Messages.Format.Temporal();
		temporal.setDate(new DateTimePattern("dd.MM.yyyy"));
		temporal.setDateTime(new DateTimePattern("dd.MM.yyyy HH:mm:ss"));
		format.setTemporal(temporal);
		messages.setFormat(format);
	}

	private void applyCommands(Messages messages) {
		Messages.Commands commands = new MessagesCommandDefaults().supply(new Messages.Commands());
		messages.setCommands(commands);
	}

	private void applyProviders(Messages messages) {
		Messages.Providers providers = new Messages.Providers();
		providers.setNoProvidersAvailable(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>No providers available, if this issue persists",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		providers.setNoProvidersMatched(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>No providers matched, if this issue persists",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		messages.setProviders(providers);
	}

	private void applyEngine(Messages messages) {
		Messages.Engine engine = new Messages.Engine();
		engine.setConcurrentLoginKick(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Your session was interrupted.</white>",
				"<white>Logged in from another location.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		engine.setResumeSentineled(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Too many resume attempts.</white>",
				"<white>Please try again in <green>{seconds}s</green>.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		engine.setJourney(buildJourneyMessages());
		engine.setPrepare(buildPrepare());
		messages.setEngine(engine);
	}

	private void applyRouting(Messages messages) {
		Messages.Routing routing = new Messages.Routing();
		routing.setMissingServer(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Target server <gold>{server}</gold> is not configured.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		Messages.Routing.UnavailableServer unavailableServer = new Messages.Routing.UnavailableServer();
		unavailableServer.setMessage(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Target server <gold>{server}</gold> is currently unavailable.</white>",
				"<white>{serverReason}</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		unavailableServer.setFallbackReason(List.of("No details provided."));
		routing.setUnavailableServer(unavailableServer);
		messages.setRouting(routing);
	}

	private void applyScenarios(Messages messages) {
		Messages.Scenarios scenarios = new Messages.Scenarios();
		scenarios.setAuthentication(buildAuthentication());
		scenarios.setRegistration(buildRegistration());
		scenarios.setMigration(buildMigration());
		messages.setScenarios(scenarios);
	}

	private Messages.Engine.Prepare buildPrepare() {
		Messages.Engine.Prepare prepare = new Messages.Engine.Prepare();
		prepare.setHandshakeDenied(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Connection handshake was denied.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		Messages.Engine.Prepare.Errors errors = new Messages.Engine.Prepare.Errors();
		errors.setPreparePolicyMissing(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to evaluate prepare policy.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		prepare.setErrors(errors);
		return prepare;
	}

	private Messages.Scenarios.Authentication buildAuthentication() {
		Messages.Scenarios.Authentication authentication = new Messages.Scenarios.Authentication();
		applyScenario(authentication, "Authentication");
		authentication.setAuthenticationFailed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Authentication could not be completed.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		authentication.setSessionBuildFailed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to establish your session.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		authentication.setConflictEntrypointRequired(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Username conflict detected.</white>",
				"<white>Please join using the correct entrypoint:</white>",
				"<white><gold>{incomingProvider}</gold>: <green>{incomingHost}</green></white>",
				"<white><gold>{existingProvider}</gold>: <green>{existingHost}</green></white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		return authentication;
	}

	private Messages.Scenarios.Registration buildRegistration() {
		Messages.Scenarios.Registration registration = new Messages.Scenarios.Registration();
		applyScenario(registration, "Registration");
		registration.setRegistrationFailed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Registration could not be completed.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		registration.setAccountAlreadyExists(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>An account for this identity already exists.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		return registration;
	}

	private Messages.Scenarios.Migration buildMigration() {
		Messages.Scenarios.Migration migration = new Messages.Scenarios.Migration();
		applyScenario(migration, "Migration");
		migration.setMigrationFailed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Migration could not be completed.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		migration.setCancelled(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Your pending migration was cancelled.</white>",
				"<white>You are still using your previous login provider.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		return migration;
	}

	private void applyScenario(
			Messages.Scenarios.Scenario scenario,
			String label
	) {
		scenario.setPipelineKick(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>" + label + " is already in progress.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		scenario.setPipelineExpired(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Your time for " + label.toLowerCase() + " ran out.</white>",
				"<white>Reconnect to try again.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		Messages.Scenarios.Scenario.AdvanceBusy advanceBusy = new Messages.Scenarios.Scenario.AdvanceBusy();
		advanceBusy.setChat("{prefix}<white>Please wait, processing your request.</white>");
		advanceBusy.setKick(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Your previous request is still processing.</white>",
				"<white>Please try again in a moment.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		scenario.setAdvanceBusy(advanceBusy);
		scenario.setNoCompletionPipeline(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>" + label + " pipeline incomplete.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		Messages.Scenarios.Scenario.ScenarioRouting routingMessages = new Messages.Scenarios.Scenario.ScenarioRouting();
		routingMessages.setMissingServer(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Target server <gold>{server}</gold> is not configured.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		Messages.Scenarios.Scenario.ScenarioRouting.UnavailableServer unavailableServer =
				new Messages.Scenarios.Scenario.ScenarioRouting.UnavailableServer();
		unavailableServer.setMessage(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Target server <gold>{server}</gold> is currently unavailable.</white>",
				"<white>{serverReason}</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		unavailableServer.setFallbackReason(List.of("No details provided."));
		routingMessages.setUnavailableServer(unavailableServer);
		scenario.setRouting(routingMessages);
		scenario.setErrors(buildScenarioErrors(label));
	}

	private Messages.Scenarios.Scenario.Errors buildScenarioErrors(String label) {
		Messages.Scenarios.Scenario.Errors errors = new Messages.Scenarios.Scenario.Errors();
		errors.setPreparationMissingContext(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Connection setup did not produce a context.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		Messages.Scenarios.Scenario.Errors.Identity identity = new Messages.Scenarios.Scenario.Errors.Identity();
		identity.setGroupMissingResult(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>" + label + " journey could not continue.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		identity.setProfileMissing(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to load provider profile.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		identity.setReplicationMissing(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to synchronize username.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		identity.setAccountMissing(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Account data is missing.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		Messages.Scenarios.Scenario.Errors.Identity.Provider identityProvider = new Messages.Scenarios.Scenario.Errors.Identity.Provider();
		identityProvider.setValidationMissing(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to validate provider.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		identityProvider.setLinkMissing(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to link provider.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		identity.setProvider(identityProvider);
		errors.setIdentity(identity);

		Messages.Scenarios.Scenario.Errors.Policy policy = new Messages.Scenarios.Scenario.Errors.Policy();
		policy.setGroupMissingResult(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>" + label + " journey could not continue.</white>",
				"<white>Policy stage did not return a result.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		policy.setAccountReviewMissing(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to review account.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		policy.setEnsureNewAccountMissing(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to verify account status.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		policy.setAccountCreationMissing(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to create account.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		errors.setPolicy(policy);

		Messages.Scenarios.Scenario.Errors.Session session = new Messages.Scenarios.Scenario.Errors.Session();
		session.setGroupMissingResult(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>" + label + " journey could not continue.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		session.setBuildMissing(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to build a session.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		errors.setSession(session);

		Messages.Scenarios.Scenario.Errors.Journey journey = new Messages.Scenarios.Scenario.Errors.Journey();
		journey.setMissingResult(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Journey did not return a result.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		journey.setMissingContext(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Journey could not start.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		journey.setMissingPlan(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Journey could not start.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		errors.setJourney(journey);

		errors.setFinalizeMissingResult(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Finalization did not return a result.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		return errors;
	}

	private Messages.Engine.Journey buildJourneyMessages() {
		Messages.Engine.Journey journey = new Messages.Engine.Journey();
		Messages.Engine.Journey.Stage stage = new Messages.Engine.Journey.Stage();
		stage.setNoCompletion(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Journey stage pipeline incomplete.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));

		Messages.Engine.Journey.Step step = new Messages.Engine.Journey.Step();
		step.setNoStatus(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Journey step returned no status.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		Messages.Engine.Journey.Step.Enrollment enrollment = new Messages.Engine.Journey.Step.Enrollment();
		enrollment.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				"  <white>Select an authentication method for account</white>",
				"  <white>It can be changed later on.</white>",
				" ",
				"  <gray>Available providers:</gray>",
				"{entries}",
				" "
		));
		Messages.Engine.Journey.Step.Enrollment.EntryFormat enrollmentEntry = new Messages.Engine.Journey.Step.Enrollment.EntryFormat();
		enrollmentEntry.setFormat("   <dark_gray><click:run_command:/identica enroll {providerId}>▪ <gray>[{providerName}]:</gray> <white>{description}</click>");
		enrollmentEntry.setEmptyFormat("   <dark_gray><click:run_command:/identica enroll {providerId}>▪ <gray>[{providerName}]:</gray></click>");
		enrollment.setEntryFormat(enrollmentEntry);
		enrollment.setEmpty(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>No providers available for this account.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		enrollment.setDescriptions(Map.of(
				"premium", "Use Minecraft account for registration.",
				"credential", "Register using password."
		));
		step.setEnrollment(enrollment);
		journey.setStage(stage);
		journey.setStep(step);

		return journey;
	}
}
