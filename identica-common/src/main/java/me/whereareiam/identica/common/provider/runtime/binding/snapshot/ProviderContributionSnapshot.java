package me.whereareiam.identica.common.provider.runtime.binding.snapshot;

import lombok.Builder;
import lombok.Getter;
import me.whereareiam.identica.common.provider.runtime.binding.ProviderRuntimeBindings;
import me.whereareiam.identica.provider.capability.contribution.ProviderCapabilityContribution;
import me.whereareiam.identica.provider.eligibility.ProviderEligibilityResolver;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import me.whereareiam.identica.provider.subject.SubjectResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Getter
@Builder
public final class ProviderContributionSnapshot {
	@Builder.Default
	private final @NotNull ProviderRuntimeBindings runtimeBindings = ProviderRuntimeBindings.builder().build();
	@Builder.Default
	private final @NotNull Set<ProviderEligibilityResolver> eligibilityResolvers = Set.of();
	@Builder.Default
	private final @NotNull Set<SubjectResolver> subjectResolvers = Set.of();
	@Builder.Default
	private final @NotNull Set<ProviderMigrationPrecheck> migrationPrechecks = Set.of();
	@Builder.Default
	private final @NotNull Set<ProviderCapabilityContribution> capabilityContributions = Set.of();
}
