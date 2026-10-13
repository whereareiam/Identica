package me.whereareiam.identica.model.conflict;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * A kind of conflict, such as two accounts wanting one username. It keys the outcomes of the kind and types what
 * they settle and decide.
 *
 * <pre>{@code
 * public static final ConflictKind<UsernameConflict, UsernameConflictDecision> KIND =
 *         ConflictKind.of("username", UsernameConflict.class, UsernameConflictDecision.class);
 * }</pre>
 *
 * @param <C> what outcomes of the kind settle
 * @param <D> what outcomes of the kind decide
 */
@Getter
@ToString
@EqualsAndHashCode(of = "key")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConflictKind<C, D> {
	/**
	 * Key of the kind, unique among kinds.
	 */
	private final @NotNull String key;

	/**
	 * Type of what outcomes of the kind settle.
	 */
	private final @NotNull Class<C> conflictType;

	/**
	 * Type of what outcomes of the kind decide.
	 */
	private final @NotNull Class<D> decisionType;

	/**
	 * Creates a kind.
	 *
	 * @param key          key of the kind
	 * @param conflictType type of what outcomes of the kind settle
	 * @param decisionType type of what outcomes of the kind decide
	 * @param <C>          what outcomes of the kind settle
	 * @param <D>          what outcomes of the kind decide
	 * @return kind
	 * @throws IllegalArgumentException when the key is blank
	 */
	public static <C, D> @NotNull ConflictKind<C, D> of(
			@NotNull String key,
			@NotNull Class<C> conflictType,
			@NotNull Class<D> decisionType
	) {
		if (key.isBlank()) throw new IllegalArgumentException("A conflict kind needs a key");

		return new ConflictKind<>(key, conflictType, decisionType);
	}
}
