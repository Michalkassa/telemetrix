package com.telemetrix;

import com.telemetrix.ingestion.f1archive.F1ArchiveClient.SessionStreams;
import com.telemetrix.ingestion.jolpica.JolpicaRace;
import com.telemetrix.ingestion.multiviewer.MultiviewerCircuit;
import com.telemetrix.ingestion.openf1.OpenF1CarData;
import com.telemetrix.ingestion.openf1.OpenF1Driver;
import com.telemetrix.ingestion.openf1.OpenF1Lap;
import com.telemetrix.ingestion.openf1.OpenF1Location;
import com.telemetrix.ingestion.openf1.OpenF1Meeting;
import com.telemetrix.ingestion.openf1.OpenF1PitStop;
import com.telemetrix.ingestion.openf1.OpenF1RaceControl;
import com.telemetrix.ingestion.openf1.OpenF1TeamRadio;
import com.telemetrix.ingestion.openf1.OpenF1Weather;
import com.telemetrix.ingestion.openf1.OpenF1Session;
import com.telemetrix.ingestion.openf1.OpenF1Stint;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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

	public static List<OpenF1CarData> openF1CarData(int sessionKey, int driver) {
		return read("openf1/car_data_" + sessionKey + "_" + driver + ".json", new TypeReference<>() {
		});
	}

	public static List<OpenF1Location> openF1Locations(int sessionKey, int driver) {
		return read("openf1/location_" + sessionKey + "_" + driver + ".json", new TypeReference<>() {
		});
	}

	public static List<OpenF1Driver> openF1Drivers(int sessionKey) {
		return read("openf1/drivers_" + sessionKey + ".json", new TypeReference<>() {
		});
	}

	public static List<OpenF1RaceControl> openF1RaceControl(int sessionKey) {
		return read("openf1/race_control_" + sessionKey + ".json", new TypeReference<>() {
		});
	}

	public static List<OpenF1Weather> openF1Weather(int sessionKey) {
		return read("openf1/weather_" + sessionKey + ".json", new TypeReference<>() {
		});
	}

	public static List<OpenF1TeamRadio> openF1TeamRadio(int sessionKey) {
		return read("openf1/team_radio_" + sessionKey + ".json", new TypeReference<>() {
		});
	}

	public static MultiviewerCircuit multiviewerCircuit(int circuitKey, int year) {
		return read("multiviewer/circuit_" + circuitKey + "_" + year + ".json", new TypeReference<>() {
		});
	}

	/** The F1 archive's TrackStatus and SessionData streams for Singapore 2023's race (session 9165). */
	public static SessionStreams f1ArchiveSingapore2023Race() {
		return new SessionStreams(text("f1archive/singapore_2023_race_TrackStatus.jsonStream"),
				text("f1archive/singapore_2023_race_SessionData.jsonStream"));
	}

	private static String text(String path) {
		try {
			return new ClassPathResource("fixtures/" + path).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
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
