package me.whereareiam.identica.common.config.migration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.configura.Configura;
import me.whereareiam.strata.MigrationStream;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import me.whereareiam.strata.adapter.configura.ConfiguraTarget;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

/**
 * The migrations of Identica's configuration: the core's, which bring its documents of an older installation into
 * the layout this build reads, run before those every feature adds from its module. This is where the core's next
 * one is added.
 * <p>
 * The core's migrations cover its documents: the settings, engine, routing, messages, commands, persistence and
 * replication documents, {@code providers/providers}, and the username documents under {@code identity/username/};
 * {@code providers/conflicts} is gone, its last migration deletes it. On startup the ones an installation has not seen yet run in order, before any
 * document is loaded, followed by those of the compiled features. The number of the core's last one is kept as
 * {@code _version} at the top of the settings document.
 * <p>
 * That number does not tell layouts apart: 2.2.1 and the 3.0.0 development builds before October 10, 2026 wrote
 * version 1 into every document, and later builds recorded 1 or 2 in layouts of their own. So every migration after
 * {@link SessionRecordsMigration} finds the layout it changes by the documents and the sections that are there,
 * moves only what it finds, and does nothing on a directory that is past it. Sections of the core's documents that
 * a feature collects, such as {@code connection.sentinels}, are left for the feature's migrations.
 * <p>
 * To add a migration, append it with the next number and never change or renumber one that was released. A
 * migration with real logic becomes a class of its own that is listed here; only a migration that changes nothing is
 * written inline.
 * <p>
 * The directories below {@code providers/} that belong to a provider are not covered. A provider that has to migrate
 * its documents declares its own migrations on its working directory.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigMigrations {
	/** Name under which Strata tracks the core's migrations; it never changes. */
	public static final String ID = "identica/config";

	private static final String VERSION_DOCUMENT = "settings";

	/**
	 * Declares the core's migrations for a data directory.
	 *
	 * @param configura reads and writes the documents; it must have a {@code StrataFeature}, which
	 *                  keeps {@code _version} in a document when Identica rewrites it
	 * @param dataPath Identica's data directory
	 * @return migrations to run through Strata before any document is loaded
	 */
	public static @NotNull MigrationStream<ConfigContext> on(@NotNull Configura configura, @NotNull Path dataPath) {
		String extension = configura.extension();
		String versionFile = VERSION_DOCUMENT + extension;

		return MigrationStream.<ConfigContext>builder()
				.id(ID)
				.target(new ConfiguraTarget(configura, dataPath, versionFile))
				// Asked once for a directory without a version: one without settings is a new installation,
				// whose documents are about to be written in their current layout, so nothing has to run.
				.baseline((files, latest) -> files.exists(versionFile) ? OptionalInt.of(0) : OptionalInt.empty())
				// Changes nothing: 2.2.1 and the development builds before migrations wrote version 1 themselves.
				.migration(1, "initial-layout", files -> {})
				.migration(SessionRecordsMigration.VERSION, SessionRecordsMigration.NAME, new SessionRecordsMigration(extension))
				.migration(ReservationUsernameMigration.VERSION, ReservationUsernameMigration.NAME, new ReservationUsernameMigration(extension))
				.migration(UsernameIdentityMigration.VERSION, UsernameIdentityMigration.NAME, new UsernameIdentityMigration(extension))
				.migration(ConnectionLayoutMigration.VERSION, ConnectionLayoutMigration.NAME, new ConnectionLayoutMigration(extension))
				.migration(ProviderFeaturesMigration.VERSION, ProviderFeaturesMigration.NAME, new ProviderFeaturesMigration(extension))
				.migration(DocumentVersionsMigration.VERSION, DocumentVersionsMigration.NAME, new DocumentVersionsMigration(extension))
				.migration(UsernameConflictRulesMigration.VERSION, UsernameConflictRulesMigration.NAME, new UsernameConflictRulesMigration(extension))
				.migration(SessionPolicyMigration.VERSION, SessionPolicyMigration.NAME, new SessionPolicyMigration(extension))
				.build();
	}

	/**
	 * Lists the core's migrations followed by those of every other owner, in the order they run: the core's first,
	 * then the others by stream name.
	 *
	 * @param core   the core's migrations, from {@link #on(Configura, Path)}
	 * @param owners the migrations of the features and any other owner of documents in the directory
	 * @return the streams to run through Strata, in one run, before any document is loaded
	 */
	public static @NotNull List<MigrationStream<ConfigContext>> inOrder(
			@NotNull MigrationStream<ConfigContext> core,
			@NotNull Collection<MigrationStream<ConfigContext>> owners
	) {
		List<MigrationStream<ConfigContext>> streams = new ArrayList<>();
		streams.add(core);
		owners.stream()
				.sorted(Comparator.comparing(MigrationStream::getId))
				.forEach(streams::add);

		return List.copyOf(streams);
	}
}
