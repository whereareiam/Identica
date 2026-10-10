package me.whereareiam.identica.provider.credential.pipeline.step.base;

import com.google.inject.Provider;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.pipeline.journey.step.StatefulStep;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.credential.pipeline.CredentialAuthenticationAttempt;
import me.whereareiam.identica.provider.credential.pipeline.CredentialRegisterStateItem;
import me.whereareiam.identica.provider.credential.pipeline.CredentialRegistrationAttempt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Credential step that works on input a player gave between two runs. It reads and changes only the state of
 * the run it is given.
 */
public abstract class AbstractCredentialStatefulStep extends AbstractCredentialStep implements StatefulStep {
	protected final Provider<Engine> coreSettingsProvider;

	protected AbstractCredentialStatefulStep(
			@NotNull String name,
			@NotNull Provider<Engine> coreSettingsProvider
	) {
		super(name);
		this.coreSettingsProvider = coreSettingsProvider;
	}

	protected final long registrationTtlMs() {
		Engine settings = coreSettingsProvider.get();
		if (settings == null) return 0L;
		return settings.getScenarios().getRegistration().pipelineTtlMillis();
	}

	protected final long migrationTtlMs() {
		Engine settings = coreSettingsProvider.get();
		if (settings == null) return 0L;
		return settings.getScenarios().getMigration().pipelineTtlMillis();
	}

	/**
	 * Takes the password a player gave for registration out of the run's state.
	 */
	protected final @Nullable CredentialRegistrationAttempt consumeRegistrationAttempt(@NotNull PipelineState state) {
		CredentialRegistrationAttempt input = state.item(CredentialRegistrationAttempt.class).orElse(null);
		if (input != null) state.removeItem(CredentialRegistrationAttempt.class);

		return input;
	}

	/**
	 * Takes the password a player gave for authentication out of the run's state.
	 */
	protected final @Nullable CredentialAuthenticationAttempt consumeAuthenticationAttempt(@NotNull PipelineState state) {
		CredentialAuthenticationAttempt input = state.item(CredentialAuthenticationAttempt.class).orElse(null);
		if (input != null) state.removeItem(CredentialAuthenticationAttempt.class);

		return input;
	}

	protected final @Nullable CredentialRegisterStateItem getRegisterState(@NotNull PipelineState state) {
		return state.item(CredentialRegisterStateItem.class).orElse(null);
	}

	protected final void storeRegisterState(
			@NotNull PipelineState state,
			@NotNull CredentialRegisterStateItem stateItem,
			long ttlMs
	) {
		if (ttlMs <= 0) return;
		state.putItem(stateItem, ttlMs);
	}

	protected final void clearRegisterState(@NotNull PipelineState state) {
		state.removeItem(CredentialRegisterStateItem.class);
	}

	protected static @NotNull String joinLines(@Nullable List<String> lines) {
		if (lines == null || lines.isEmpty()) return "";
		return String.join("\n", lines);
	}
}
