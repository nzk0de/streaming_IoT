# ---- Stage 1: Build ----
# UPDATED AGAIN: Use Go 1.23 to satisfy the latest dependency requirements.
# It's best practice to match this with the 'go' directive in your go.mod file.
FROM golang:1.23-bookworm AS builder

# 1. Install CGO dependency for librdkafka.
RUN apt-get update && apt-get install -y --no-install-recommends librdkafka-dev gcc

# 2. Set the working directory for our application.
WORKDIR /app

# 3. Copy only the dependency manifests first.
COPY mqtt-bridge/go.mod mqtt-bridge/go.sum ./

# 4. Download dependencies INSIDE the container.
#    This will now succeed using Go 1.23.
RUN go mod download

# 5. Now, copy all the application source code.
COPY mqtt-bridge/. .
RUN go mod tidy
# 6. Build the application.
RUN CGO_ENABLED=1 go build -ldflags="-w -s" -o /app/mqttbridge ./cmd/mqtt-bridge


# ---- Stage 2: Run ----
# The run stage does not need to change.
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends librdkafka1 ca-certificates && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/mqttbridge .

CMD ["./mqttbridge"]