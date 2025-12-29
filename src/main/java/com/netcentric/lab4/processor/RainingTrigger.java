package com.netcentric.lab4.processor;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Properties;

public class RainingTrigger {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "raining-detector");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();
        ObjectMapper mapper = new ObjectMapper();

        // 1. Consume from the raw readings topic
        KStream<String, String> readings = builder.stream("weather_readings");

        // 2. Filter logic: Humidity > 70%
        KStream<String, String> alerts = readings.filter((key, value) -> {
            try {
                JsonNode node = mapper.readTree(value);
                int humidity = node.get("weather").get("humidity").asInt();
                return humidity > 70; // Requirement: Detect "Raining"
            } catch (Exception e) {
                return false;
            }
        }).mapValues(value -> {
             try {
                JsonNode node = mapper.readTree(value);
                ObjectNode alert = mapper.createObjectNode();
                alert.put("type", "RAIN_ALERT");
                alert.put("station_id", node.get("station_id").asLong());
                alert.put("timestamp", System.currentTimeMillis());
                alert.put("message", "Heavy rain detected! Humidity: " + node.get("weather").get("humidity").asInt() + "%");
                return mapper.writeValueAsString(alert);
             } catch (Exception e) {
                 return "{\"error\": \"Failed to parse\"}";
             }
        });

        // 3. Send the alerts to the raining_alerts topic
        alerts.to("raining_alerts", Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();
        
        System.out.println("Raining Trigger is watching the clouds...");
        
        // Add shutdown hook to stop gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}