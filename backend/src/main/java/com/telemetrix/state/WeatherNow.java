package com.telemetrix.state;

public record WeatherNow(Double airTemperature, Double trackTemperature, Double humidity, boolean rainfall,
		Double windSpeed) {
}
