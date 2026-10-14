package me.whereareiam.identica.common.provider.selection.source;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.pipeline.state.StoredPipelineState;
import me.whereareiam.identica.type.PrepareStage;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Migration Claim Source")
class MigrationClaimSourceTest {
	private static final String USERNAME = "PlayerOne";

	@Mock
	private PipelineStateStore pipelineStateStore;

	@DisplayName("Claims a connection with a pending migration for its target")
	@Test
	void claimsForTheTargetOfAPendingMigration() {
		when(pipelineStateStore.find(any(PipelineStateReference.class)))
				.thenReturn(Optional.of(pendingState(Duration.ofMinutes(5).toMillis())));

		assertEquals(List.of(ProviderClaim.of("alpha", ProviderClaimOrigin.MIGRATION)), claims());

		ArgumentCaptor<PipelineStateReference> reference = ArgumentCaptor.forClass(PipelineStateReference.class);
		verify(pipelineStateStore).find(reference.capture());
		assertEquals(PipelineStateReference.of(USERNAME, "127.0.0.1"), reference.getValue());
	}

	@DisplayName("Claims nothing for a connection without a pending migration")
	@Test
	void claimsNothingWithoutAPendingMigration() {
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());

		assertTrue(claims().isEmpty());
	}

	@DisplayName("Claims nothing once the pending migration expired")
	@Test
	void claimsNothingForAnExpiredMigration() {
		when(pipelineStateStore.find(any(PipelineStateReference.class)))
				.thenReturn(Optional.of(expired(pendingState(Duration.ofMinutes(5).toMillis()))));

		assertTrue(claims().isEmpty());
	}

	@DisplayName("Claims nothing for a stored pipeline that is not a migration")
	@Test
	void claimsNothingForAnotherPipeline() {
		PipelineState authentication = pendingState(Duration.ofMinutes(5).toMillis());
		authentication.setPipelineType(PipelineType.AUTHENTICATION);
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.of(authentication));

		assertTrue(claims().isEmpty());
	}

	private Collection<ProviderClaim> claims() {
		return new MigrationClaimSource(pipelineStateStore).claims(ProviderClaimRequest.builder()
				.identity(new ConnectionIdentity(USERNAME, "127.0.0.1"))
				.stage(PrepareStage.HANDSHAKE)
				.build());
	}

	private PipelineState pendingState(long ttlMillis) {
		UUID connectionUniqueId = UUID.randomUUID();
		PipelineState pendingState = PipelineState.initial();
		pendingState.setPipelineType(PipelineType.MIGRATION);
		pendingState.setScenario(MigrationContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new ConnectionIdentity(UUID.randomUUID(), USERNAME, "127.0.0.1"))
				.targetProviderId("alpha")
				.build());
		pendingState.put(MigrationPendingState.KEY, new MigrationPendingState(
				"alpha",
				1234L,
				MigrationInitiator.USER,
				connectionUniqueId
		), ttlMillis);
		return pendingState;
	}

	/**
	 * Returns the state as the store hands it out once the pending marker has outlived its time to live.
	 */
	private PipelineState expired(PipelineState pendingState) {
		StoredPipelineState stored = pendingState.store();
		stored.getValues().get(MigrationPendingState.KEY.id()).setExpiresAt(1L);
		return PipelineState.restore(stored);
	}
}
