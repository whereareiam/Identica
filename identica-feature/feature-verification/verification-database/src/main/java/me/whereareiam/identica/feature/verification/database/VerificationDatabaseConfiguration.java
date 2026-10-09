package me.whereareiam.identica.feature.verification.database;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.verification.database.repository.VerificationEnrollmentRepository;
import me.whereareiam.identica.feature.verification.database.repository.VerificationRecoveryCodeRepository;
import me.whereareiam.identica.feature.verification.database.repository.VerificationSelectionRepository;
import org.jdbi.v3.core.Jdbi;

public class VerificationDatabaseConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(VerificationPersistenceService.class).to(DefaultVerificationPersistenceService.class).in(Singleton.class);
	}

	@Provides
	@Singleton
	public VerificationEnrollmentRepository provideVerificationEnrollmentRepository(Jdbi jdbi) {
		return jdbi.onDemand(VerificationEnrollmentRepository.class);
	}

	@Provides
	@Singleton
	public VerificationSelectionRepository provideVerificationSelectionRepository(Jdbi jdbi) {
		return jdbi.onDemand(VerificationSelectionRepository.class);
	}

	@Provides
	@Singleton
	public VerificationRecoveryCodeRepository provideVerificationRecoveryCodeRepository(Jdbi jdbi) {
		return jdbi.onDemand(VerificationRecoveryCodeRepository.class);
	}
}
