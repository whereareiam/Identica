package me.whereareiam.identica.provider.credential.database;

import me.whereareiam.dialectica.SchemaManager;
import me.whereareiam.identica.database.schema.SchemaContributor;
import org.jetbrains.annotations.NotNull;

public final class CredentialSchemaContributor implements SchemaContributor {
	@Override
	public void contribute(@NotNull SchemaManager schemaManager) {
		ClassLoader classLoader = CredentialSchemaContributor.class.getClassLoader();
		schemaManager.scanPackages(classLoader, "me.whereareiam.identica.provider.credential.database.entity");
	}
}
