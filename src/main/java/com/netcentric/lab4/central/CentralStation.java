package com.netcentric.lab4.central;

import org.apache.kafka.clients.consumer.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.Duration;
import java.util.*;

public class CentralStation {
    private static final String DB_URL = "jdbc:postgresql://postgres-service:5432/weather_db";
    private static final String USER = "admin";
    private static final String PASS = "password";

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", "kafka-service:9092");
        props.put("group.id", "central-station-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("auto.offset.reset", "earliest");
        props.put("session.timeout.ms", "30000");
        props.put("max.poll.records", "500");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList("weather_readings"));
            
            ObjectMapper mapper = new ObjectMapper();
            List<JsonNode> buffer = new ArrayList<>();

            try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
                System.out.println("Central Station is connected to Database. Waiting for data...");
                
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    if (!records.isEmpty()) {
                        System.out.println("Received " + records.count() + " records from Kafka");
                    }
                    for (ConsumerRecord<String, String> record : records) {
                        buffer.add(mapper.readTree(record.value()));

                        // Requirement: Batch size 5,000 (reduced to 100 for testing)
                        if (buffer.size() >= 100) {
                            System.out.println("Buffer reached " + buffer.size() + " records, saving batch...");
                            saveBatch(conn, buffer);
                            buffer.clear();
                        }
                    }
                }
            }
        }
    }

    private static void saveBatch(Connection conn, List<JsonNode> batch) throws SQLException {
        String sql = "INSERT INTO weather_readings (station_id, s_no, battery_status, status_timestamp, humidity, temperature, wind_speed) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false); // Speed up batch processing
            for (JsonNode node : batch) {
                pstmt.setLong(1, node.get("station_id").asLong());
                pstmt.setLong(2, node.get("s_no").asLong());
                pstmt.setString(3, node.get("battery_status").asText());
                pstmt.setLong(4, node.get("status_timestamp").asLong());
                pstmt.setInt(5, node.get("weather").get("humidity").asInt());
                pstmt.setInt(6, node.get("weather").get("temperature").asInt());
                pstmt.setInt(7, node.get("weather").get("wind_speed").asInt());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
            System.out.println("Successfully saved a batch of " + batch.size() + " records to SQL.");
        }
    }
}