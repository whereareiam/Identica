package me.whereareiam.identica.common.provider.runtime.binding.snapshot;

import com.google.inject.*;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.common.provider.runtime.binding.ProviderRuntimeBindings;
import me.whereareiam.identica.database.schema.SchemaContributor;
import me.whereareiam.identica.handshake.policy.HandshakePolicy;
import me.whereareiam.identica.provider.ProviderPlatformBinding;
import me.whereareiam.identica.provider.capability.ProviderCapabilityCoordinator;
import me.whereareiam.identica.provider.eligibility.ProviderEligibilityResolver;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import me.whereareiam.identica.provider.subject.SubjectResolver;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionCompletedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionDisconnectedParticipant;
import me.whereareiam.identica.replication.store.participant.ConnectionTerminatedParticipant;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ProviderContributionSnapshotFactory {
	private static final TypeLiteral<Set<HandshakePolicy>> HANDSHAKE_POLICIES = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ProviderEligibilityResolver>> ELIGIBILITY_RESOLVERS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<SubjectResolver>> SUBJECT_RESOLVERS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ProviderMigrationPrecheck>> MIGRATION_PRECHECKS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<SchemaContributor>> SCHEMA_CONTRIBUTORS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ProviderPlatformBinding>> PLATFORM_BINDINGS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ConnectionDisconnectedParticipant>> CONNECTION_DISCONNECTED_PARTICIPANTS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ConnectionCompletedParticipant>> CONNECTION_COMPLETED_PARTICIPANTS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<ConnectionTerminatedParticipant>> CONNECTION_TERMINATED_PARTICIPANTS = new TypeLiteral<>() {};
	private static final TypeLiteral<Set<AccountLifecycleParticipant>> ACCOUNT_LIFECYCLE_PARTICIPANTS = new TypeLiteral<>() {};

	private final ProviderCapabilityCoordinator capabilityCoordinator;

	public @NotNull ProviderContributionSnapshot snapshot(@NotNull Injector injector) {
		return ProviderContributionSnapshot.builder()
				.runtimeBindings(ProviderRuntimeBindings.builder()
						.handshakePolicies(copySet(resolveSet(injector, HANDSHAKE_POLICIES)))
						.platformBindings(copySet(resolveSet(injector, PLATFORM_BINDINGS)))
						.connectionDisconnectedParticipants(copyOrderedSet(resolveSet(injector, CONNECTION_DISCONNECTED_PARTICIPANTS)))
						.connectionCompletedParticipants(copyOrderedSet(resolveSet(injector, CONNECTION_COMPLETED_PARTICIPANTS)))
						.connectionTerminatedParticipants(copyOrderedSet(resolveSet(injector, CONNECTION_TERMINATED_PARTICIPANTS)))
						.accountLifecycleParticipants(copyOrderedSet(resolveSet(injector, ACCOUNT_LIFECYCLE_PARTICIPANTS)))
						.build())
				.eligibilityResolvers(copySet(resolveSet(injector, ELIGIBILITY_RESOLVERS)))
				.subjectResolvers(copySet(resolveSet(injector, SUBJECT_RESOLVERS)))
				.migrationPrechecks(copySet(resolveSet(injector, MIGRATION_PRECHECKS)))
				.capabilityContributions(copySet(capabilityCoordinator.resolveCapabilityContributions(injector)))
				.build();
	}

	public @NotNull Set<SchemaContributor> schemaContributors(@NotNull Injector injector) {
		return copySet(resolveSet(injector, SCHEMA_CONTRIBUTORS));
	}

	private <T> @NotNull Set<T> resolveSet(@NotNull Injector injector, @NotNull TypeLiteral<Set<T>> type) {
		try {
			Set<T> resolved = injector.getInstance(Key.get(type));
			return resolved != null ? resolved : Set.of();
		} catch (ConfigurationException ignored) {
			return Set.of();
		}
	}

	private <T> @NotNull Set<T> copySet(Set<T> values) {
		if (values == null || values.isEmpty())
			return Set.of();

		return Set.copyOf(values);
	}

	private <T> @NotNull Set<T> copyOrderedSet(Set<T> values) {
		if (values == null || values.isEmpty())
			return Set.of();

		return Collections.unmodifiableSet(new LinkedHashSet<>(values));
	}
}
