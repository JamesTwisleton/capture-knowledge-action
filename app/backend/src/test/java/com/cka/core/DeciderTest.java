package com.cka.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The audit record says who decided — "system or named human" (PRD 7.8). The pairing of the two
 * components is the whole type, so it is the pairing these tests pin down: an anonymous human is
 * a decision nobody can be held to, and a named system is a human record with the wrong label.
 */
@DisplayName("A decider is the system, or a human with a name")
class DeciderTest {

    @Test
    @DisplayName("The system decider carries no name")
    void theSystemDeciderCarriesNoName() {
        assertThat(Decider.system().kind()).isEqualTo(Decider.Kind.SYSTEM);
        assertThat(Decider.system().name()).isNull();
    }

    @Test
    @DisplayName("A human decider is named")
    void aHumanDeciderIsNamed() {
        var james = Decider.human("james");

        assertThat(james.kind()).isEqualTo(Decider.Kind.HUMAN);
        assertThat(james.name()).isEqualTo("james");
    }

    @Test
    @DisplayName("A human decider without a name is rejected")
    void aHumanDeciderWithoutANameIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Decider(Decider.Kind.HUMAN, null))
                .withMessageContaining("name");
    }

    @Test
    @DisplayName("A named system decider is rejected")
    void aNamedSystemDeciderIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Decider(Decider.Kind.SYSTEM, "james"))
                .withMessageContaining("system");
    }
}
