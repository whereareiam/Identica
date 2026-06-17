package me.whereareiam.identica.common.provider.runtime.binding.registration;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public final class CompositeRegistration implements Registration {
	private final List<Registration> registrations = new ArrayList<>();

	@SuppressWarnings("resource")
    public void add(@NotNull Registration registration) {
		if (registration == Registration.noop())
			return;

		registrations.add(registration);
	}

	@Override
	public void close() {
		RuntimeException failure = null;
		ListIterator<Registration> iterator = registrations.listIterator(registrations.size());
		while (iterator.hasPrevious()) {
			try {
				iterator.previous().close();
			} catch (RuntimeException exception) {
				if (failure == null) {
					failure = exception;
				} else {
					failure.addSuppressed(exception);
				}
			}
		}

		registrations.clear();
		if (failure != null)
			throw failure;
	}
}
