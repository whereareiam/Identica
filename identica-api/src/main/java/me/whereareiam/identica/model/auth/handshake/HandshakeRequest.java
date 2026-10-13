package me.whereareiam.identica.model.auth.handshake;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Request details for the handshake phase.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class HandshakeRequest {
	private final @NotNull ConnectionIdentity identity;
	/**
	 * Which provider handles the connection so far, and why. At the handshake nothing about the client is proven
	 * yet.
	 */
	private final @NotNull ProviderSelection selection;
	/**
	 * Preferred provider link of the account the connection belongs to, or {@code null} when no known account uses
	 * its subject or username.
	 */
	private final @Nullable AccountProviderLink preferredLink;
	/**
	 * Preferred journey mode of the scenario the connection heads to: authentication for a known account,
	 * registration otherwise.
	 */
	private final @Nullable JourneyMode journeyMode;
}
