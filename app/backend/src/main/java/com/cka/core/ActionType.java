package com.cka.core;

/**
 * What an action does to a work item. Trust settings are per action type, never global
 * (PRD 9.2). Only the MVP's types are listed; add one here alongside the work item provider
 * operation that performs it.
 */
public enum ActionType {
    /** Low risk: changes nothing on the item, so needs no trigger phrase. */
    COMMENT,
    /** Mutating: needs the trigger phrase and an explicit item id (PRD 9.1, check 2). */
    CLOSE
}
