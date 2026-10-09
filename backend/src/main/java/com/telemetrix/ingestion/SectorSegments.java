package com.telemetrix.ingestion;

// How many mini-sectors each sector of a session's laps has (OpenF1 laps' segments_sector_1..3) —
// with the track's mini-sector indexes, that places the sector ends exactly.
record SectorSegments(int sector1, int sector2, int sector3) {
}
