package me.whereareiam.identica.model.pipeline.prepare;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.pipeline.state.StateKey;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PrepareContextItem {
	/**
	 * Key of the prepare context in a pipeline state. Only lives during one run and is never saved.
	 */
	public static final StateKey<PrepareContextItem> KEY = StateKey.run("prepare:context", PrepareContextItem.class);

	/**
	 * UUID the client claimed when it started logging in, kept from the handshake for the later stages.
	 */
	private @Nullable UUID claimedUniqueId;
	/**
	 * Provider the connection continues with, once its profile is known.
	 */
	private @Nullable ProviderContext provider;
	/**
	 * Which provider handles the connection, and why. Restored from the handshake for the later stages.
	 */
	private @NotNull ProviderSelection selection = ProviderSelection.empty();
	/**
	 * Preferred provider link of the account the connection belongs to, resolved for the handshake.
	 */
	private @Nullable AccountProviderLink preferredLink;
	/**
	 * Journey mode of the scenario the connection heads to, resolved for the handshake.
	 */
	private @Nullable JourneyMode journeyMode;
	/**
	 * Decision of the handshake policies, once the handshake was evaluated.
	 */
	private @Nullable HandshakeDecision handshake;
}
