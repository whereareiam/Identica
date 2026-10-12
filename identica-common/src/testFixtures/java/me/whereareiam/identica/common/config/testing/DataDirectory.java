package me.whereareiam.identica.common.config.testing;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.common.config.migration.ConfigMigrations;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.MigrationStream;
import me.whereareiam.strata.Strata;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import me.whereareiam.strata.adapter.configura.ConfiguraTarget;
import me.whereareiam.strata.adapter.configura.StrataFeature;
import me.whereareiam.strata.model.MigrationReport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * A data directory under test, read and written as Identica reads and writes it in one format, on which the
 * configuration migrations of the core and of other owners run.
 */
public final class DataDirectory {
	/** The documents of the providers, which the core does not migrate, below {@code providers/}. */
	private static final List<String> PROVIDER_DIRECTORIES = List.of("providers/Credential/", "providers/Premium/");

	/** The document a single migration under test records its version in. */
	private static final String UNDER_TEST_VERSION = "migration-version";

	@Getter
	private final Path path;
	@Getter
	private final Configura configura;

	/**
	 * Opens a data directory.
	 *
	 * @param path   the directory
	 * @param format the format its documents are written in
	 */
	public DataDirectory(Path path, Format format) {
		this.path = path;
		this.configura = Config.builder()
				.format(format)
				.module(new IdenticaModule())
				.feature(new StrataFeature())
				.build();
	}

	/**
	 * Runs the core's configuration migrations alone, as Identica does when no feature declares any.
	 *
	 * @return what ran
	 */
	public MigrationReport migrate() {
		return migrate(List.of());
	}

	/**
	 * Runs the core's configuration migrations and those of other owners in one run, as Identica does before it
	 * loads a document.
	 *
	 * @param owners how each other owner, such as a feature, declares its migrations for a data directory
	 * @return what ran
	 */
	public MigrationReport migrate(Collection<? extends Owner> owners) {
		List<MigrationStream<ConfigContext>> streams = owners.stream().map(owner -> owner.on(configura, path)).toList();
		return new Strata(ConfigMigrations.inOrder(ConfigMigrations.on(configura, path), streams)).migrate();
	}

	/**
	 * How an owner of documents declares its configuration migrations, such as {@code SentinelConfigMigrations::on}.
	 */
	@FunctionalInterface
	public interface Owner {
		/**
		 * Declares the migrations for a data directory.
		 *
		 * @param configura reads and writes the documents
		 * @param dataPath  the data directory
		 * @return the stream
		 */
		MigrationStream<ConfigContext> on(Configura configura, Path dataPath);
	}

	/**
	 * Runs one migration on its own, as the only one of a stream.
	 *
	 * @param migration the migration
	 * @return what ran
	 */
	public MigrationReport migrate(MigrationAction<ConfigContext> migration) {
		MigrationStream<ConfigContext> stream = MigrationStream.<ConfigContext>builder()
				.id("identica/config-under-test")
				.target(new ConfiguraTarget(configura, path, UNDER_TEST_VERSION + configura.extension()))
				.baseline((files, latest) -> OptionalInt.of(0))
				.migration(1, "under-test", migration)
				.build();

		return new Strata(List.of(stream)).migrate();
	}

	/**
	 * Writes a document.
	 *
	 * @param document the document, without its extension
	 * @param content  what the file holds
	 */
	public void write(String document, String content) {
		try {
			Path file = file(document);
			Files.createDirectories(file.getParent());
			Files.writeString(file, content);
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	/**
	 * Tells whether a document is there.
	 *
	 * @param document the document, without its extension
	 * @return whether its file exists
	 */
	public boolean exists(String document) {
		return Files.exists(file(document));
	}

	/**
	 * Reads a document as a tree.
	 *
	 * @param document the document, without its extension
	 * @return the tree
	 */
	public ObjectNode tree(String document) {
		try {
			return (ObjectNode) configura.mapper().readTree(Files.readAllBytes(file(document)));
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	/**
	 * Reads a value of a document.
	 *
	 * @param document the document, without its extension
	 * @param pointer  JSON pointer to the value
	 * @return the value, or a missing node
	 */
	public JsonNode at(String document, String pointer) {
		return exists(document) ? tree(document).at(pointer) : MissingNode.getInstance();
	}

	/**
	 * Returns every value of the core and feature documents by document and pointer, such as
	 * {@code settings:/identity/uniqueIdMode}. The entries of a list of objects are numbered, such as
	 * {@code providers/providers:/providers/0/id}; any other list is a value of its own.
	 *
	 * @return the values by document and pointer
	 */
	public Map<String, JsonNode> values() {
		Map<String, JsonNode> values = new TreeMap<>();
		for (String document : documents())
			flatten(document + ":", tree(document), values);

		return values;
	}

	/**
	 * Returns every file below the directory, except Strata's own.
	 *
	 * @return file contents by path relative to the directory
	 */
	public Map<String, String> files() {
		Map<String, String> files = new TreeMap<>();
		try (Stream<Path> paths = Files.walk(path)) {
			for (Path file : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
				String name = path.relativize(file).toString();
				if (!name.startsWith(".strata/")) files.put(name, Files.readString(file));
			}
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}

		return files;
	}

	/**
	 * Returns the core and feature documents there are.
	 *
	 * @return the documents, without their extension
	 */
	public List<String> documents() {
		String extension = configura.extension();

		return files().keySet().stream()
				.filter(name -> name.endsWith(extension))
				.filter(name -> PROVIDER_DIRECTORIES.stream().noneMatch(name::startsWith))
				.filter(name -> !name.startsWith(UNDER_TEST_VERSION))
				.map(name -> name.substring(0, name.length() - extension.length()))
				.toList();
	}

	private Path file(String document) {
		return path.resolve(document + configura.extension());
	}

	private static void flatten(String prefix, JsonNode node, Map<String, JsonNode> values) {
		if (node instanceof ObjectNode object && !object.isEmpty()) {
			object.fieldNames().forEachRemaining(field -> flatten(prefix + JsonPointer.empty().appendProperty(field), object.get(field), values));
			return;
		}
		if (node instanceof ArrayNode array && !array.isEmpty() && array.get(0) instanceof ObjectNode) {
			for (int index = 0; index < array.size(); index++) flatten(prefix + "/" + index, array.get(index), values);
			return;
		}

		values.put(prefix, node);
	}
}
