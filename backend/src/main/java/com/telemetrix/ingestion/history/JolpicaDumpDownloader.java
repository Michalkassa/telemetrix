package com.telemetrix.ingestion.history;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

// Downloads Jolpica's free CSV database dump (delayed 14 days, non-commercial use — docs/adr/0003),
// checks its SHA-256 against the published hash, and unzips it.
public class JolpicaDumpDownloader {

	private static final Logger log = LoggerFactory.getLogger(JolpicaDumpDownloader.class);

	private final RestClient http;

	private final String metadataUrl;

	public JolpicaDumpDownloader(RestClient http, String metadataUrl) {
		this.http = http;
		this.metadataUrl = metadataUrl;
	}

	/** Downloads and unzips into `workDir/csv`; returns that folder. */
	public Path download(Path workDir) {
		Map<String, Object> csv = csvDumpInfo();
		String url = (String) csv.get("download_url");
		String expectedHash = (String) csv.get("file_hash");
		log.info("Downloading the Jolpica CSV dump uploaded at {} from {}", csv.get("uploaded_at"), url);

		byte[] zip = http.get().uri(java.net.URI.create(url)).retrieve().body(byte[].class);
		if (zip == null) {
			throw new IllegalStateException("The Jolpica dump download was empty: " + url);
		}
		String actualHash = sha256(zip);
		if (!actualHash.equalsIgnoreCase(expectedHash)) {
			throw new IllegalStateException(
					"The Jolpica dump's SHA-256 is " + actualHash + ", but Jolpica published " + expectedHash);
		}
		return unzip(zip, workDir.resolve("csv"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> csvDumpInfo() {
		Map<String, Object> meta = http.get().uri(java.net.URI.create(metadataUrl)).retrieve()
				.body(new ParameterizedTypeReference<Map<String, Object>>() {
				});
		if (meta == null || !(meta.get("delayed_dumps") instanceof Map<?, ?> delayed)
				|| !(delayed.get("csv") instanceof Map<?, ?> csv)) {
			throw new IllegalStateException("No delayed CSV dump listed at " + metadataUrl);
		}
		return (Map<String, Object>) csv;
	}

	private static Path unzip(byte[] zip, Path target) {
		try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			Files.createDirectories(target);
			Path root = target.toAbsolutePath().normalize();
			ZipEntry entry;
			while ((entry = in.getNextEntry()) != null) {
				Path file = root.resolve(entry.getName()).normalize();
				if (!file.startsWith(root)) { // a "zip slip": an entry trying to write outside the folder
					throw new IllegalStateException("Refusing zip entry outside the target folder: " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(file);
				}
				else {
					Files.createDirectories(file.getParent());
					Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			}
			return root;
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not unzip the Jolpica dump", e);
		}
	}

	private static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
