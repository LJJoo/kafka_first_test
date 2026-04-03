package com.example.kafka_test2;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

@Service
public class ConsumerControlService {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerControlService.class);
    private static final String GROUP_ID = "order-group";
    private static final String TOPIC = "order-events";

    private final KafkaListenerEndpointRegistry registry;
    private final String bootstrapServers;

    public ConsumerControlService(KafkaListenerEndpointRegistry registry,
                                  @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.registry = registry;
        this.bootstrapServers = bootstrapServers;
    }

    public void pause() {
        registry.getListenerContainers().forEach(MessageListenerContainer::pause);
        logger.info("consumer paused");
    }

    public void resume() {
        registry.getListenerContainers().forEach(MessageListenerContainer::resume);
        logger.info("consumer resumed");
    }

    public boolean isPaused() {
        return registry.getListenerContainers().stream()
                .anyMatch(MessageListenerContainer::isContainerPaused);
    }

    public long getLag() {
        try (AdminClient admin = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {

            Map<TopicPartition, OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets(GROUP_ID)
                            .partitionsToOffsetAndMetadata()
                            .get();

            if (committed.isEmpty()) return 0;

            Map<TopicPartition, OffsetSpec> latestRequest = committed.keySet().stream()
                    .filter(tp -> tp.topic().equals(TOPIC))
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

            if (latestRequest.isEmpty()) return 0;

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    admin.listOffsets(latestRequest).all().get();

            long lag = 0;
            for (Map.Entry<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> entry : endOffsets.entrySet()) {
                long end = entry.getValue().offset();
                long current = committed.getOrDefault(entry.getKey(), new OffsetAndMetadata(0)).offset();
                lag += Math.max(0, end - current);
            }
            return lag;

        } catch (Exception e) {
            logger.warn("failed to get consumer lag", e);
            return -1;
        }
    }
}
