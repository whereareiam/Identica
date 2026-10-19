package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.LoadingScreenRule;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.checkpoint.Checkpoint;
import me.whereareiam.identica.checkpoint.CheckpointStop;
import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

/**
 * Asks players with dialogs before they enter any server: Identica holds the player on the loading screen, the
 * presenter shows the dialogs there, and the player enters the server their journey completes on once it has
 * finished. Where the proxy cannot hold the player on the loading screen, Identica holds them on the step's server,
 * where they see the same dialogs in the server's world. It takes the connections of clients that show dialogs, when
 * the addon's {@code loadingScreen} rule asks for them.
 */
public final class DialogsCheckpoint implements Checkpoint {
	/**
	 * ID of the checkpoint.
	 */
	public static final String ID = "dialogs";

	private final DialogPlatform platform;
	private final DialogPresenter presenter;
	private final Supplier<LoadingScreenRule> rule;

	/**
	 * Creates the checkpoint.
	 *
	 * @param platform the proxy, which tells whether a client shows dialogs
	 * @param presenter shows the held players their dialogs and runs their answers
	 * @param rule the current loading-screen rule
	 */
	public DialogsCheckpoint(@NotNull DialogPlatform platform, @NotNull DialogPresenter presenter, @NotNull Supplier<LoadingScreenRule> rule) {
		this.platform = platform;
		this.presenter = presenter;
		this.rule = rule;
	}

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public @NotNull List<CheckpointPlacement> placements() {
		return List.of(CheckpointPlacement.LOADING_SCREEN, CheckpointPlacement.STEP_SERVER);
	}

	@Override
	public boolean accepts(@NotNull CheckpointRequest request) {
		return platform.supports(request.getConnectionUniqueId()) && RuleMatcher.onLoadingScreen(rule.get(), request);
	}

	@Override
	public void arrived(@NotNull CheckpointStop stop) {
		presenter.held(stop);
	}
}
