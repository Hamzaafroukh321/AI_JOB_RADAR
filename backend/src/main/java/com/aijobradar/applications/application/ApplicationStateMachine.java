package com.aijobradar.applications.application;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStateMachine {
  private static final Map<State, Set<State>> ALLOWED = transitions();

  public void requireTransition(State from, State to) {
    if (from == to) return;
    if (!ALLOWED.getOrDefault(from, Set.of()).contains(to))
      throw new IllegalArgumentException(
          "Application transition is not allowed: " + from + " -> " + to);
  }

  private static Map<State, Set<State>> transitions() {
    Map<State, Set<State>> result = new EnumMap<>(State.class);
    result.put(State.SAVED, EnumSet.of(State.OPENED, State.WITHDRAWN));
    result.put(State.OPENED, EnumSet.of(State.APPLIED, State.WITHDRAWN));
    result.put(
        State.APPLIED, EnumSet.of(State.OPENED, State.INTERVIEW, State.REJECTED, State.WITHDRAWN));
    result.put(State.INTERVIEW, EnumSet.of(State.OFFER, State.REJECTED, State.WITHDRAWN));
    result.put(State.OFFER, EnumSet.of(State.REJECTED, State.WITHDRAWN));
    result.put(State.REJECTED, EnumSet.noneOf(State.class));
    result.put(State.WITHDRAWN, EnumSet.noneOf(State.class));
    return Map.copyOf(result);
  }

  public enum State {
    SAVED,
    OPENED,
    APPLIED,
    INTERVIEW,
    OFFER,
    REJECTED,
    WITHDRAWN
  }
}
