package me.whereareiam.identica.model.pipeline.prepare;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.state.PipelineStateItem;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrepareContextItem implements PipelineStateItem {
	private @Nullable ProviderContext provider;
	/**
	 * Preferred provider link of the account the connection belongs to, resolved for the handshake.
	 */
	private @Nullable AccountProviderLink preferredLink;
	/**
	 * Decision of the handshake policies, once the handshake was evaluated.
	 */
	private @Nullable HandshakeDecision handshake;
}
