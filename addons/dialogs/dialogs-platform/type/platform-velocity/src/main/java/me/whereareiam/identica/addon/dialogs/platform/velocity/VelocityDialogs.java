package me.whereareiam.identica.addon.dialogs.platform.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.identica.Constants;
import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.addon.dialogs.DialogsAddon;
import me.whereareiam.identica.addon.dialogs.platform.velocity.adapter.VelocityDialogPlatform;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * The dialogs addon on Velocity. It needs Identica and PacketEvents, which sends the dialogs. It starts once both
 * are: in the last phase of the proxy's start, after PacketEvents has loaded, and once Identica is ready.
 */
@Plugin(
		id = "identica-dialogs",
		name = "Identica-Dialogs",
		version = Constants.VERSION,
		description = "Shows Identica's prompts as dialogs",
		authors = {"whereareiam"},
		dependencies = {@Dependency(id = "identica"), @Dependency(id = "packetevents")}
)
public final class VelocityDialogs {
	private final ProxyServer proxy;
	private final Path folder;
	private volatile @Nullable DialogsAddon addon;

	@Inject
	public VelocityDialogs(ProxyServer proxy, @DataDirectory Path folder) {
		this.proxy = proxy;
		this.folder = folder;
	}

	@Subscribe(order = PostOrder.LAST)
	public void onInitialize(ProxyInitializeEvent event) {
		IdenticaAPI.whenReady(this::start);
	}

	private void start(IdenticaAPI identica) {
		addon = DialogsAddon.start(identica, new VelocityDialogPlatform(proxy, this), folder);
	}

	@Subscribe
	public void onShutdown(ProxyShutdownEvent event) {
		DialogsAddon running = addon;
		if (running != null) running.stop();
	}
}
