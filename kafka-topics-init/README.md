# Kafka Stack Management Command

To test only the kafka-initialization script, use the following command:

## The Command

```bash
docker compose down && \
docker compose up kafka2 kafka3 kafka-connect kafka-topic-init -d --build && \
docker logs kafka-topic-init -f
