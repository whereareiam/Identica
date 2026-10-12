package me.whereareiam.identica.adapter.database;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.jdbi.v3.core.Jdbi;

public class JdbiProvider implements Provider<Jdbi> {
	private final DefaultDatabaseService databaseService;

	@Inject
	public JdbiProvider(DefaultDatabaseService databaseService) {
		this.databaseService = databaseService;
	}

	@Override
	public Jdbi get() {
		if (!databaseService.isInitialized()) {
			throw new IllegalStateException("The database is not open yet; it opens in the persistence phase of the startup");
		}

		return databaseService.getJdbi();
	}
}
