package me.whereareiam.identica.model.registration;

import lombok.*;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.IdentityReference;
import me.whereareiam.identica.model.pipeline.ScenarioTransitionItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineStateItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Registration context containing connection identity and mutable state.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class RegistrationContext implements ScenarioContext, PipelineStateItem {
	@Builder.Default
	private @NotNull IdentityReference identityReference = new IdentityReference();
	private @NotNull ConnectionIdentity identity;
	private @Nullable String intendedServer;

	@Setter
	private @Nullable ProviderContext provider;

	@Setter
	private @Nullable ScenarioTransitionItem transition;

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

	public static class RegistrationContextBuilder {
		private final IdentityReference identityReference = new IdentityReference();

		public @NotNull RegistrationContextBuilder connectionUniqueId(@Nullable UUID connectionUniqueId) {
			identityReference.setConnectionUniqueId(connectionUniqueId);
			return this;
		}

		public @NotNull RegistrationContextBuilder accountUniqueId(@Nullable UUID accountUniqueId) {
			identityReference.setAccountUniqueId(accountUniqueId);
			return this;
		}

		public @NotNull RegistrationContext build() {
			return new RegistrationContext(identityReference, identity, intendedServer, provider, transition);
		}
	}
}
