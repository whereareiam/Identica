package me.whereareiam.identica.engine.pipeline.prepare.group.profile.phase;

import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.model.pipeline.prepare.PrepareAccountCandidateItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Resolve Pending Migration Account Phase")
class ResolvePendingMigrationAccountPhaseTest {
	@Mock
	private PipelineStateStore pipelineStateStore;
	@Mock
	private AccountPersistenceService accountPersistenceService;
	@Mock
	private DeliveryService deliveryService;

	@DisplayName("Restores the pending migration account without requiring provider subject")
	@Test
	void restoresPendingMigrationAccountWithoutProviderSubject() {
		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		String connectionKey = "PlayerOne:127.0.0.1";

		PipelineState pendingState = PipelineState.initial();
		pendingState.setPipelineType(PipelineType.MIGRATION);
		pendingState.setScenario(MigrationContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new me.whereareiam.identica.identity.actor.ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.targetProviderId("credential")
				.build());
		pendingState.putItem(new MigrationPendingState(
				"credential",
				1234L,
				MigrationInitiator.USER,
				connectionUniqueId
		), Duration.ofMinutes(5).toMillis());
		pendingState.putItem(new JourneyStateItem(null, null, 0), Duration.ofMinutes(5).toMillis());

		doReturn(Optional.of(pendingState)).when(pipelineStateStore).find(any(PipelineStateReference.class));
		when(accountPersistenceService.findByUniqueId(accountUniqueId)).thenReturn(Optional.of(Account.builder()
				.uniqueId(accountUniqueId)
				.username("PlayerOne")
				.build()));

		ResolvePendingMigrationAccountPhase phase = new ResolvePendingMigrationAccountPhase(
				pipelineStateStore,
				accountPersistenceService,
				deliveryService
		);

		PipelineState state = PipelineState.initial();
		state.putItem(new PrepareContextItem(null, ProviderContext.of(
				"credential",
				null,
				"PlayerOne",
				ProviderOrigin.MANUAL
		), null, null), 0L);

		PrepareGroupState groupState = new PrepareGroupState();
		groupState.setRequest(PrepareRequest.builder()
				.connectionKey(connectionKey)
				.stage(PrepareStage.PROFILE)
				.identity(new me.whereareiam.identica.identity.actor.ConnectionIdentity("PlayerOne", "127.0.0.1"))
				.build());

		phase.execute(state, groupState).toCompletableFuture().join();

		PrepareAccountCandidateItem candidate = state.item(PrepareAccountCandidateItem.class).orElse(null);
		assertNotNull(candidate);
		assertEquals(accountUniqueId, candidate.getUniqueId());
		assertNotNull(candidate.getAccount());
		assertEquals("PlayerOne", candidate.getAccount().getUsername());
		assertNull(candidate.getLink());
		assertNull(candidate.getProfile());
	}
}
