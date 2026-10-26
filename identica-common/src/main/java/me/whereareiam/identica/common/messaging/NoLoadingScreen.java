package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.message.LoadingScreen;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The loading screen of a platform that never holds players there.
 */
final class NoLoadingScreen implements LoadingScreen {
	@Override
	public boolean holds(@NotNull UUID connectionUniqueId) {
		return false;
	}
}
