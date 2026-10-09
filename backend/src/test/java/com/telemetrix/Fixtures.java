package com.telemetrix;

import com.telemetrix.ingestion.jolpica.JolpicaRace;
import com.telemetrix.ingestion.openf1.OpenF1Lap;
import com.telemetrix.ingestion.openf1.OpenF1Meeting;
import com.telemetrix.ingestion.openf1.OpenF1PitStop;
import com.telemetrix.ingestion.openf1.OpenF1Session;
import com.telemetrix.ingestion.openf1.OpenF1Stint;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// Recorded API responses under src/test/resources/fixtures, read into the clients' records.
public final class Fixtures {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private Fixtures() {
	}

	public static List<JolpicaRace> jolpicaRaces(int year) {
		var races = read("jolpica/races_" + year + ".json").path("MRData").path("RaceTable").path("Races");
		return JSON.readerFor(new TypeReference<List<JolpicaRace>>() {
		}).readValue(races);
	}

	public static List<OpenF1Meeting> openF1Meetings(int year) {
		return read("openf1/meetings_" + year + ".json", new TypeReference<>() {
		});
	}

	public static List<OpenF1Session> openF1Sessions(int meetingKey) {
		return read("openf1/sessions_" + meetingKey + ".json", new TypeReference<>() {
		});
	}

	public static List<OpenF1Lap> openF1Laps(int sessionKey) {
		return read("openf1/laps_" + sessionKey + ".json", new TypeReference<>() {
		});
	}

	public static List<OpenF1Stint> openF1Stints(int sessionKey) {
		return read("openf1/stints_" + sessionKey + ".json", new TypeReference<>() {
		});
	}

	public static List<OpenF1PitStop> openF1PitStops(int sessionKey) {
		return read("openf1/pit_" + sessionKey + ".json", new TypeReference<>() {
		});
	}

	private static tools.jackson.databind.JsonNode read(String path) {
		try (InputStream in = new ClassPathResource("fixtures/" + path).getInputStream()) {
			return JSON.readTree(in);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static <T> T read(String path, TypeReference<T> type) {
		try (InputStream in = new ClassPathResource("fixtures/" + path).getInputStream()) {
			return JSON.readValue(in, type);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
