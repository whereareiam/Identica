package me.whereareiam.identica.model.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a part renderer did with a part: showed it, possibly with something to take down later, cannot show it to the
 * player yet, such as chat on the loading screen, or cannot show it to the player at all, such as a dialog on a client
 * without dialogs.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class PartRendering {
	private static final PartRendering SHOWN = new PartRendering(State.SHOWN, null);
	private static final PartRendering NOT_YET = new PartRendering(State.NOT_YET, null);
	private static final PartRendering UNSUPPORTED = new PartRendering(State.UNSUPPORTED, null);

	private final @NotNull State state;
	/**
	 * Takes the part down once its message is no longer current, or {@code null} for a part that cannot be taken back,
	 * such as a chat line.
	 */
	private final @Nullable Close close;

	/**
	 * The part was shown and stays until it goes away by itself.
	 *
	 * @return the rendering
	 */
	public static @NotNull PartRendering shown() {
		return SHOWN;
	}

	/**
	 * The part was shown and stays until Identica takes it down: when its prompt is answered or replaced by another
	 * one, when the next notice replaces a notice, or when the player leaves.
	 *
	 * @param close takes the part down
	 * @return the rendering
	 */
	public static @NotNull PartRendering shown(@NotNull Close close) {
		return new PartRendering(State.SHOWN, close);
	}

	/**
	 * The player cannot see the part now but will, such as chat while the player is on the loading screen or between
	 * servers. Identica renders it again once the player joined a server's world.
	 *
	 * @return the rendering
	 */
	public static @NotNull PartRendering notYet() {
		return NOT_YET;
	}

	/**
	 * The part cannot be shown to this player; the part type's fallbacks are shown instead.
	 *
	 * @return the rendering
	 */
	public static @NotNull PartRendering unsupported() {
		return UNSUPPORTED;
	}

	/**
	 * Tells whether the part was shown.
	 *
	 * @return {@code true} when it was shown
	 */
	public boolean isShown() {
		return state == State.SHOWN;
	}

	/**
	 * Tells whether the part waits until the player can see it.
	 *
	 * @return {@code true} when it waits
	 */
	public boolean isNotYet() {
		return state == State.NOT_YET;
	}

	/**
	 * What became of a part.
	 */
	public enum State {
		/**
		 * The part was shown.
		 */
		SHOWN,
		/**
		 * The part waits until the player can see it.
		 */
		NOT_YET,
		/**
		 * The part cannot be shown to the player.
		 */
		UNSUPPORTED
	}

	/**
	 * Takes a shown part down.
	 */
	@FunctionalInterface
	public interface Close {
		/**
		 * Takes the part down, such as by closing a dialog or hiding a boss bar.
		 */
		void close();
	}
}
