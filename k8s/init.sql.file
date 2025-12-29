-- Database Initialization Script
-- This table stores all weather readings from stations
CREATE TABLE IF NOT EXISTS weather_readings (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT,
    s_no BIGINT,
    battery_status VARCHAR(10),
    status_timestamp BIGINT,
    humidity INT,
    temperature INT,
    wind_speed INT
);

-- Index for faster queries on station IDs
CREATE INDEX IF NOT EXISTS idx_station_id ON weather_readings(station_id);
