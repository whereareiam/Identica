package me.whereareiam.identica.common.provider.selection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.provider.selection.source.AccountLinkClaimSource;
import me.whereareiam.identica.common.provider.selection.source.DiscoveryClaimSource;
import me.whereareiam.identica.common.provider.selection.source.EntrypointClaimSource;
import me.whereareiam.identica.common.provider.selection.source.MigrationClaimSource;
import me.whereareiam.identica.common.provider.selection.source.NameMatchClaimSource;
import me.whereareiam.identica.common.provider.selection.source.PlayerChoiceClaimSource;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The claim sources asked for every connection: Identica's own, and those enabled providers contribute.
 */
@Singleton
public class ProviderClaimSourceRegistry implements Registry<ProviderClaimSource> {
	private final Set<ProviderClaimSource> sources = new CopyOnWriteArraySet<>();

	@Inject
	public ProviderClaimSourceRegistry(
			@NotNull MigrationClaimSource migration,
			@NotNull DiscoveryClaimSource discovery,
			@NotNull AccountLinkClaimSource accountLink,
			@NotNull EntrypointClaimSource entrypoint,
			@NotNull PlayerChoiceClaimSource playerChoice,
			@NotNull NameMatchClaimSource nameMatch
	) {
		sources.addAll(Set.of(migration, discovery, accountLink, entrypoint, playerChoice, nameMatch));
	}

	@Override
	public void register(@NotNull ProviderClaimSource source) {
		sources.add(source);
	}

	@Override
	public void unregister(@NotNull ProviderClaimSource source) {
		sources.remove(source);
	}

	@Override
	public @NotNull Set<ProviderClaimSource> values() {
		return Collections.unmodifiableSet(sources);
	}
}
