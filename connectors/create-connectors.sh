#!/bin/bash

set -euo pipefail

CONNECTOR_NAME="mongo-sink-new-sessions"
TEMPLATE_FILE="/config/new-session-mongo-sink.template.json"
TEMP_FILE="/tmp/${CONNECTOR_NAME}.json"
CONNECT_URL=${CONNECT_URL}

echo "--- Creating MongoDB Sink Connector: $CONNECTOR_NAME ---"

# Check if the template exists
if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "ERROR: Template not found at $TEMPLATE_FILE"
    exit 1
fi

# Substitute env variables into template
envsubst < "$TEMPLATE_FILE" > "$TEMP_FILE"

echo "✓ Template rendered to $TEMP_FILE:"
cat "$TEMP_FILE"

# Submit connector to Kafka Connect
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$CONNECT_URL/connectors" \
  -H "Content-Type: application/json" \
  --data @"$TEMP_FILE")

if [ "$RESPONSE" -eq 201 ] || [ "$RESPONSE" -eq 409 ]; then
    echo "✓ Connector '$CONNECTOR_NAME' created (or already exists)."
else
    echo "✖ Failed to create connector '$CONNECTOR_NAME'. HTTP $RESPONSE"
    exit 1
fi
