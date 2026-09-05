package me.whereareiam.identica.trait.authoritative.username.database;

import me.whereareiam.dialectica.SchemaManager;
import me.whereareiam.identica.database.schema.SchemaContributor;
import org.jetbrains.annotations.NotNull;

public final class AuthoritativeUsernameSchemaContributor implements SchemaContributor {
	@Override
	public void contribute(@NotNull SchemaManager schemaManager) {
		ClassLoader classLoader = AuthoritativeUsernameSchemaContributor.class.getClassLoader();
		schemaManager.scanPackages(classLoader, "me.whereareiam.identica.trait.authoritative.username.database.entity");
	}
}
