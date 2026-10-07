package com.cka.core;

/**
 * Which provider and model produced something. Every summary, mention and action carries
 * one, because the audit trail must say who decided what (PRD 9.3).
 *
 * @param provider the provider implementation's name, for example "jev"
 * @param model    the model it used, for example "claude-sonnet-5-5"
 */
public record Provenance(String provider, String model) {
}
