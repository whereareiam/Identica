package me.whereareiam.identica.provider.password.pipeline.scenario.registration;

import com.google.inject.Provider;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.model.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.password.pipeline.scenario.AbstractPasswordStep;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.pipeline.PasswordAuthenticationAttempt;
import me.whereareiam.identica.provider.password.pipeline.PasswordRegistrationAttempt;
import me.whereareiam.identica.provider.password.pipeline.PasswordRegisterStateItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class AbstractPasswordRegistrationStep extends AbstractPasswordStep {
	protected final Provider<PasswordMessages> messagesProvider;
	protected final Provider<Settings> coreSettingsProvider;
	protected final PipelineStateStore pipelineStateStore;

	protected AbstractPasswordRegistrationStep(
			@NotNull String name,
			@NotNull Provider<PasswordMessages> messagesProvider,
			@NotNull Provider<Settings> coreSettingsProvider,
			@NotNull PipelineStateStore pipelineStateStore
	) {
		super(name);
		this.messagesProvider = messagesProvider;
		this.coreSettingsProvider = coreSettingsProvider;
		this.pipelineStateStore = pipelineStateStore;
	}

	protected long registrationTtlMs() {
		Settings settings = coreSettingsProvider.get();
		if (settings == null)
			return 0L;
		return settings.getConnection().getRegistration().pipelineTtlMillis();
	}

	protected @NotNull PipelineStateReference reference(@NotNull ScenarioContext context) {
		return PipelineStateReference.from(context);
	}

	protected @Nullable PasswordRegistrationAttempt consumeRegistrationAttempt(
			@NotNull ScenarioContext context,
			long ttlMs
	) {
		PipelineStateReference reference = reference(context);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return null;

		PasswordRegistrationAttempt input = stored.item(PasswordRegistrationAttempt.class).orElse(null);
		if (input == null) return null;

		stored.removeItem(PasswordRegistrationAttempt.class);
		if (ttlMs > 0) {
			pipelineStateStore.save(reference, stored, ttlMs);
		}
		return input;
	}

	protected @Nullable PasswordAuthenticationAttempt consumeAuthenticationAttempt(
			@NotNull ScenarioContext context,
			long ttlMs
	) {
		PipelineStateReference reference = reference(context);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return null;

		PasswordAuthenticationAttempt input = stored.item(PasswordAuthenticationAttempt.class).orElse(null);
		if (input == null) return null;

		stored.removeItem(PasswordAuthenticationAttempt.class);
		if (ttlMs > 0) {
			pipelineStateStore.save(reference, stored, ttlMs);
		}
		return input;
	}

	protected @Nullable PasswordRegisterStateItem getRegisterState(@NotNull ScenarioContext context) {
		PipelineState stored = pipelineStateStore.find(reference(context)).orElse(null);
		return stored != null ? stored.item(PasswordRegisterStateItem.class).orElse(null) : null;
	}

	protected void storeRegisterState(
			@NotNull ScenarioContext context,
			@NotNull PasswordRegisterStateItem stateItem,
			long ttlMs
	) {
		if (ttlMs <= 0) return;
		PipelineStateReference reference = reference(context);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return;

		stored.putItem(stateItem, ttlMs);
		pipelineStateStore.save(reference, stored, ttlMs);
	}

	protected void clearRegisterState(@NotNull ScenarioContext context, long ttlMs) {
		PipelineStateReference reference = reference(context);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return;

		stored.removeItem(PasswordRegisterStateItem.class);
		if (ttlMs > 0) {
			pipelineStateStore.save(reference, stored, ttlMs);
		}
	}

	protected static @NotNull String joinLines(@Nullable List<String> lines) {
		if (lines == null || lines.isEmpty())
			return "";
		return String.join("\n", lines);
	}
}
