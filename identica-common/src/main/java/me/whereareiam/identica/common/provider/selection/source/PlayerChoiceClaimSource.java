package me.whereareiam.identica.common.provider.selection.source;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Claims the connection again for the provider the player chose in the pipeline still pending for it, so a choice
 * outlasts a reconnect. Only the choice is carried over; proof belongs to the connection that gave it.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PlayerChoiceClaimSource implements ProviderClaimSource {
	private final PipelineStateStore pipelineStateStore;

	@Override
	public @NotNull Collection<ProviderClaim> claims(@NotNull ProviderClaimRequest request) {
		PipelineStateReference reference = PipelineStateReference.from(request.getIdentity());
		if (reference.isEmpty()) return List.of();

		PipelineState pending = pipelineStateStore.find(reference).orElse(null);
		ScenarioContext scenario = pending != null ? pending.getScenario() : null;
		if (scenario == null) return List.of();

		return scenario.getSelection().claims().stream()
				.filter(claim -> claim.getOrigin() == ProviderClaimOrigin.PLAYER_CHOICE)
				.toList();
	}
}
