package me.whereareiam.identica.model.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a part renderer did with a part: showed it, possibly with something to take down later, or could not show it
 * to the player, such as a dialog on a client without dialogs.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class PartRendering {
	private static final PartRendering SHOWN = new PartRendering(true, null);
	private static final PartRendering UNSUPPORTED = new PartRendering(false, null);

	private final boolean shown;
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
	 * The part was shown and stays until Identica closes it: when its message is answered, replaced by the next
	 * message to the player, or its journey ends. Identica closes it only after the next message's parts are shown,
	 * so a part that shows again, such as a dialog asking again, replaces itself; the close takes down only what this
	 * rendering showed.
	 *
	 * @param close takes the part down
	 * @return the rendering
	 */
	public static @NotNull PartRendering shown(@NotNull Close close) {
		return new PartRendering(true, close);
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
