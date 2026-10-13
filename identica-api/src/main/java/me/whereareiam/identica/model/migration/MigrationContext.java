package me.whereareiam.identica.model.migration;

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
 * Migration context containing connection identity and target provider state.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class MigrationContext implements ScenarioContext {
	/**
	 * Key of the migration scenario context in a pipeline state. Saved with a pending pipeline.
	 */
	public static final StateKey<MigrationContext> KEY =
			StateKey.persisted("scenario:migration", MigrationContext.class);

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

	@Setter
	private @Nullable String targetProviderId;

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

	public static class MigrationContextBuilder {
		private final IdentityReference identityReference = new IdentityReference();

		public @NotNull MigrationContextBuilder connectionUniqueId(@Nullable UUID connectionUniqueId) {
			identityReference.setConnectionUniqueId(connectionUniqueId);
			return this;
		}

		public @NotNull MigrationContextBuilder accountUniqueId(@Nullable UUID accountUniqueId) {
			identityReference.setAccountUniqueId(accountUniqueId);
			return this;
		}

		public @NotNull MigrationContext build() {
			return new MigrationContext(identityReference, identity, intendedServer, provider, selection, transition, targetProviderId);
		}
	}
}
