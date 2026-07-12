#!/bin/sh
# Ensure log directory exists and is writable
# This runs as root before dropping privileges
mkdir -p /var/log/stock_simulator
chown appuser:appgroup /var/log/stock_simulator 2>/dev/null || true
chmod 755 /var/log/stock_simulator 2>/dev/null || true
# Drop to appuser and exec the main process
exec su-exec appuser "$@"
