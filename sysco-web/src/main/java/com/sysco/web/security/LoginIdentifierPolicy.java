package com.sysco.web.security;

import java.util.Optional;

/**
 * Normalises and validates the value submitted on {@code /login} as {@code username}.
 * Only a single SYSCO login token is accepted: the account {@code users.username} or
 * {@code users.matricule} (case-insensitive match in {@link SyscoUserDetailsService}).
 * <p>
 * Rejects e-mail shaped values and any internal whitespace so users cannot sign in with
 * a display-style full name or an e-mail address on the main login form.
 */
public final class LoginIdentifierPolicy {

    private LoginIdentifierPolicy() {}

    /**
     * @return trimmed key when it is allowed for password login, otherwise empty
     */
    public static Optional<String> parseLoginKey(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.strip();
        if (s.isEmpty()) {
            return Optional.empty();
        }
        if (s.indexOf('@') >= 0) {
            return Optional.empty();
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return Optional.empty();
            }
        }
        return Optional.of(s);
    }
}
