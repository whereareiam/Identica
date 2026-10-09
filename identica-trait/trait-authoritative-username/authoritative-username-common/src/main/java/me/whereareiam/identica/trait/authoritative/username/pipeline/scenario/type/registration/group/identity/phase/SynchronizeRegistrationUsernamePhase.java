package me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.registration.group.identity.phase;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.trait.authoritative.username.model.AuthoritativeUsernameMessages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.registration.IdentityState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.pipeline.phase.base.AbstractSynchronizeUsernamePhase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
public class SynchronizeRegistrationUsernamePhase extends AbstractSynchronizeUsernamePhase<IdentityState> {
	@Inject
	public SynchronizeRegistrationUsernamePhase(
			ProviderManager providerManager,
			AccountUsernameStatePersistenceService statePersistenceService,
			Provider<AuthoritativeUsernameMessages> messagesProvider
	) {
		super(providerManager, statePersistenceService, messagesProvider);
	}

	@Override
	public @NotNull String id() {
		return "authoritative-username-sync";
	}

	@Override
	public int order() {
		return 500;
	}

	@Override
	public @NotNull Class<IdentityState> stateType() {
		return IdentityState.class;
	}

	@Override
	protected @Nullable ScenarioContext scenarioContext(@NotNull PipelineState pipelineState, @NotNull IdentityState state) {
		return state.getContext();
	}

	@Override
	protected @Nullable Account account(@NotNull IdentityState state) {
		return state.getAccount();
	}

	@Override
	protected @Nullable AccountProviderLink link(@NotNull IdentityState state) {
		return state.getLink();
	}

	@Override
	protected @Nullable AccountProviderProfile profile(@NotNull IdentityState state) {
		return state.getProfile();
	}

	@Override
	protected @NotNull String missingStateMessage() {
		return String.join("\n", messagesProvider.get().getPipeline().getIdentity().getSynchronizationFailed());
	}
}
