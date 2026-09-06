package dev.bytelore.server.auth.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Preference update. Every field is optional; an empty object is a no-op that returns current
 * state.
 *
 * <p>There is no version field and no optimistic locking. A preference has one writer in practice,
 * the loss window is a theme toggle, and a conflict response on a theme toggle is a worse outcome
 * than the lost update it would prevent.
 *
 * <p>Email and role are not patchable here.
 *
 * @param locale new interface locale, or null to leave it alone
 * @param theme new appearance preference, or null to leave it alone
 */
public record UpdateMeRequest(
    String locale, @Pattern(regexp = "^(LIGHT|DARK|SYSTEM)$") String theme) {}
