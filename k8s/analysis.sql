-- 1. Battery status distribution per station
-- Expected: Low ~30%, Medium ~40%, High ~30%
SELECT station_id, battery_status, COUNT(*) as count,
       ROUND((COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (PARTITION BY station_id)), 2) as percentage
FROM weather_readings
GROUP BY station_id, battery_status
ORDER BY station_id, battery_status;

-- 2. Dropped messages per station
-- Expected: ~10% drop rate
SELECT station_id,
       MIN(s_no) as min_seq,
       MAX(s_no) as max_seq,
       COUNT(*) as received_count,
       (MAX(s_no) - MIN(s_no) + 1) as expected_count,
       (MAX(s_no) - MIN(s_no) + 1) - COUNT(*) as dropped_count,
       ROUND(((MAX(s_no) - MIN(s_no) + 1) - COUNT(*)) * 100.0 / (MAX(s_no) - MIN(s_no) + 1), 2) as drop_rate_percentage
FROM weather_readings
GROUP BY station_id
ORDER BY station_id;
