package me.whereareiam.identica.addon.dialogs.display.bungeecord;

import com.google.gson.JsonElement;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.addon.dialogs.presentation.DialogClicks;
import net.md_5.bungee.api.event.CustomClickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads the clicks the addon's buttons send, such as a command button or the one that switches to chat, keeps them
 * from the server and reports them with the values they carry.
 */
public final class DialogClickListener implements Listener {
	private final DialogPlatform.Listener listener;

	/**
	 * Creates the listener.
	 *
	 * @param listener where the listener reports
	 */
	public DialogClickListener(@NotNull DialogPlatform.Listener listener) {
		this.listener = listener;
	}

	@EventHandler
	public void onCustomClick(CustomClickEvent event) {
		if (!event.getId().startsWith(DialogClicks.PREFIX)) return;

		event.setCancelled(true);
		listener.clicked(event.getPlayer().getUniqueId(), event.getId(), values(event.getData()));
	}

	private static Map<String, String> values(@Nullable JsonElement data) {
		Map<String, String> values = new HashMap<>();
		if (data == null || !data.isJsonObject()) return values;

		data.getAsJsonObject().entrySet().forEach(entry -> {
			if (entry.getValue().isJsonPrimitive()) values.put(entry.getKey(), entry.getValue().getAsString());
		});
		return values;
	}
}
