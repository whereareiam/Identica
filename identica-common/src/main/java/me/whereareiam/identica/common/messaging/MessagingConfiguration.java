package me.whereareiam.identica.common.messaging;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.OptionalBinder;
import me.whereareiam.identica.message.LoadingScreen;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.service.DeliveryStore;

public class MessagingConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(DeliveryStore.class).to(DefaultDeliveryStore.class).asEagerSingleton();
		bind(DeliveryService.class).to(DefaultDeliveryService.class).asEagerSingleton();
		bind(DeliveryLifecycle.class).asEagerSingleton();
		bind(MessageService.class).to(DefaultMessageService.class).asEagerSingleton();
		OptionalBinder.newOptionalBinder(binder(), LoadingScreen.class).setDefault().to(NoLoadingScreen.class);
	}
}
