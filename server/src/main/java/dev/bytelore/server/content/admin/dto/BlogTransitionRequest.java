package dev.bytelore.server.content.admin.dto;

import dev.bytelore.server.domain.BlogStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The one request shape every lifecycle transition endpoint shares (§5.5.2): {@code submit}, {@code
 * approve}, {@code reject}, {@code publish}, {@code unpublish}.
 *
 * <p>{@code expectedStatus} is required on every transition, so two callers racing the same post
 * can never both succeed silently. {@code reason} is required (10-500 chars) for {@code reject} and
 * {@code unpublish} and optional elsewhere -- a class-level rule the service enforces, since which
 * action is being performed is not something this shared DTO can see. The 10-500 length range
 * itself, though, applies whenever a reason is given at all: {@code @Size} passes a {@code null}
 * value through untouched, so the lower bound only ever bites on a present-but-too-short reason,
 * regardless of which transition it was sent to.
 */
public record BlogTransitionRequest(
    @NotNull BlogStatus expectedStatus, @Size(min = 10, max = 500) String reason) {}
