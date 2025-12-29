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
