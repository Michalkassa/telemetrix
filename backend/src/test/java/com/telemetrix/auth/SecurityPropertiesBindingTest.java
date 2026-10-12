package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

// docker-compose.yml configures security through environment variables: they bind as the README says.
class SecurityPropertiesBindingTest {

	@Test
	void composesEnvironmentVariablesBind() {
		var environment = new StandardEnvironment();
		// as the real environment variables: Spring maps names only for the source called systemEnvironment
		environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
				new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(
				"TELEMETRIX_SECURITY_APIKEYS_0_NAME", "seed",
				"TELEMETRIX_SECURITY_APIKEYS_0_ROLE", "ADMIN",
				"TELEMETRIX_SECURITY_APIKEYS_0_SHA256", "3269123ed71ec32853e5e11324d321a4116cdfca4a5591f3ae855dc2041d766d",
				"TELEMETRIX_SECURITY_ADMINS", "octo-admin,someone-else",
				"TELEMETRIX_SECURITY_RATELIMIT_ANONYMOUSPERMINUTE", "60")));

		var properties = new Binder(ConfigurationPropertySources.get(environment))
				.bindOrCreate("telemetrix.security", SecurityProperties.class);

		assertThat(properties.apiKeys()).singleElement().satisfies(key -> {
			assertThat(key.name()).isEqualTo("seed");
			assertThat(key.role()).isEqualTo(Role.ADMIN);
			assertThat(key.sha256()).startsWith("3269123e");
		});
		assertThat(properties.admins()).containsExactly("octo-admin", "someone-else");
		assertThat(properties.rateLimit().anonymousPerMinute()).isEqualTo(60);
		assertThat(properties.rateLimit().authenticatedPerMinute()).isEqualTo(1200);
		assertThat(properties.jwt().accessTtl()).hasMinutes(15);
	}

}
