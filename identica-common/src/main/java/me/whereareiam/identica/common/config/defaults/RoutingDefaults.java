package me.whereareiam.identica.common.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.model.config.Routing;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;

import java.util.HashMap;

@Singleton
public class RoutingDefaults implements DefaultsProvider<Routing> {
	@Override
	public Routing supply(Routing routing) {
		Routing.Defaults defaults = new Routing.Defaults();

		Routing.Target step = Routing.Target.step();
		step.setTarget("auth");
		step.setAttempts(RoutingAttemptPolicy.defaultStep());

		Routing.Target complete = Routing.Target.complete();
		complete.setTarget("survival");
		complete.setForcedHosts(false);
		complete.setAttempts(RoutingAttemptPolicy.defaultCompletion());

		defaults.setStep(step);
		defaults.setComplete(complete);
		routing.setDefaults(defaults);
		routing.setScenarios(new HashMap<>());
		return routing;
	}
}
