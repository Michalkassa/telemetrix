package com.telemetrix.watch;

import com.telemetrix.common.Inputs;
import com.telemetrix.watch.WatchService.Command;
import com.telemetrix.watch.WatchService.Created;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

// Watch-together (docs/adr/0005, 0008). REST creates a watch session and reads its clock; STOMP carries
// the host's commands in and every change out to /topic/watch/{id}.
@Controller
class WatchController {

	record CreateRequest(@Positive long sessionId) {
	}

	private final WatchService watches;

	WatchController(WatchService watches) {
		this.watches = watches;
	}

	// The creator is the host: keep the hostKey, share the watchId.
	@PostMapping("/api/watch")
	@ResponseStatus(HttpStatus.CREATED)
	@ResponseBody
	Created create(@RequestBody CreateRequest request) {
		return watches.create(request.sessionId());
	}

	@GetMapping("/api/watch/{watchId}")
	@ResponseBody
	ClockState state(@PathVariable @Pattern(regexp = Inputs.WATCH_ID) String watchId) {
		return watches.state(watchId);
	}

	// SUBSCRIBE /app/watch/{id}: the current clock at once (late joiners), then follow /topic/watch/{id}
	@SubscribeMapping("/watch/{watchId}")
	ClockState current(@DestinationVariable String watchId) {
		return watches.state(watchId);
	}

	// SEND /app/watch/{id}/command {"hostKey": "...", "action": "SEEK", "value": 600000}; the change reaches
	// /topic/watch/{id} on every instance through Redis (RedisWatch), this one included
	@MessageMapping("/watch/{watchId}/command")
	void command(@DestinationVariable String watchId, Command command) {
		watches.command(watchId, command);
	}

}
