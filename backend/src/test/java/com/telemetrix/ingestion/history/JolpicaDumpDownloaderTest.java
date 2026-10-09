package com.telemetrix.ingestion.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JolpicaDumpDownloaderTest {

	static final String META = "https://jolpica.test/data/dumps/download/";

	static final String ZIP = "https://jolpica.test/data/dumps/download/delayed/?dump_type=csv";

	MockRestServiceServer server;

	JolpicaDumpDownloader downloader;

	@TempDir
	Path work;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		downloader = new JolpicaDumpDownloader(builder.build(), META);
	}

	@Test
	void downloadsVerifiesAndUnzipsTheDelayedCsvDump() throws Exception {
		byte[] zip = zip("formula_one_season.csv", "id,year\n1,1950\n");
		expectMetadata(sha256(zip));
		server.expect(requestTo(ZIP)).andRespond(withSuccess(zip, MediaType.APPLICATION_OCTET_STREAM));

		Path dir = downloader.download(work);

		server.verify();
		assertThat(Files.readString(dir.resolve("formula_one_season.csv"))).isEqualTo("id,year\n1,1950\n");
	}

	@Test
	void refusesADumpWhoseHashDoesNotMatch() throws Exception {
		byte[] zip = zip("formula_one_season.csv", "id,year\n1,1950\n");
		expectMetadata("0".repeat(64));
		server.expect(requestTo(ZIP)).andRespond(withSuccess(zip, MediaType.APPLICATION_OCTET_STREAM));

		assertThatThrownBy(() -> downloader.download(work)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SHA-256");
	}

	@Test
	void refusesAZipEntryThatEscapesTheTargetFolder() throws Exception {
		byte[] zip = zip("../evil.csv", "x");
		expectMetadata(sha256(zip));
		server.expect(requestTo(ZIP)).andRespond(withSuccess(zip, MediaType.APPLICATION_OCTET_STREAM));

		assertThatThrownBy(() -> downloader.download(work)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("evil.csv");
		assertThat(work.resolve("evil.csv")).doesNotExist();
		assertThat(work.getParent().resolve("evil.csv")).doesNotExist();
	}

	private void expectMetadata(String hash) {
		server.expect(requestTo(META)).andRespond(withSuccess("""
				{"available_types": ["csv", "sql"], "delay_days": 14,
				 "delayed_dumps": {"csv": {"dump_type": "csv", "file_hash": "%s", "file_size": 123,
				   "uploaded_at": "2026-09-25T14:10:49Z", "download_url": "%s"}},
				 "latest_dumps": {}}
				""".formatted(hash, ZIP), MediaType.APPLICATION_JSON));
	}

	private static byte[] zip(String name, String content) throws Exception {
		var bytes = new ByteArrayOutputStream();
		try (var out = new ZipOutputStream(bytes)) {
			out.putNextEntry(new ZipEntry(name));
			out.write(content.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		return bytes.toByteArray();
	}

	private static String sha256(byte[] data) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
	}

}
