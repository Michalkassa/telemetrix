package com.telemetrix.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemetrix.FakeTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

// In production the signing key comes from PEM files, so tokens survive a restart (docs/adr/0007).
class SigningKeyTest {

	@TempDir
	Path dir;

	@Test
	void aKeyFromPemFilesSignsTokensThatAnotherStartStillAccepts() throws Exception {
		var generated = Keys.generateRsa();
		Path pub = pem(dir.resolve("public.pem"), "PUBLIC KEY", generated.getPublic().getEncoded());
		Path priv = pem(dir.resolve("private.pem"), "PRIVATE KEY", generated.getPrivate().getEncoded());
		var jwt = new SecurityProperties.Jwt("telemetrix", Duration.ofMinutes(15), Duration.ofDays(14),
				new FileSystemResource(pub), new FileSystemResource(priv));
		var clock = new FakeTime(Instant.parse("2026-10-12T20:00:00Z"));
		var roles = new Roles(List.of(), List.of());

		var before = new TokenService(SecurityConfig.keys(jwt), "telemetrix", Duration.ofMinutes(15), Duration.ofDays(14),
				clock, roles);
		var afterRestart = new TokenService(SecurityConfig.keys(jwt), "telemetrix", Duration.ofMinutes(15),
				Duration.ofDays(14), clock, roles);

		assertThat(afterRestart.decodeAccess(before.issue("github:someone").accessToken()).getSubject())
				.isEqualTo("github:someone");
	}

	private static Path pem(Path file, String type, byte[] der) throws Exception {
		String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
		return Files.writeString(file, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
	}

}
