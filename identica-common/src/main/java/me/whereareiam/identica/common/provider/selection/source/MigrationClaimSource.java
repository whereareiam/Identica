package me.whereareiam.identica.common.provider.selection.source;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Claims the connection for the target provider of a migration pending for it.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MigrationClaimSource implements ProviderClaimSource {
	private final PipelineStateStore pipelineStateStore;

	@Override
	public @NotNull Collection<ProviderClaim> claims(@NotNull ProviderClaimRequest request) {
		String connectionKey = request.getConnectionKey();
		if (connectionKey == null || connectionKey.isBlank()) return List.of();

		PipelineState pending = pipelineStateStore.find(PipelineStateReference.builder()
				.connectionKey(connectionKey)
				.build()).orElse(null);
		if (pending == null || pending.getPipelineType() != PipelineType.MIGRATION) return List.of();
		if (pending.get(MigrationPendingState.KEY).isEmpty()) return List.of();

		MigrationContext migration = (MigrationContext) pending.getScenario(PipelineType.MIGRATION);
		if (migration == null || migration.getTargetProviderId() == null || migration.getTargetProviderId().isBlank())
			return List.of();

		return List.of(ProviderClaim.of(migration.getTargetProviderId(), ProviderClaimOrigin.MIGRATION));
	}
}
