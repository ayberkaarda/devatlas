package dev.devatlas.server.domain;

/**
 * Account role.
 *
 * <p>{@code ADMIN} is broader than {@code EDITOR}, which is broader than {@code USER}, but nothing
 * derives a permission from that ordering: every endpoint states the roles it accepts. An ordinal
 * comparison would silently grant a new role everything a lower one had.
 */
public enum Role {
  ADMIN,
  EDITOR,
  USER
}
