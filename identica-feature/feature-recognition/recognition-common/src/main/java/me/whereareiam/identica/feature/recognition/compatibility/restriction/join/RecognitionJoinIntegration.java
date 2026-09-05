package me.whereareiam.identica.feature.recognition.compatibility.restriction.join;

import com.google.inject.Injector;
import me.whereareiam.identica.feature.restriction.join.JoinRestrictionType;
import me.whereareiam.identica.feature.restriction.model.RestrictionSignalDescriptor;
import me.whereareiam.identica.feature.restriction.registry.RestrictionSignalRegistry;
import me.whereareiam.identica.feature.restriction.type.RestrictionSignal;
import org.jetbrains.annotations.NotNull;

/**
 * Join integration loaded only when the restriction-join feature is installed.
 */
public final class RecognitionJoinIntegration {
	public static @NotNull Runnable register(@NotNull Injector injector) {
		RestrictionSignalRegistry registry = injector.getInstance(RestrictionSignalRegistry.class);
		RestrictionSignalDescriptor signal = RestrictionSignalDescriptor.builder()
				.restrictionType(JoinRestrictionType.TYPE)
				.signal(RestrictionSignal.of("recognized"))
				.displayName("Recognized")
				.description("Allows recognized reconnects through join restriction.")
				.build();
		registry.register(signal);
		return () -> registry.unregister(signal);
	}
}
