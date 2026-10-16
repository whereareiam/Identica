package me.whereareiam.identica.addon.dialogs.platform.velocity.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTNumber;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientCustomClickAction;
import me.whereareiam.identica.addon.dialogs.platform.velocity.checkpoint.LoadingScreenCheckpoint;
import me.whereareiam.identica.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers the buttons a player presses on the loading screen, where the client runs no commands: a button sends a
 * custom click with its index and the inputs, and the proxy runs that button's command as the player, with each
 * {@code $(key)} filled from the inputs. Only the commands of the dialog the player was shown can run this way.
 */
public final class LoadingScreenAnswerListener implements PacketListener {
	/**
	 * ID of the custom click the loading screen's command buttons send.
	 */
	public static final String ANSWER_CLICK = "identica:dialogs/answer";
	/**
	 * Key of the button's index in the click's payload.
	 */
	public static final String ACTION = "action";

	private static final Pattern INPUT = Pattern.compile("\\$\\(([a-zA-Z0-9_]+)\\)");

	private final LoadingScreenCheckpoint checkpoint;
	private final Map<UUID, List<String>> commands = new ConcurrentHashMap<>();

	public LoadingScreenAnswerListener(@NotNull LoadingScreenCheckpoint checkpoint) {
		this.checkpoint = checkpoint;
	}

	/**
	 * Remembers the command lines of the dialog a held player is shown, by button index.
	 *
	 * @param connectionUniqueId the player's connection
	 * @param lines the command line of each button, or {@code null} for a button that runs none
	 */
	public void shown(@NotNull UUID connectionUniqueId, @NotNull List<@Nullable String> lines) {
		commands.put(connectionUniqueId, Collections.unmodifiableList(new ArrayList<>(lines)));
	}

	/**
	 * Forgets the commands of a player who is no longer on the loading screen.
	 *
	 * @param connectionUniqueId the player's connection
	 */
	public void forget(@NotNull UUID connectionUniqueId) {
		commands.remove(connectionUniqueId);
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (event.getPacketType() != PacketType.Configuration.Client.CUSTOM_CLICK_ACTION) return;

		WrapperConfigClientCustomClickAction click = new WrapperConfigClientCustomClickAction(event);
		if (!click.getId().toString().equals(ANSWER_CLICK)) return;

		event.setCancelled(true);
		UUID connectionUniqueId = event.getUser().getUUID();
		if (connectionUniqueId == null || !(click.getPayload() instanceof NBTCompound payload)) return;

		String command = command(connectionUniqueId, payload);
		if (command == null) return;

		checkpoint.held(connectionUniqueId).ifPresent(stop -> stop.answer(command).whenComplete((ran, failure) -> {
			if (failure != null) Logger.severe(failure, "Could not run a loading screen answer");
		}));
	}

	private @Nullable String command(UUID connectionUniqueId, NBTCompound payload) {
		List<String> lines = commands.get(connectionUniqueId);
		Number index = payload.getNumberTagValueOrNull(ACTION);
		if (lines == null || index == null || index.intValue() < 0 || index.intValue() >= lines.size()) return null;

		String line = lines.get(index.intValue());
		return line == null ? null : fill(line, payload);
	}

	private static String fill(String line, NBTCompound payload) {
		Matcher matcher = INPUT.matcher(line);
		StringBuilder filled = new StringBuilder();
		while (matcher.find())
			matcher.appendReplacement(filled, Matcher.quoteReplacement(value(payload.getTagOrNull(matcher.group(1)))));
		matcher.appendTail(filled);
		return filled.toString();
	}

	private static String value(@Nullable NBT tag) {
		if (tag instanceof NBTString text) return text.getValue();
		if (tag instanceof NBTNumber number) return String.valueOf(number.getAsNumber());
		return "";
	}
}
