package com.plant_watering_system.server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalTime;

@Embeddable
public class ScheduleEntry {

    @Column(name = "time_of_day", nullable = false)
    private LocalTime timeOfDay;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    // bit0 = Monday ... bit6 = Sunday (same encoding as the MQTT "w" field)
    @Column(name = "days_mask", nullable = false)
    private int daysMask;

    protected ScheduleEntry() {}

    public ScheduleEntry(LocalTime timeOfDay, int durationSeconds, int daysMask) {
        this.timeOfDay = timeOfDay;
        this.durationSeconds = durationSeconds;
        this.daysMask = daysMask;
    }

    public LocalTime getTimeOfDay() { return timeOfDay; }
    public int getDurationSeconds() { return durationSeconds; }
    public int getDaysMask() { return daysMask; }
}
