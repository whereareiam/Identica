package me.whereareiam.identica.feature.verification.database;

import me.whereareiam.dialectica.SchemaManager;
import me.whereareiam.identica.database.schema.SchemaContributor;
import me.whereareiam.identica.feature.verification.database.entity.VerificationEnrollmentEntity;
import org.jetbrains.annotations.NotNull;

/** Registers verification tables after the core account schema has initialized. */
public final class VerificationSchemaContributor implements SchemaContributor {
	@Override
	public void contribute(@NotNull SchemaManager schemaManager) {
		schemaManager.scanPackages(
				VerificationEnrollmentEntity.class.getClassLoader(),
				VerificationEnrollmentEntity.class.getPackageName()
		);
	}
}
