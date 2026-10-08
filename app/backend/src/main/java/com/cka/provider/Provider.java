package com.cka.provider;

/**
 * What every provider interface has in common: a connection check. It is a required
 * operation, not an optional extra — the front end's provider status view and the setup
 * wizard's connection tester are built on it (PRD 7.2, 10).
 */
public interface Provider {

    /** Checks the provider can reach whatever it depends on. Must not throw; report instead. */
    ProviderHealth checkHealth();
}
