package me.whereareiam.identica.database.schema;

/**
 * Identica's table of accounts, in the {@link DatabaseSchema#ACCOUNT_STREAM} schema. A table of
 * another owner that belongs to an account references it by name, so that its rows go with the
 * account:
 * <pre>{@code
 * @References(table = AccountTable.NAME, column = AccountTable.KEY, onDelete = ReferentialAction.CASCADE)
 * private UUID uniqueId;
 * }</pre>
 */
public final class AccountTable {
	/** Name of the table. */
	public static final String NAME = "identica_accounts";

	/** Its primary key column: the account's unique id. */
	public static final String KEY = "unique_id";
}
