package com.aijobradar.applications.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aijobradar.applications.application.ApplicationStateMachine.State;
import org.junit.jupiter.api.Test;

class ApplicationStateMachineTest {
  private final ApplicationStateMachine states = new ApplicationStateMachine();

  @Test
  void supportsManualAppliedJourneyAndConfirmedReversalTarget() {
    states.requireTransition(State.SAVED, State.OPENED);
    states.requireTransition(State.OPENED, State.APPLIED);
    states.requireTransition(State.APPLIED, State.OPENED);
    states.requireTransition(State.APPLIED, State.INTERVIEW);
    states.requireTransition(State.INTERVIEW, State.OFFER);
  }

  @Test
  void rejectsSkippingFromSavedToOfferOrReopeningTerminalStates() {
    assertThatThrownBy(() -> states.requireTransition(State.SAVED, State.OFFER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> states.requireTransition(State.REJECTED, State.OPENED))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
