package com.cka.core;

import lombok.Builder;
import lombok.NonNull;

/**
 * Which provider and model produced something. Every summary, mention and action carries
 * one, because the audit trail must say who decided what (PRD 9.3).
 *
 * @param provider the provider implementation's name, for example "jev"
 * @param model    the model it used, for example "claude-sonnet-5-5"
 */
@Builder
public record Provenance(@NonNull String provider, @NonNull String model) {}
