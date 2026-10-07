package com.cka.provider.llm;

import com.cka.core.CapturedContent;
import com.cka.core.Summary;
import com.cka.provider.Provider;

/**
 * Summarises captured content, using the prompt for its content type (PRD 8.2). That is its
 * whole job in the pipeline: it does not extract work item ids, and it does not write to the
 * knowledge provider.
 */
public interface LlmProvider extends Provider {

    Summary summarise(CapturedContent content);
}
