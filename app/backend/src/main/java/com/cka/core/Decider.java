package com.cka.core;

/**
 * Who decided something: CKA itself, or a named human (PRD 7.8, 9.3).
 *
 * <p>One type rather than a string, because the two halves only make sense together. A human
 * decision with nobody's name against it is a decision nobody can be held to — and it is
 * precisely the human ones the trust ramp counts (PRD 9.2) and the front end shows back.
 *
 * @param kind whether the system or a person decided
 * @param name who, for a human; {@code null} for the system, which has no name
 */
public record Decider(Kind kind, String name) {

    public enum Kind {
        SYSTEM,
        HUMAN
    }

    private static final Decider SYSTEM = new Decider(Kind.SYSTEM, null);

    public Decider {
        if (kind == Kind.HUMAN && name == null) {
            throw new IllegalArgumentException("a human decider needs a name");
        }
        if (kind == Kind.SYSTEM && name != null) {
            throw new IllegalArgumentException("the system decider has no name, but was given \"" + name + "\"");
        }
    }

    /** CKA decided, with no human involved — an automatic comment, or any pipeline step. */
    public static Decider system() {
        return SYSTEM;
    }

    public static Decider human(String name) {
        return new Decider(Kind.HUMAN, name);
    }
}
