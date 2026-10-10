package me.whereareiam.identica.platform.common;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Types;
import me.whereareiam.identica.platform.common.adapter.PlatformHandshakeDecisionAdapter;
import me.whereareiam.identica.platform.common.adapter.PlatformLoginDecisionAdapter;
import me.whereareiam.identica.platform.common.adapter.PlatformProfileAdapter;
import me.whereareiam.identica.platform.common.adapter.PlatformResumeDecisionAdapter;
import me.whereareiam.identica.handshake.HandshakeApplierRegistry;
import me.whereareiam.identica.handshake.HandshakeContext;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeApplierContributor;
import me.whereareiam.identica.platform.adapter.PlatformHandshakeApplierRegistry;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Base module of a platform runtime. Each abstract method names one adapter a platform has to provide, so a
 * platform that leaves one out does not compile.
 *
 * <p>An adapter is bound under the contract it implements, with the event or handshake context type its class
 * declares: an adapter that implements {@code PlatformLoginDecisionAdapter<LoginEvent>} is injected as exactly
 * that type.</p>
 *
 * @param <C> handshake context type of the platform
 */
public abstract class PlatformConfiguration<C extends HandshakeContext> extends AbstractModule {
	@Override
	protected final void configure() {
		bind(PlatformDeliveryAdapter.class).to(delivery());
		bindAdapter(PlatformHandshakeDecisionAdapter.class, handshakeDecision());
		bindAdapter(PlatformLoginDecisionAdapter.class, loginDecision());
		bindAdapter(PlatformResumeDecisionAdapter.class, resumeDecision());
		bindAdapter(PlatformProfileAdapter.class, profile());
		bindHandshakeApplierRegistry();

		configurePlatform();
	}

	/**
	 * Returns the adapter that coordinates when messages can reach a joining player.
	 */
	protected abstract @NotNull Class<? extends PlatformDeliveryAdapter> delivery();

	/**
	 * Returns the registry that applies handshake instructions to the platform's handshake context.
	 */
	protected abstract @NotNull Class<? extends PlatformHandshakeApplierRegistry<C>> handshakeApplierRegistry();

	/**
	 * Returns the adapter that processes handshake-time connection decisions.
	 */
	protected abstract @NotNull Class<? extends PlatformHandshakeDecisionAdapter<?>> handshakeDecision();

	/**
	 * Returns the adapter that processes login-time connection decisions.
	 */
	protected abstract @NotNull Class<? extends PlatformLoginDecisionAdapter<?>> loginDecision();

	/**
	 * Returns the adapter that processes first-connect resume decisions.
	 */
	protected abstract @NotNull Class<? extends PlatformResumeDecisionAdapter<?>> resumeDecision();

	/**
	 * Returns the adapter that prepares or rewrites the player's profile.
	 */
	protected abstract @NotNull Class<? extends PlatformProfileAdapter<?>> profile();

	/**
	 * Binds everything else the platform provides, such as its proxy, logging, listeners, scheduler and commands.
	 */
	protected abstract void configurePlatform();

	/**
	 * The registry also answers as the plain handshake applier registry, and providers contribute to it through a
	 * set that is empty until one does.
	 */
	private void bindHandshakeApplierRegistry() {
		Class<? extends PlatformHandshakeApplierRegistry<C>> registry = handshakeApplierRegistry();
		bindAdapter(PlatformHandshakeApplierRegistry.class, registry);
		bindAdapter(HandshakeApplierRegistry.class, registry);

		Type declared = TypeLiteral.get(registry).getSupertype(PlatformHandshakeApplierRegistry.class).getType();
		Type context = ((ParameterizedType) declared).getActualTypeArguments()[0];
		Multibinder.newSetBinder(
				binder(),
				TypeLiteral.get(Types.newParameterizedType(PlatformHandshakeApplierContributor.class, context))
		);
	}

	@SuppressWarnings("unchecked")
	private <T> void bindAdapter(@NotNull Class<T> contract, @NotNull Class<? extends T> adapter) {
		TypeLiteral<T> declared = (TypeLiteral<T>) TypeLiteral.get(adapter).getSupertype(contract);
		bind(declared).to(adapter);
	}
}
