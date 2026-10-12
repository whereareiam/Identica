package me.whereareiam.identica.provider.credential.database.repository;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * The credential queries beyond reading and writing one credential by its provider subject, which
 * {@code EntityAccess<CredentialAccountEntity>} does.
 */
public interface CredentialAccountRepository {
	@SqlUpdate("""
			UPDATE identica_provider_credential_accounts
			   SET password_hash = :passwordHash,
			       hashing_method = :hashingMethod,
			       updated_at = :updatedAt
			 WHERE provider_id = :providerId
			   AND provider_subject = :providerSubject
			""")
	void updatePassword(
			@Bind("providerId") String providerId,
			@Bind("providerSubject") String providerSubject,
			@Bind("passwordHash") String passwordHash,
			@Bind("hashingMethod") String hashingMethod,
			@Bind("updatedAt") long updatedAt
	);
}
