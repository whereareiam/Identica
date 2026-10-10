package me.whereareiam.identica.engine.pipeline.prepare.group.context.phase;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.prepare.PrepareGroupState;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Resolve Pending Migration Context Phase")
class ResolvePendingMigrationContextPhaseTest {
	private static final String USERNAME = "PlayerOne";
	private static final String CONNECTION_KEY = "PlayerOne|127.0.0.1|play.example.org|25565";

	@Mock
	private PipelineStateStore pipelineStateStore;

	@DisplayName("Selects the migration target for a connection that matches a pending migration")
	@Test
	void selectsTheTargetOfAPendingMigration() {
		when(pipelineStateStore.find(any(PipelineStateReference.class)))
				.thenReturn(Optional.of(pendingState(Duration.ofMinutes(5).toMillis())));

		PipelineState state = execute();

		ProviderContext provider = state.item(PrepareContextItem.class).orElseThrow().getProvider();
		assertNotNull(provider);
		assertEquals("credential", provider.getProviderId());
		assertEquals(USERNAME, provider.getProviderUsername());
		assertEquals(ProviderOrigin.MANUAL, provider.getSource());
		assertFalse(provider.isSubjectVerified());

		ArgumentCaptor<PipelineStateReference> reference = ArgumentCaptor.forClass(PipelineStateReference.class);
		verify(pipelineStateStore).find(reference.capture());
		assertEquals(CONNECTION_KEY, reference.getValue().getConnectionKey());
	}

	@DisplayName("Selects nothing for a connection without a pending migration")
	@Test
	void selectsNothingWithoutAPendingMigration() {
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());

		assertTrue(execute().item(PrepareContextItem.class).isEmpty());
	}

	@DisplayName("Selects nothing once the pending migration expired")
	@Test
	void selectsNothingForAnExpiredMigration() {
		when(pipelineStateStore.find(any(PipelineStateReference.class)))
				.thenReturn(Optional.of(pendingState(1L).pruneExpired(System.currentTimeMillis() + 1000L)));

		assertTrue(execute().item(PrepareContextItem.class).isEmpty());
	}

	@DisplayName("Selects nothing for a stored pipeline that is not a migration")
	@Test
	void selectsNothingForAnotherPipeline() {
		PipelineState authentication = pendingState(Duration.ofMinutes(5).toMillis());
		authentication.setPipelineType(PipelineType.AUTHENTICATION);
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.of(authentication));

		assertTrue(execute().item(PrepareContextItem.class).isEmpty());
	}

	private PipelineState execute() {
		PrepareGroupState groupState = new PrepareGroupState();
		groupState.setRequest(PrepareRequest.builder()
				.connectionKey(CONNECTION_KEY)
				.stage(PrepareStage.HANDSHAKE)
				.identity(new ConnectionIdentity(USERNAME, "127.0.0.1"))
				.build());

		PipelineState state = PipelineState.initial();
		new ResolvePendingMigrationContextPhase(pipelineStateStore).execute(state, groupState).toCompletableFuture().join();
		return state;
	}

	private PipelineState pendingState(long ttlMillis) {
		UUID connectionUniqueId = UUID.randomUUID();
		PipelineState pendingState = PipelineState.initial();
		pendingState.setPipelineType(PipelineType.MIGRATION);
		pendingState.setScenario(MigrationContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new ConnectionIdentity(UUID.randomUUID(), USERNAME, "127.0.0.1"))
				.targetProviderId("credential")
				.build());
		pendingState.putItem(new MigrationPendingState(
				"credential",
				1234L,
				MigrationInitiator.USER,
				connectionUniqueId
		), ttlMillis);
		return pendingState;
	}
}
