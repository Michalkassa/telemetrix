package com.telemetrix.ingestion.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemetrix.result.ResultStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class JolpicaValuesTest {

	@ParameterizedTest
	@CsvSource({
			"01:31:44.742, 5504742", // a race time
			"00:01:29.179, 89179", // a lap
			"02:13:23.6, 8003600", // tenths (1950)
			"00:01:16.29, 76290", // hundredths
			"00:00:24, 24000" // whole seconds
	})
	void parsesJolpicaTimesToMillis(String time, long millis) {
		assertThat(JolpicaValues.millis(time)).isEqualTo(millis);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void aMissingTimeIsNull(String time) {
		assertThat(JolpicaValues.millis(time)).isNull();
	}

	@ParameterizedTest
	@CsvSource({ "1:31", "abc", "00:61:00" })
	void rejectsSomethingThatIsNotATime(String time) {
		assertThatThrownBy(() -> JolpicaValues.millis(time)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(time);
	}

	@ParameterizedTest
	@CsvSource({ "0, FINISHED", "1, LAPPED", "10, RETIRED_INCIDENT", "11, RETIRED_MECHANICAL", "20, DISQUALIFIED",
			"30, DID_NOT_START", "40, DID_NOT_QUALIFY", "99, OTHER" })
	void mapsStatusCodes(String code, ResultStatus status) {
		assertThat(ResultStatus.fromJolpicaCode(code)).isEqualTo(status);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void aMissingStatusIsNull(String code) {
		assertThat(ResultStatus.fromJolpicaCode(code)).isNull();
	}

}
