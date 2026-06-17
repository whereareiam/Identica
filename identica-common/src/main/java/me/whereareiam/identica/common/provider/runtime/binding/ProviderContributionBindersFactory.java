package me.whereareiam.identica.common.provider.runtime.binding;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.provider.runtime.binding.registration.Registration;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.handshake.policy.HandshakePolicy;
import me.whereareiam.identica.provider.ProviderPlatformBinding;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Singleton
public final class ProviderContributionBindersFactory {
	private final ProviderContributionBinders binders;

	@Inject
	public ProviderContributionBindersFactory(
			HandshakeStore handshakeStore,
			Registry<ConnectionDisconnectedParticipant> connectionDisconnectedParticipants,
			Registry<ConnectionCompletedParticipant> connectionCompletedParticipants,
			Registry<ConnectionTerminatedParticipant> connectionTerminatedParticipants,
			Registry<AccountLifecycleParticipant> accountLifecycleParticipants
	) {
		this.binders = new ProviderContributionBinders(List.of(
				handshakePolicyBinder(handshakeStore),
				platformBindingBinder(),
				registryBinder(ConnectionDisconnectedParticipant.class, connectionDisconnectedParticipants),
				registryBinder(ConnectionCompletedParticipant.class, connectionCompletedParticipants),
				registryBinder(ConnectionTerminatedParticipant.class, connectionTerminatedParticipants),
				registryBinder(AccountLifecycleParticipant.class, accountLifecycleParticipants)
		));
	}

	public @NotNull ProviderContributionBinders create() {
		return binders;
	}

	private static @NotNull ProviderContributionBinder<HandshakePolicy> handshakePolicyBinder(@NotNull HandshakeStore handshakeStore) {
		return new ProviderContributionBinder<>() {
			@Override
			public @NotNull Class<HandshakePolicy> contributionType() {
				return HandshakePolicy.class;
			}

			@Override
			public @NotNull Registration bind(@NotNull HandshakePolicy contribution) {
				handshakeStore.registerPolicy(contribution);
				return () -> handshakeStore.unregisterPolicy(contribution);
			}
		};
	}

	private static @NotNull ProviderContributionBinder<ProviderPlatformBinding> platformBindingBinder() {
		return new ProviderContributionBinder<>() {
			@Override
			public @NotNull Class<ProviderPlatformBinding> contributionType() {
				return ProviderPlatformBinding.class;
			}

			@Override
			public @NotNull Registration bind(@NotNull ProviderPlatformBinding contribution) {
				contribution.register();
				return contribution::unregister;
			}
		};
	}

	private static <T> @NotNull ProviderContributionBinder<T> registryBinder(
			@NotNull Class<T> contributionType,
			@NotNull Registry<T> registry
	) {
		return new ProviderContributionBinder<>() {
			@Override
			public @NotNull Class<T> contributionType() {
				return contributionType;
			}

			@Override
			public @NotNull Registration bind(@NotNull T contribution) {
				registry.register(contribution);
				return () -> registry.unregister(contribution);
			}
		};
	}
}
