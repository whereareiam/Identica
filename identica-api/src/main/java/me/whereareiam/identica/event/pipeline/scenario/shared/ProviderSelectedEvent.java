package me.whereareiam.identica.event.pipeline.scenario.shared;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.model.provider.InternalProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a journey starts running an option: a provider and one of its methods.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class ProviderSelectedEvent implements Event, SynchronousEvent {
	private final @NotNull ScenarioContext context;
	private final @NotNull InternalProvider provider;
	/**
	 * Name of the provider method that runs, or {@code null} when the provider runs without methods.
	 */
	private final @Nullable String method;
}
