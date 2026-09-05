package me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.registration.group.policy.phase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.type.registration.PolicyState;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameHistoryPersistenceService;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.pipeline.phase.base.AbstractPersistUsernamePhase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
public class PersistRegistrationUsernamePhase extends AbstractPersistUsernamePhase<PolicyState> {
	@Inject
	public PersistRegistrationUsernamePhase(
			ProviderManager providerManager,
			AccountUsernameStatePersistenceService statePersistenceService,
			AccountPersistenceService accountPersistenceService,
			AccountUsernameHistoryPersistenceService historyPersistenceService,
			EventManager eventManager
	) {
		super(providerManager, statePersistenceService, accountPersistenceService, historyPersistenceService, eventManager);
	}

	@Override
	public @NotNull String id() {
		return "persist-authoritative-username";
	}

	@Override
	public int order() {
		return 200;
	}

	@Override
	public @NotNull Class<PolicyState> stateType() {
		return PolicyState.class;
	}

	@Override
	protected @Nullable ScenarioContext scenarioContext(@NotNull PipelineState pipelineState, @NotNull PolicyState state) {
		return pipelineState.getScenario(pipelineState.getPipelineType()) instanceof RegistrationContext registrationContext
				? registrationContext
				: null;
	}
}
