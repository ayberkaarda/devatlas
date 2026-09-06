package dev.bytelore.server.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Objects;

/**
 * Password change. There is no password reset flow: reset needs outbound email, which is a real
 * external dependency and not something to smuggle in as a footnote.
 *
 * @param currentPassword the secret in force, proving the caller is at the keyboard
 * @param newPassword its replacement, under the same length rule
 * @param refreshToken the caller's own refresh token, when it is not carried in a cookie -- see
 *     {@link dev.bytelore.server.auth.AuthController#changePassword}. Optional and unvalidated,
 *     exactly like the token on {@link RefreshRequest} and {@link LogoutRequest}: a caller using
 *     {@code BODY} token delivery (§3.8) has no cookie at all, and without this field the server
 *     has no way to tell "the family the caller is currently using" from "some other family", so a
 *     password change would revoke the very session that requested it.
 */
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 12, max = 128) String newPassword,
    String refreshToken) {

  /** Changing a password to itself is a no-op the caller almost certainly did not intend. */
  @AssertTrue(message = "new_password must differ from current_password")
  public boolean isNewPasswordDistinct() {
    return currentPassword == null
        || newPassword == null
        || !Objects.equals(currentPassword, newPassword);
  }
}
