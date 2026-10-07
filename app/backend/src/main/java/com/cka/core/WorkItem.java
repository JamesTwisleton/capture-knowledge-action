package com.cka.core;

/**
 * The core's generic work item. No provider's own concept reaches past the provider layer:
 * each work item provider's {@code WorkItemMapper} converts to and from this (PRD 7.9).
 *
 * @param id               the provider's identifier for the item, for example "142"
 * @param title            its title, which mention detection scores against
 * @param description      its body text, or {@code null}
 * @param type             the generic type the core reasons about
 * @param providerTypeName the provider's own label for the type, for example "Epic" —
 *                         kept distinct from {@code type}, never derived from it
 * @param parentId         the id of the parent item, or {@code null} for a top-level item;
 *                         this is what makes work items nestable (Jira epics, Azure DevOps
 *                         hierarchy, GitHub sub-issues)
 */
public record WorkItem(
        String id,
        String title,
        String description,
        WorkItemType type,
        String providerTypeName,
        String parentId) {
}
