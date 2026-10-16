package me.whereareiam.identica.addon.dialogs;

import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.addon.dialogs.config.DialogsFiles;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.presentation.DialogPresenter;
import me.whereareiam.identica.addon.dialogs.trigger.StepWaitingTrigger;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerRegistry;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Starts and stops the addon on a proxy, the same way on every platform: once Identica is ready, it reads the
 * files, starts the platform and then the {@code step-waiting} trigger, and registers {@link Dialogs}.
 */
public final class DialogsAddon {
	private final IdenticaAPI identica;
	private final DialogsFiles files;
	private final TriggerRegistry triggers;

	private DialogsAddon(IdenticaAPI identica, DialogsFiles files, TriggerRegistry triggers) {
		this.identica = identica;
		this.files = files;
		this.triggers = triggers;
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

		DialogPresenter presenter = new DialogPresenter(platform, identica.getIdentityService(), identica.getCommandService(), files::rules,
				files::loadingScreen, files::dialogs);
		platform.start(presenter);
		TriggerRegistry triggers = new TriggerRegistry(presenter);
		triggers.register(new StepWaitingTrigger(identica.getEventManager()));

		Dialogs.register(new DialogsService(files, presenter, triggers));
		return new DialogsAddon(identica, files, triggers);
	}

	/**
	 * Stops the addon: its triggers stop, its files are no longer reloaded, and {@link Dialogs} is unregistered.
	 */
	public void stop() {
		Dialogs.unregister();
		triggers.stopAll();
		identica.getReloadables().unregister(files);
	}
}
