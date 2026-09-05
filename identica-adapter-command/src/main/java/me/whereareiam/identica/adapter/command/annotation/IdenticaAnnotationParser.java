package me.whereareiam.identica.adapter.command.annotation;

import io.leangen.geantyref.GenericTypeReflector;
import io.leangen.geantyref.TypeToken;
import lombok.RequiredArgsConstructor;
import me.whereareiam.commandant.CommandantKeys;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.annotation.Description;
import me.whereareiam.identica.annotation.Parser;
import me.whereareiam.identica.annotation.Permission;
import me.whereareiam.identica.annotation.Range;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.parser.MethodArgumentParserFactory;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.ParserParameters;
import org.incendo.cloud.parser.ParserRegistry;
import org.incendo.cloud.parser.StandardParameters;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@RequiredArgsConstructor
public class IdenticaAnnotationParser<C> {
	private final AnnotationParser<C> cloudParser;
	private final MethodArgumentParserFactory<C> methodArgumentParserFactory = MethodArgumentParserFactory.defaultFactory();
	private final Set<String> registeredParserNames = new HashSet<>();

	public static <C> IdenticaAnnotationParser<C> create(
			@NotNull CommandManager<C> commandManager,
			@NotNull Class<C> senderType
	) {
		AnnotationParser<C> cloudParser = new AnnotationParser<>(
				new RecordingCommandManager<>(commandManager),
				senderType
		);

		IdenticaCommandExtractor commandExtractor = new IdenticaCommandExtractor(cloudParser);
		ArgumentExtractor argumentExtractor = new ArgumentExtractor(cloudParser);

		cloudParser.commandExtractor(commandExtractor);
		cloudParser.argumentExtractor(argumentExtractor);

		IdenticaAnnotationParser<C> parser = new IdenticaAnnotationParser<>(cloudParser);

		cloudParser.registerBuilderModifier(
				Definition.class,
				(annotation, builder) -> builder.meta(CommandantKeys.DEFINITION_ID, annotation.value())
		);

		cloudParser.registerBuilderModifier(
				Description.class,
				(annotation, builder) -> builder.commandDescription(
						org.incendo.cloud.description.CommandDescription.commandDescription(annotation.value())
				)
		);

		cloudParser.registerBuilderModifier(
				Permission.class,
				(annotation, builder) -> {
					String[] permissions = annotation.value();
					if (permissions.length == 1) return builder.permission(permissions[0]);

					if (permissions.length > 1)
						return builder.permission(
								annotation.mode() == Permission.Mode.ANY_OF
										? org.incendo.cloud.permission.Permission.anyOf(
										Arrays.stream(permissions)
												.map(org.incendo.cloud.permission.Permission::permission)
												.toArray(org.incendo.cloud.permission.Permission[]::new)
										)
										: org.incendo.cloud.permission.Permission.allOf(
										Arrays.stream(permissions)
												.map(org.incendo.cloud.permission.Permission::permission)
												.toArray(org.incendo.cloud.permission.Permission[]::new)
										)
						);

					return builder;
				}
		);

		commandManager.parserRegistry().registerAnnotationMapper(Range.class, new RangeMapper());

		return parser;
	}

	public @NotNull Collection<@NotNull Command<C>> parse(@NotNull Object @NotNull... instances) {
		registerCommandantParsers(instances);
		return cloudParser.parse(instances);
	}

	private void registerCommandantParsers(@NotNull Object @NotNull... instances) {
		CommandManager<C> commandManager = cloudParser.manager();
		for (Object instance : instances) {
			for (Method method : instance.getClass().getMethods()) {
				Parser parser = method.getAnnotation(Parser.class);
				if (parser == null) continue;

				String suggestions = cloudParser.processString(parser.suggestions());
				SuggestionProvider<C> suggestionProvider;
				if (suggestions.isEmpty()) {
					suggestionProvider = SuggestionProvider.noSuggestions();
				} else {
					suggestionProvider = commandManager.parserRegistry()
							.getSuggestionProvider(suggestions)
							.orElseThrow(() -> new NullPointerException(
									String.format("Cannot find the suggestion provider with name '%s'", suggestions)
							));
				}

				ParserDescriptor<C, ?> parserDescriptor = methodArgumentParserFactory.createArgumentParser(
						suggestionProvider,
						instance,
						method,
						commandManager.parameterInjectorRegistry()
				);

				String name = cloudParser.processString(parser.name());
				if (name.isEmpty()) {
					commandManager.parserRegistry().registerParser(parserDescriptor);
				} else if (registeredParserNames.add(name)) {
					commandManager.parserRegistry().registerNamedParser(name, parserDescriptor);
				}
			}
		}
	}

	private static final class RecordingCommandManager<C> extends CommandManager<C> {
		private final CommandManager<C> realManager;

		RecordingCommandManager(@NotNull CommandManager<C> realManager) {
			super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
			this.realManager = realManager;
		}

		@Override
		public @NotNull CommandManager<C> command(@NotNull Command<? extends C> command) {
			// AnnotationParser returns the parsed commands itself. Keeping a second
			// tree here would retain handlers after their lifecycle owner shuts down.
			return this;
		}

		@Override
		public boolean hasPermission(@NotNull C sender, @NotNull String permission) {
			return true;
		}

		@Override
		public @NotNull ParserRegistry<C> parserRegistry() {
			return realManager.parserRegistry();
		}
	}

	private static final class RangeMapper implements ParserRegistry.AnnotationMapper<Range> {
		private static final Map<Class<?>, Class<?>> PRIMITIVE_MAPPINGS = Map.of(
				byte.class, Byte.class,
				short.class, Short.class,
				int.class, Integer.class,
				long.class, Long.class,
				float.class, Float.class,
				double.class, Double.class
		);
		private static final Map<Class<?>, Function<String, Number>> PARSERS = Map.of(
				Byte.class, Byte::valueOf,
				Short.class, Short::valueOf,
				Integer.class, Integer::valueOf,
				Long.class, Long::valueOf,
				Float.class, Float::valueOf,
				Double.class, Double::valueOf
		);

		@Override
		public @NonNull ParserParameters mapAnnotation(@NonNull Range range, @NonNull TypeToken<?> type) {
			Class<?> raw = GenericTypeReflector.erase(type.getType());
			Class<?> clazz = raw.isPrimitive() ? PRIMITIVE_MAPPINGS.get(raw) : raw;
			if (clazz == null) return ParserParameters.empty();
			if (!Number.class.isAssignableFrom(clazz)) return ParserParameters.empty();

			Function<String, Number> parser = PARSERS.get(clazz);
			if (parser == null) return ParserParameters.empty();

			Number min = parseOptional(range.min(), parser);
			Number max = parseOptional(range.max(), parser);
			if (min == null && max == null) return ParserParameters.empty();

			ParserParameters parameters = new ParserParameters();
			if (min != null) parameters.store(StandardParameters.RANGE_MIN, min);
			if (max != null) parameters.store(StandardParameters.RANGE_MAX, max);

			return parameters;
		}

		private static Number parseOptional(String value, Function<String, Number> parser) {
			if (value == null || value.isEmpty()) return null;
			return parser.apply(value);
		}
	}
}
