package me.whereareiam.identica.common.provider.runtime.binding;

import lombok.Builder;
import lombok.Getter;
import me.whereareiam.identica.common.provider.runtime.binding.registration.CompositeRegistration;
import me.whereareiam.identica.common.provider.runtime.binding.registration.Registration;
import me.whereareiam.identica.handshake.policy.HandshakePolicy;
import me.whereareiam.identica.provider.ProviderPlatformBinding;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Getter
@Builder
public final class ProviderRuntimeBindings implements Registration {
	@Builder.Default
	private final @NotNull Set<HandshakePolicy> handshakePolicies = Set.of();
	@Builder.Default
	private final @NotNull Set<ProviderPlatformBinding> platformBindings = Set.of();
	@Builder.Default
	private final @NotNull Set<ConnectionDisconnectedParticipant> connectionDisconnectedParticipants = Set.of();
	@Builder.Default
	private final @NotNull Set<ConnectionCompletedParticipant> connectionCompletedParticipants = Set.of();
	@Builder.Default
	private final @NotNull Set<ConnectionTerminatedParticipant> connectionTerminatedParticipants = Set.of();
	@Builder.Default
	private final @NotNull Set<AccountLifecycleParticipant> accountLifecycleParticipants = Set.of();

	@Builder.Default
	private @NotNull Registration registration = Registration.noop();

	public void bind(@NotNull ProviderContributionBinders binders) {
		close();

		CompositeRegistration composite = new CompositeRegistration();
		binders.bindAll(composite, HandshakePolicy.class, handshakePolicies);
		binders.bindAll(composite, ProviderPlatformBinding.class, platformBindings);
		binders.bindAll(composite, ConnectionDisconnectedParticipant.class, connectionDisconnectedParticipants);
		binders.bindAll(composite, ConnectionCompletedParticipant.class, connectionCompletedParticipants);
		binders.bindAll(composite, ConnectionTerminatedParticipant.class, connectionTerminatedParticipants);
		binders.bindAll(composite, AccountLifecycleParticipant.class, accountLifecycleParticipants);
		registration = composite;
	}

	@Override
	public void close() {
		registration.close();
		registration = Registration.noop();
	}
}
