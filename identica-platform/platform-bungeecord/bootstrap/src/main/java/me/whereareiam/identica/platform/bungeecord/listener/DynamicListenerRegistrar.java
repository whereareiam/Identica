package me.whereareiam.identica.platform.bungeecord.listener;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.platform.bungeecord.BungeeCordEventPriority;
import me.whereareiam.identica.platform.bungeecord.BungeeCordIdentica;
import me.whereareiam.identica.type.event.EventPriority;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.event.EventBus;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

@Singleton
public class DynamicListenerRegistrar {
	private final @NotNull Provider<Settings> settings;
	private final @NotNull BungeeCordIdentica plugin;
	private final @NotNull EventBus eventBus;
	private final @NotNull Lock eventBusLock;
	private final @NotNull Map<Class<?>, Map<Byte, Map<Object, Method[]>>> handlersByEvent;
	private final @NotNull Object listenersByPlugin;
	private final @NotNull Method listenersByPluginPutMethod;
	private final @NotNull Method bakeHandlersMethod;
	private final @NotNull Map<RegistrationKey, Boolean> registrations = new ConcurrentHashMap<>();

	@Inject
	public DynamicListenerRegistrar(
			@NotNull Provider<Settings> settingsProvider,
			@NotNull BungeeCordIdentica plugin
	) {
		this.settings = settingsProvider;
		this.plugin = plugin;
		try {
			PluginManager pluginManager = plugin.getProxy().getPluginManager();
			eventBus = readField(PluginManager.class, "eventBus", pluginManager);
			eventBusLock = readField(EventBus.class, "lock", eventBus);
			handlersByEvent = readField(EventBus.class, "byListenerAndPriority", eventBus);
			listenersByPlugin = readField(PluginManager.class, "listenersByPlugin", pluginManager);
			listenersByPluginPutMethod = listenersByPlugin.getClass().getMethod("put", Object.class, Object.class);
			listenersByPluginPutMethod.setAccessible(true);
			bakeHandlersMethod = EventBus.class.getDeclaredMethod("bakeHandlers", Class.class);
			bakeHandlersMethod.setAccessible(true);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Failed to initialize Bungee dynamic listener registrar", exception);
		}
	}

	public <T> void register(@NotNull Class<T> eventClass, @NotNull DynamicListener<T> listener) {
		if (shouldSkip(eventClass)) return;

		Logger.debug("Registering listener for event " + eventClass.getName());
		registrations.computeIfAbsent(new RegistrationKey(listener, eventClass), ignored -> {
			registerReflective(eventClass, listener);
			return Boolean.TRUE;
		});
	}

	private boolean shouldSkip(@NotNull Class<?> eventClass) {
		var registration = settings
				.get()
				.getListeners()
				.getEvents()
				.get(eventClass.getName());

		return registration != null && !registration.isRegister();
	}

	private @NotNull EventPriority determinePriority(@NotNull Class<?> eventClass) {
		Settings.Listeners listeners = settings.get().getListeners();

		if (listeners.getEvents().isEmpty() || listeners.getEvents().get(eventClass.getName()) == null)
			return EventPriority.NORMAL;

		EventPriority priority = listeners.getEvents().get(eventClass.getName()).getPriority();
		if (priority == null) {
			Logger.warn("No priority found for event " + eventClass.getName() + ", using default NORMAL.");
			return EventPriority.NORMAL;
		}

		return priority;
	}

	private <T> void registerReflective(@NotNull Class<T> eventClass, @NotNull DynamicListener<T> listener) {
		Method handler = resolveHandlerMethod(listener, eventClass);
		byte priority = BungeeCordEventPriority.of(determinePriority(eventClass));

		eventBusLock.lock();
		try {
			Map<Byte, Map<Object, Method[]>> handlersByPriority =
					handlersByEvent.computeIfAbsent(eventClass, ignored -> new ConcurrentHashMap<>());
			Map<Object, Method[]> handlersByListener =
					handlersByPriority.computeIfAbsent(priority, ignored -> new ConcurrentHashMap<>());
			handlersByListener.put(listener, new Method[]{handler});
			listenersByPluginPutMethod.invoke(listenersByPlugin, plugin, listener);
			bakeHandlersMethod.invoke(eventBus, eventClass);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Failed to register Bungee listener for " + eventClass.getName(), exception);
		} finally {
			eventBusLock.unlock();
		}
	}

	private <T> @NotNull Method resolveHandlerMethod(
			@NotNull DynamicListener<T> listener,
			@NotNull Class<T> eventClass
	) {
		try {
			Method method = listener.getClass().getMethod("onEvent", eventClass);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException ignored) {
			for (Method method : listener.getClass().getMethods()) {
				if (!method.getName().equals("onEvent")) continue;
				if (method.isBridge() || method.isSynthetic()) continue;

				Class<?>[] parameters = method.getParameterTypes();
				if (parameters.length != 1 || !parameters[0].equals(eventClass))
					continue;

				method.setAccessible(true);
				return method;
			}
		}

		throw new IllegalStateException(
				"Failed to resolve onEvent method for "
						+ listener.getClass().getName()
						+ " and event "
						+ eventClass.getName()
		);
	}

	@SuppressWarnings("unchecked")
	private static <T> T readField(
			@NotNull Class<?> owner,
			@NotNull String name,
			@NotNull Object instance
	) throws ReflectiveOperationException {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return (T) field.get(instance);
	}

	private record RegistrationKey(
			@NotNull DynamicListener<?> listener,
			@NotNull Class<?> eventClass
	) {
	}
}
