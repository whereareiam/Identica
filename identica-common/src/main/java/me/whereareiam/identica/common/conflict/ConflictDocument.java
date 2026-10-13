package me.whereareiam.identica.common.conflict;

import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.model.conflict.ConflictKind;
import org.jetbrains.annotations.NotNull;

/**
 * The document that keeps the rules and outcome settings of one kind of conflict. It writes the defaults of every
 * outcome registered for its kind that it does not hold yet.
 */
public interface ConflictDocument {
	/**
	 * Returns the kind of conflict the document belongs to.
	 *
	 * @return kind of conflict
	 */
	@NotNull ConflictKind<?, ?> kind();

	/**
	 * Writes an outcome's default settings under its id unless the document holds settings for it already.
	 *
	 * @param outcome the registered outcome
	 */
	void include(@NotNull ConflictOutcome<?, ?, ?> outcome);
}
