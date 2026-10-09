package com.telemetrix.racecontrol;

import com.telemetrix.race.Session;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

// One weather reading at the track during a session (OpenF1, about once a minute).
@Entity
@Table(name = "weather_data")
public class WeatherReading {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id")
	private Session session;

	@Column(nullable = false)
	private Instant recordedAt;

	private Double airTemperature;

	private Double trackTemperature;

	private Double humidity;

	private Double pressure;

	private Boolean rainfall;

	private Double windSpeed;

	private Short windDirection;

	protected WeatherReading() {
	}

	public WeatherReading(Session session, Instant recordedAt, Double airTemperature, Double trackTemperature,
			Double humidity, Double pressure, Boolean rainfall, Double windSpeed, Integer windDirection) {
		this.session = session;
		this.recordedAt = recordedAt;
		this.airTemperature = airTemperature;
		this.trackTemperature = trackTemperature;
		this.humidity = humidity;
		this.pressure = pressure;
		this.rainfall = rainfall;
		this.windSpeed = windSpeed;
		this.windDirection = windDirection == null ? null : windDirection.shortValue();
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public Double getAirTemperature() {
		return airTemperature;
	}

	public Double getTrackTemperature() {
		return trackTemperature;
	}

	public Double getHumidity() {
		return humidity;
	}

	public Double getPressure() {
		return pressure;
	}

	public boolean isRainfall() {
		return Boolean.TRUE.equals(rainfall);
	}

	public Double getWindSpeed() {
		return windSpeed;
	}

	public Short getWindDirection() {
		return windDirection;
	}

}
