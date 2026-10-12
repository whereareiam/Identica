package me.whereareiam.identica.common.config.migration;

import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Removes the {@code _version} that Configura 1.x, used by 2.2.1 and the development builds up to October 10, 2026,
 * wrote at the top of every document, from the core's documents. Only the settings document keeps it, as the
 * version of these migrations; in any other core document it means nothing and would stay there for good. Each
 * feature removes it from its own documents.
 */
@RequiredArgsConstructor
final class DocumentVersionsMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 7;
	static final String NAME = "document-versions";

	/** Every core document except the settings, which carry the version of these migrations. */
	static final List<String> DOCUMENTS = List.of(
			"messages",
			"commands",
			"persistence",
			"replication",
			"engine",
			"routing",
			"providers/providers",
			"providers/conflicts",
			"identity/username/conflicts",
			"identity/username/messages"
	);

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		for (String document : DOCUMENTS)
			files.removeVersion(document + extension);
	}
}
