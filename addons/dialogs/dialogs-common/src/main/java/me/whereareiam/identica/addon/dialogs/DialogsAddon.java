package me.whereareiam.identica.addon.dialogs;

import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.addon.dialogs.config.DialogsFiles;
import me.whereareiam.identica.addon.dialogs.model.DialogPart;
import me.whereareiam.identica.addon.dialogs.platform.DialogDisplay;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.presentation.DialogChooser;
import me.whereareiam.identica.addon.dialogs.presentation.DialogPartRenderer;
import me.whereareiam.identica.addon.dialogs.presentation.DialogPresenter;
import me.whereareiam.identica.addon.dialogs.presentation.JourneyListener;
import me.whereareiam.identica.addon.dialogs.presentation.RulePresentations;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.ChatPart;
import me.whereareiam.identica.model.message.PartType;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Starts and stops the addon on a proxy, the same way on every platform: once Identica is ready, it reads the
 * files, starts the platform and the display it picks, registers the dialog part type with Identica's message service
 * and presents what the rules name, follows Identica's server joins and resolved scenarios, and registers
 * {@link Dialogs}.
 */
public final class DialogsAddon {
	private final IdenticaAPI identica;
	private final DialogsFiles files;
	private final RulePresentations presentations;
	private final MessageService.Registration partType;
	private final JourneyListener journeys;

	private DialogsAddon(
			IdenticaAPI identica,
			DialogsFiles files,
			RulePresentations presentations,
			MessageService.Registration partType,
			JourneyListener journeys
	) {
		this.identica = identica;
		this.files = files;
		this.presentations = presentations;
		this.partType = partType;
		this.journeys = journeys;
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
		MessageService messages = identica.getMessageService();
		RulePresentations presentations = new RulePresentations(messages);
		DialogsFiles files = new DialogsFiles(identica.getConfigura(), folder, presentations::apply);
		identica.getReloadables().register(files);

		DialogDisplay display = platform.display();
		DialogPresenter presenter = new DialogPresenter(platform, display, identica.getIdentityService(), messages, files::dialogs);
		platform.start(presenter);
		display.start(presenter);
		DialogChooser chooser = new DialogChooser(identica.getPendingScenarioService(), files::rules, files::dialogs);
		MessageService.Registration partType = messages.registerPartType(PartType.<DialogPart>builder()
				.id(DialogPart.TYPE)
				.partClass(DialogPart.class)
				.renderer(new DialogPartRenderer(presenter, chooser, messages))
				.fallback(ChatPart.TYPE)
				.droppedOnServerSwitch(true)
				.build());
		JourneyListener journeys = new JourneyListener(presenter);
		identica.getEventManager().register(journeys);

		Dialogs.register(new DialogsService(files, presenter));
		return new DialogsAddon(identica, files, presentations, partType, journeys);
	}

	/**
	 * Stops the addon: Identica tells players its messages in chat again, the addon no longer follows Identica's
	 * events, its files are no longer reloaded, and {@link Dialogs} is unregistered.
	 */
	public void stop() {
		Dialogs.unregister();
		presentations.remove();
		partType.remove();
		identica.getEventManager().unregister(journeys);
		identica.getReloadables().unregister(files);
	}
}
