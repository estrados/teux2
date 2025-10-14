#!/bin/bash

# Change this to match your server IP (must match LogHelper.kt SERVER_URL)
SERVER_IP="192.168.1.11"
SERVER_PORT="8080"

# Start PHP logging server
echo "Starting PHP logging server at http://$SERVER_IP:$SERVER_PORT"
echo "Open http://$SERVER_IP:$SERVER_PORT in your browser to view logs"
echo "Press Ctrl+C to stop"
echo ""

php -S $SERVER_IP:$SERVER_PORT
