package me.whereareiam.identica.addon.dialogs;

import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.addon.dialogs.config.DialogsFiles;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.presentation.DialogPresenter;
import me.whereareiam.identica.addon.dialogs.presentation.DialogsCheckpoint;
import me.whereareiam.identica.addon.dialogs.presentation.ServerJoinListener;
import me.whereareiam.identica.addon.dialogs.trigger.StepWaitingTrigger;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerRegistry;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Starts and stops the addon on a proxy, the same way on every platform: once Identica is ready, it reads the
 * files, starts the platform and the display it picks, follows Identica's server joins and the {@code step-waiting}
 * trigger, registers its checkpoint for the loading screen, and registers {@link Dialogs}.
 */
public final class DialogsAddon {
	private final IdenticaAPI identica;
	private final DialogsFiles files;
	private final TriggerRegistry triggers;
	private final ServerJoinListener joins;
	private final DialogsCheckpoint checkpoint;

	private DialogsAddon(
			IdenticaAPI identica,
			DialogsFiles files,
			TriggerRegistry triggers,
			ServerJoinListener joins,
			DialogsCheckpoint checkpoint
	) {
		this.identica = identica;
		this.files = files;
		this.triggers = triggers;
		this.joins = joins;
		this.checkpoint = checkpoint;
	}

	/**
	 * Starts the addon.
	 *
	 * @param identica Identica, once it is ready
	 * @param platform the proxy's dialogs
	 * @param folder the addon's data folder
	 * @return the running addon, to stop it with
	 */
	public static @NotNull DialogsAddon start(@NotNull IdenticaAPI identica, @NotNull DialogPlatform platform, @NotNull Path folder) {
		DialogsFiles files = new DialogsFiles(identica.getConfigura(), folder);
		identica.getReloadables().register(files);

		DialogDisplay display = platform.display();
		DialogPresenter presenter = new DialogPresenter(platform, display, identica.getIdentityService(), files::rules, files::dialogs);
		platform.start(presenter);
		display.start(presenter);
		ServerJoinListener joins = new ServerJoinListener(presenter);
		identica.getEventManager().register(joins);
		TriggerRegistry triggers = new TriggerRegistry(presenter);
		triggers.register(new StepWaitingTrigger(identica.getEventManager()));

		DialogsCheckpoint checkpoint = new DialogsCheckpoint(platform, presenter, files::loadingScreen);
		identica.getCheckpoints().register(checkpoint);
		Dialogs.register(new DialogsService(files, presenter, triggers));

		return new DialogsAddon(identica, files, triggers, joins, checkpoint);
	}

	/**
	 * Stops the addon: its checkpoint takes no more players, its triggers stop, it no longer follows server joins, its
	 * files are no longer reloaded, and {@link Dialogs} is unregistered.
	 */
	public void stop() {
		identica.getCheckpoints().unregister(checkpoint);
		Dialogs.unregister();
		triggers.stopAll();
		identica.getEventManager().unregister(joins);
		identica.getReloadables().unregister(files);
	}
}
