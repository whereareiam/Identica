package me.whereareiam.identica.database.schema;

import me.whereareiam.dialectica.SchemaManager;
import org.jetbrains.annotations.NotNull;

/**
 * Contributes provider-owned Dialectica schema configuration.
 *
 * <p>Implementations may register entity packages and other
 * schema-manager options for a provider-specific persistence slice.
 *
 * <p>Example:
 * <pre>{@code
 * public final class CredentialSchemaContributor implements SchemaContributor {
 *     @Override
 *     public void contribute(@NotNull SchemaManager schemaManager) {
 *         schemaManager
 *                 .scanPackages("me.whereareiam.identica.provider.credential.database.entity");
 *     }
 * }
 * }</pre>
 */
public interface SchemaContributor {
	/**
	 * Applies this contribution to the provided schema manager.
	 *
	 * @param schemaManager schema manager to configure
	 */
	void contribute(@NotNull SchemaManager schemaManager);
}
