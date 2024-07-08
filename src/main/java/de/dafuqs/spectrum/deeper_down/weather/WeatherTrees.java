package de.dafuqs.spectrum.deeper_down.weather;

import de.dafuqs.spectrum.deeper_down.*;
import de.dafuqs.spectrum.helpers.*;
import de.dafuqs.spectrum.registries.*;
import net.minecraft.util.collection.*;

import java.util.*;
import java.util.function.*;

public final class WeatherTrees {
	
	private static final Map<WeatherState, Node> FLOW_TRANSITIONS = new HashMap<>();
	private static final Map<WeatherState, Node> MONSOON_TRANSITIONS = new HashMap<>();
	private static final Map<WeatherState, Node> STEAMY_TRANSITIONS = new HashMap<>();
	
	public static void initFlow() {
		FLOW_TRANSITIONS.put(SpectrumWeatherStates.PLAIN_WEATHER, new Node() {
			@Override
			boolean canTransition(long curDuration, float aquiferFill, Season.Period period) {
				return curDuration > period.minDefaultWeatherDuration;
			}
			
			@Override
			WeightedPool<Branch> getPotentialTransitions() {
				return WeightedPool.of(
						new Branch(SpectrumWeatherStates.SHOWER, 4, (aquiferFill, period) -> aquiferFill > 50)
				);
			}
		});
	}
	
	private abstract static class Node {
		
		/**
		 * Holy shit trans
		 */
		abstract boolean canTransition(long curDuration, float aquiferFill, Season.Period period);
		
		abstract WeightedPool<Branch> getPotentialTransitions();
	}
	
	private record Branch(WeatherState state, Weight weight,
						  BiPredicate<Long, Season.Period> startPredicate) implements Weighted {
		
		public Branch(WeatherState state, int weight, BiPredicate<Long, Season.Period> startPredicate) {
			this(state, Weight.of(weight), startPredicate);
		}
		
		@Override
		public Weight getWeight() {
			return weight;
		}
	}
}