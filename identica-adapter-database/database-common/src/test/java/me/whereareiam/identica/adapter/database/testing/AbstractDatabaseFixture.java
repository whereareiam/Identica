package me.whereareiam.identica.adapter.database.testing;

import me.whereareiam.attache.LibraryManager;
import me.whereareiam.identica.adapter.database.DefaultDatabaseService;
import me.whereareiam.identica.model.config.persistence.Persistence;
import org.jdbi.v3.core.Jdbi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;

public abstract class AbstractDatabaseFixture implements DatabaseFixture {
	private DefaultDatabaseService databaseService;
	private Jdbi jdbi;
	private Path dataPath;
	private boolean started;

	protected abstract Persistence createPersistence(Path dataPath);

	protected synchronized void ensureStarted() {
		if (started) return;
		try {
			dataPath = Files.createTempDirectory("identica-test-");
			Persistence persistence = createPersistence(dataPath);
			databaseService = new DefaultDatabaseService(() -> persistence, dataPath, mock(LibraryManager.class));
			databaseService.initialize();
			jdbi = databaseService.getJdbi();
			started = true;
		} catch (RuntimeException e) {
			started = false;
			jdbi = null;
			databaseService = null;
			dataPath = null;
			throw e;
		} catch (IOException e) {
			throw new IllegalStateException("Failed to create temp directory for tests", e);
		}
	}

	@Override
	public Jdbi jdbi() {
		ensureStarted();
		return jdbi;
	}

	@Override
	public void reset() {
		useHandle(handle -> {
			handle.execute("DELETE FROM identica_provider_profiles");
			handle.execute("DELETE FROM identica_provider_links");
			handle.execute("DELETE FROM identica_accounts");
		});
	}

	@Override
	public void shutdown() {
		if (!started) return;

		try {
			if (databaseService != null) {
				databaseService.shutdown();
			}
		} finally {
			started = false;
			jdbi = null;
			databaseService = null;
			dataPath = null;
		}
	}

	@Override
	public String toString() {
		return name();
	}
}
