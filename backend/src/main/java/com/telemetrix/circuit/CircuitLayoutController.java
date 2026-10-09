package com.telemetrix.circuit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CircuitLayoutController {

	private final CircuitLayoutService service;

	CircuitLayoutController(CircuitLayoutService service) {
		this.service = service;
	}

	// Available once a race at the circuit that year has been opened (its import fetches the map).
	@GetMapping("/api/circuits/{ref}/layout")
	LayoutResponse layout(@PathVariable String ref, @RequestParam @Min(1950) @Max(2100) int year) {
		return LayoutResponse.of(service.layout(ref, year));
	}

}
