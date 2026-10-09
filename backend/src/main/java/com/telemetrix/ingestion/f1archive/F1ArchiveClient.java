package com.telemetrix.ingestion.f1archive;

import com.telemetrix.ingestion.RateLimitedClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.ParameterizedTypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// The F1 live-timing static archive (livetiming.formula1.com/static; unofficial, free, docs/adr/0003):
// used only for a session's exact track status. Each year's Index.json lists every session's folder by
// session key (the same keys as OpenF1); it's fetched once per year and remembered. A file that isn't
// there (404) is empty.
public class F1ArchiveClient {

	/** A session's TrackStatus and SessionData streams, as text. */
	public record SessionStreams(String trackStatus, String sessionData) {
	}

	private static final ParameterizedTypeReference<String> TEXT = new ParameterizedTypeReference<>() {
	};

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RateLimitedClient http;

	// year -> session key -> folder, e.g. "2023/2023-09-17_Singapore_Grand_Prix/2023-09-17_Race/"
	private final Map<Integer, Map<Integer, String>> folders = new ConcurrentHashMap<>();

	public F1ArchiveClient(RateLimitedClient http) {
		this.http = http;
	}

	public Optional<SessionStreams> trackStatus(int year, int sessionKey) {
		String folder = folders.computeIfAbsent(year, this::index).get(sessionKey);
		if (folder == null) {
			return Optional.empty();
		}
		Optional<String> trackStatus = file(folder + "TrackStatus.jsonStream");
		if (trackStatus.isEmpty()) {
			return Optional.empty();
		}
		return file(folder + "SessionData.jsonStream").map(sessionData -> new SessionStreams(trackStatus.get(),
				sessionData));
	}

	private Map<Integer, String> index(int year) {
		Map<Integer, String> byKey = new HashMap<>();
		file(year + "/Index.json").ifPresent(text -> {
			for (JsonNode meeting : JSON.readTree(text).path("Meetings")) {
				for (JsonNode session : meeting.path("Sessions")) {
					if (session.hasNonNull("Key") && session.hasNonNull("Path")) {
						byKey.put(session.get("Key").asInt(), session.get("Path").asString());
					}
				}
			}
		});
		return byKey;
	}

	// the archive's files start with a UTF-8 byte order mark
	private Optional<String> file(String path) {
		return http.get("/" + path, Map.of(), TEXT).map(text -> text.replace("﻿", ""));
	}

}
