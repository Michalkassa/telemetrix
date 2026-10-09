package com.telemetrix.ingestion.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DumpCsvTest {

	@Test
	void readsColumnsByNameWhateverTheirOrder() throws Exception {
		// the fixture's circuit.csv has its columns in reverse order
		Path file = new ClassPathResource("fixtures/jolpica/dump/formula_one_circuit.csv").getFile().toPath();

		var rows = DumpCsv.readAll(file);

		var bahrain = rows.stream().filter(r -> r.get("reference").equals("bahrain")).findFirst().orElseThrow();
		assertThat(bahrain).containsEntry("name", "Bahrain International Circuit")
				.containsEntry("locality", "Sakhir")
				.containsEntry("latitude", "26.0325");
		assertThat(rows).hasSize(4);
	}

	@Test
	void anEmptyValueIsAnEmptyString() throws Exception {
		Path file = new ClassPathResource("fixtures/jolpica/dump/formula_one_driver.csv").getFile().toPath();

		var farina = DumpCsv.readAll(file).stream().filter(r -> r.get("reference").equals("farina")).findFirst()
				.orElseThrow();

		assertThat(farina).containsEntry("abbreviation", "").containsEntry("surname", "Farina");
	}

}
