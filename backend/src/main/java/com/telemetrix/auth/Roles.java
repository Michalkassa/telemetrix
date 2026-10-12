package com.telemetrix.auth;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// Who gets which role (docs/adr/0007): GitHub logins on the configured allowlists
// (telemetrix.security.admins / analysts) are ADMIN or ANALYST; anyone else who signs in is a USER.
// Subjects are "github:<login>"; logins compare without case, as GitHub's do.
public class Roles {

	private static final String GITHUB = "github:";

	private final Set<String> admins;

	private final Set<String> analysts;

	public Roles(List<String> admins, List<String> analysts) {
		this.admins = lower(admins);
		this.analysts = lower(analysts);
	}

	public Role roleOf(String subject) {
		if (subject == null || !subject.startsWith(GITHUB)) {
			return Role.USER;
		}
		String login = subject.substring(GITHUB.length()).toLowerCase(Locale.ROOT);
		return admins.contains(login) ? Role.ADMIN : analysts.contains(login) ? Role.ANALYST : Role.USER;
	}

	private static Set<String> lower(List<String> logins) {
		return logins == null ? Set.of()
				: logins.stream().map(l -> l.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
	}

}
