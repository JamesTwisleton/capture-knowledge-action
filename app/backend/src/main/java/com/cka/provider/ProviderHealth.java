package com.cka.provider;

/**
 * The result of a provider's connection check.
 *
 * @param up     whether the provider can currently do its job
 * @param detail what was checked, or what is wrong, in words a person can act on
 */
public record ProviderHealth(boolean up, String detail) {

    public static ProviderHealth up(String detail) {
        return new ProviderHealth(true, detail);
    }

    public static ProviderHealth down(String detail) {
        return new ProviderHealth(false, detail);
    }
}
