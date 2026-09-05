package me.whereareiam.identica.trait.authoritative.username.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when authoritative username persistence stores a username change.
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
public class UsernameChangedEvent implements Event, SynchronousEvent {
	private final @NotNull UUID accountUniqueId;
	private final @NotNull String oldUsername;
	private final @NotNull String newUsername;
	private final @Nullable String providerId;
}
