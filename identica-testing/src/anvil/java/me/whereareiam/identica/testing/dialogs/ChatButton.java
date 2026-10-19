package me.whereareiam.identica.testing.dialogs;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;
import me.whereareiam.identica.addon.dialogs.type.ActionType;
import me.whereareiam.identica.testing.environment.Addon;
import me.whereareiam.identica.testing.environment.ProxyConfiguration;
import me.whereareiam.identica.testing.journey.Administrator;

import static me.whereareiam.identica.testing.environment.IdenticaNetwork.PROXY;

/**
 * Adds a button that switches to chat below the form of shipped dialogs, as an administrator does, since the shipped
 * dialogs have none, and reloads Identica so the addon reads them.
 */
final class ChatButton {
	private ChatButton() {
		throw new UnsupportedOperationException("This class cannot be instantiated");
	}

	/**
	 * Adds the button to dialogs of the running proxy and reloads.
	 *
	 * @param anvil running network
	 * @param dialogs IDs of the dialogs, the names of their files
	 */
	static void addTo(ScenarioContext anvil, String... dialogs) {
		for (String dialog : dialogs)
			ProxyConfiguration.editGenerated(anvil, Addon.DIALOGS, "dialogs/" + dialog + ".yml", DialogDocument.class, document -> {
				DialogDocument.Action chat = new DialogDocument.Action();
				chat.setLabel("<gray>Use chat instead");
				chat.setType(ActionType.CHAT);
				document.setExitAction(chat);
			});

		Administrator.at(anvil, PROXY).reloads();
	}
}
