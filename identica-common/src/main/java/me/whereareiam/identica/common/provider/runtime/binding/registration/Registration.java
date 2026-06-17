package me.whereareiam.identica.common.provider.runtime.binding.registration;

import org.jetbrains.annotations.NotNull;

public interface Registration extends AutoCloseable {
	Registration NOOP = () -> {};

	@Override
	void close();

	static @NotNull Registration noop() {
		return NOOP;
	}
}
