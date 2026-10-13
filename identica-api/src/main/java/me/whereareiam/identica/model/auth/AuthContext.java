package me.whereareiam.identica.model.auth;

import lombok.*;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.IdentityReference;
import me.whereareiam.identica.model.pipeline.ScenarioTransitionItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.StateKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Authentication context containing connection identity and mutable state.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AuthContext implements ScenarioContext {
	/**
	 * Key of the authentication scenario context in a pipeline state. Saved with a pending pipeline.
	 */
	public static final StateKey<AuthContext> KEY = StateKey.persisted("scenario:authentication", AuthContext.class);

	@Builder.Default
	private @NotNull IdentityReference identityReference = new IdentityReference();
	private @NotNull ConnectionIdentity identity;
	private @Nullable String intendedServer;

	@Setter
	private @Nullable ProviderContext provider;

	/**
	 * Which provider handles the connection, and why.
	 */
	@Setter
	private @Nullable ProviderSelection selection;

	@Setter
	private @Nullable ScenarioTransitionItem transition;

	/**
	 * Returns the connection identity attached to this context.
	 *
	 * @return connection identity
	 */
	public @NotNull ConnectionIdentity getIdentity() {
		return identity;
	}

	@Override
	public @NotNull ProviderSelection getSelection() {
		return selection != null ? selection : ProviderSelection.empty();
	}

	@Override
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

	/**
	 * Returns the username for this connection.
	 *
	 * @return username or {@code null}
	 */
	public @Nullable String getUsername() {
		return identity.getUsername();
	}

	/**
	 * Returns the IP address for this connection.
	 *
	 * @return IP address or {@code null}
	 */
	public @Nullable String getIp() {
		return identity.getIp();
	}

	public static class AuthContextBuilder {
		private final IdentityReference identityReference = new IdentityReference();

		public @NotNull AuthContextBuilder connectionUniqueId(@Nullable UUID connectionUniqueId) {
			identityReference.setConnectionUniqueId(connectionUniqueId);
			return this;
		}

		public @NotNull AuthContextBuilder accountUniqueId(@Nullable UUID accountUniqueId) {
			identityReference.setAccountUniqueId(accountUniqueId);
			return this;
		}

		public @NotNull AuthContext build() {
			return new AuthContext(identityReference, identity, intendedServer, provider, selection, transition);
		}
	}

}
