package me.whereareiam.identica.model.auth.request;

import lombok.*;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.IdentityReference;
import me.whereareiam.identica.model.pipeline.ScenarioTransitionItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Connection request details used to build the connection context.
 */
@Getter
@ToString
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("unused")
public class ConnectionRequest {
	@Builder.Default
	private final @NotNull IdentityReference identityReference = new IdentityReference();
	private final @NotNull ConnectionIdentity identity;
	private final @Nullable String intendedServer;
	/**
	 * Provider context selected for this connection, if known.
	 */
	private final @Nullable ProviderContext provider;
	/**
	 * Scenario transition contract propagated across pipeline reroutes.
	 */
	private final @Nullable ScenarioTransitionItem transition;

	/**
	 * Returns the connection identity for this connection request.
	 *
	 * @return connection identity
	 */
	public @NotNull ConnectionIdentity getIdentity() {
		return identity;
	}

	/**
	 * Returns the username from the connection identity.
	 *
	 * @return username or {@code null}
	 */
	public @Nullable UUID getConnectionUniqueId() {
		if (identityReference.getConnectionUniqueId() != null)
			return identityReference.getConnectionUniqueId();

		return identity.getConnectionUniqueId();
	}

	public @Nullable UUID getAccountUniqueId() {
		if (identityReference.getAccountUniqueId() != null)
			return identityReference.getAccountUniqueId();

		return identity.getAccountUniqueId();
	}

	public @NotNull IdentityReference getIdentityReference() {
		if (identityReference.getConnectionUniqueId() == null)
			identityReference.setConnectionUniqueId(identity.getConnectionUniqueId());
		if (identityReference.getClaimedUniqueId() == null)
			identityReference.setClaimedUniqueId(identity.getClaimedUniqueId());
		if (identityReference.getObservedUniqueId() == null)
			identityReference.setObservedUniqueId(identity.getObservedUniqueId());
		if (identityReference.getAccountUniqueId() == null)
			identityReference.setAccountUniqueId(identity.getAccountUniqueId());

		return identityReference;
	}

	public @Nullable String getUsername() {
		return identity.getUsername();
	}

	/**
	 * Returns the IP address from the connection identity.
	 *
	 * @return IP address or {@code null}
	 */
	public @Nullable String getIp() {
		return identity.getIp();
	}

	public static class ConnectionRequestBuilder {
		private final IdentityReference identityReference = new IdentityReference();

		public @NotNull ConnectionRequestBuilder connectionUniqueId(@Nullable UUID connectionUniqueId) {
			identityReference.setConnectionUniqueId(connectionUniqueId);
			return this;
		}

		public @NotNull ConnectionRequestBuilder accountUniqueId(@Nullable UUID accountUniqueId) {
			identityReference.setAccountUniqueId(accountUniqueId);
			return this;
		}

		public @NotNull ConnectionRequest build() {
			return new ConnectionRequest(identityReference, identity, intendedServer, provider, transition);
		}
	}
}
