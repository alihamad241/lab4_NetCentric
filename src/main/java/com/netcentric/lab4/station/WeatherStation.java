package com.netcentric.lab4.station;

import org.apache.kafka.clients.producer.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Properties;
import java.util.Random;

public class WeatherStation {
    public static void main(String[] args) throws Exception {
        // Allow passing ID as arg (e.g., java WeatherStation 5)
        // Unique ID from Environment (Pod Name) or Arg or Default
        String envId = System.getenv("STATION_ID_ENV");
        long stationId = (envId != null) ? (long)Math.abs(envId.hashCode()) : (args.length > 0) ? Long.parseLong(args[0]) : 1L;
        long sNo = 1;
        
        Properties props = new Properties();
        String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") != null ? System.getenv("KAFKA_BOOTSTRAP_SERVERS") : "kafka-service:9092";
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            ObjectMapper mapper = new ObjectMapper();
            Random rand = new Random();

            System.out.println("Weather Station " + stationId + " starting...");

            while (true) {
                // 1. Requirement: 10% Message Drop Logic
                if (rand.nextDouble() > 0.10) {
                    
                    ObjectNode root = mapper.createObjectNode();
                    root.put("station_id", stationId);
                    root.put("s_no", sNo);
                    
                    // 2. Requirement: Battery Distribution (30/40/30)
                    double bVal = rand.nextDouble();
                    String battery = (bVal < 0.3) ? "low" : (bVal < 0.7) ? "medium" : "high";
                    root.put("battery_status", battery);
                    root.put("status_timestamp", System.currentTimeMillis() / 1000L);

                    // 3. Requirement: Nested Weather Object
                    ObjectNode weather = root.putObject("weather");
                    weather.put("humidity", rand.nextInt(101));
                    weather.put("temperature", rand.nextInt(40) + 60); // 60-100F
                    weather.put("wind_speed", rand.nextInt(50));

                    String json = mapper.writeValueAsString(root);

                    // 4. Send to Kafka
                    producer.send(new ProducerRecord<>("weather_readings", String.valueOf(stationId), json));
                    System.out.println("Sent seq " + sNo + " | Battery: " + battery);
                } else {
                    System.out.println("Dropped seq " + sNo + " (Simulating instability)");
                }

                sNo++;
                Thread.sleep(1000); // 5. Requirement: 1-second interval
            }
        }
    }
}