package com.telemetrix.ingestion.openf1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

// One lap of one car, as OpenF1 returns it from /laps. Durations in seconds; any can be missing.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Lap(
		@JsonProperty("driver_number") int driverNumber,
		@JsonProperty("lap_number") int lapNumber,
		@JsonProperty("date_start") OffsetDateTime dateStart,
		@JsonProperty("duration_sector_1") Double durationSector1,
		@JsonProperty("duration_sector_2") Double durationSector2,
		@JsonProperty("duration_sector_3") Double durationSector3,
		@JsonProperty("lap_duration") Double lapDuration,
		@JsonProperty("i1_speed") Integer i1Speed,
		@JsonProperty("i2_speed") Integer i2Speed,
		@JsonProperty("st_speed") Integer stSpeed,
		@JsonProperty("is_pit_out_lap") Boolean isPitOutLap,
		// one status code per mini-sector of each sector: their counts place the sector ends on the map
		@JsonProperty("segments_sector_1") List<Integer> segmentsSector1,
		@JsonProperty("segments_sector_2") List<Integer> segmentsSector2,
		@JsonProperty("segments_sector_3") List<Integer> segmentsSector3) {
}
