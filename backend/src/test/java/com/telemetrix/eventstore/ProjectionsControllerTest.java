package com.telemetrix.eventstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// Throwing every read model away to rebuild it from the events (an admin operation).
@WebMvcTest(ProjectionsController.class)
// controller behaviour only: who may call it is AuthorizationMatrixTest's, against the real filter chain
@AutoConfigureMockMvc(addFilters = false)
class ProjectionsControllerTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	Projections projections;

	@Test
	void rebuildsEveryReadModelAndSaysWhich() {
		given(projections.rebuildAll()).willReturn(List.of("timeline", "championship_snapshots"));

		assertThat(mvc.post().uri("/api/admin/projections/rebuild")).hasStatusOk().bodyJson()
				.isStrictlyEqualTo("""
						{"rebuilt": ["timeline", "championship_snapshots"]}
						""");
	}

}
