package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Roles come from the configured allowlists of GitHub logins; anyone else who signs in is a USER.
class RolesTest {

	Roles roles = new Roles(List.of("Octo-Admin"), List.of("octo-analyst", "octo-admin"));

	@ParameterizedTest
	@CsvSource({ "github:octo-admin, ADMIN", "github:OCTO-ANALYST, ANALYST", "github:someone, USER",
			"api-key:ci, USER", "octo-admin, USER" })
	void aSubjectsRole(String subject, Role expected) {
		assertThat(roles.roleOf(subject)).isEqualTo(expected);
	}

}
