package me.whereareiam.identica.pipeline;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.IdentityReference;
import me.whereareiam.identica.model.pipeline.ScenarioTransitionItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Base contract for scenario-specific contexts.
 */
public interface ScenarioContext {
	default @NotNull IdentityReference getIdentityReference() {
		return getIdentity().getIdentityReference();
	}

	default @Nullable UUID getConnectionUniqueId() {
		return getIdentityReference().getConnectionUniqueId();
	}

	@NotNull ConnectionIdentity getIdentity();

	@Nullable String getIntendedServer();

	@Nullable ProviderContext getProvider();

	void setProvider(@Nullable ProviderContext provider);

	/**
	 * Returns which provider handles the connection, and why. Kept with the pending pipeline.
	 *
	 * @return provider selection, empty when nothing claims the connection
	 */
	@NotNull ProviderSelection getSelection();

	/**
	 * Replaces the provider selection with one ranked again by {@link ProviderSelector}.
	 *
	 * @param selection ranked provider selection
	 */
	void setSelection(@NotNull ProviderSelection selection);

	@Nullable ScenarioTransitionItem getTransition();

	void setTransition(@Nullable ScenarioTransitionItem transition);

	default @Nullable UUID getAccountUniqueId() {
		return getIdentityReference().getAccountUniqueId();
	}

	default void setAccountUniqueId(@Nullable UUID accountUniqueId) {
		getIdentityReference().setAccountUniqueId(accountUniqueId);
	}

	default @Nullable String getUsername() {
		return getIdentity().getUsername();
	}

	default @Nullable String getIp() {
		return getIdentity().getIp();
	}
}
