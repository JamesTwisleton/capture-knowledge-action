package com.cka.core;

import lombok.Builder;
import lombok.NonNull;

/**
 * Where the knowledge provider stored something, so it can be linked to and opened again.
 *
 * @param id       the knowledge provider's own identifier for the page, for example a
 *                 vault-relative file path
 * @param location how to open it, for example a file or web URL
 */
@Builder
public record PageReference(@NonNull String id, @NonNull String location) {}
