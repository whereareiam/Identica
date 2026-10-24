package me.whereareiam.identica.event.provider;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.event.base.CancellableEvent;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.pipeline.ScenarioContext;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired to determine whether a provider can handle a context.
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class ProviderEligibilityEvent implements Event, SynchronousEvent, CancellableEvent {
	private final @NotNull ScenarioContext context;
	private final @NotNull InternalProvider provider;
	private boolean cancelled;
}
