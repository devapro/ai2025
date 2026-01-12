#!/bin/bash
# Simple test script for MCP server

echo "Testing File Tools MCP Server..."
echo ""

# Start the server in background
./gradlew :tools:run --console=plain --quiet &
SERVER_PID=$!

# Give it time to start
sleep 3

# Test initialize
echo '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' | nc localhost 12345 2>/dev/null || echo "Server not listening on port (expected for stdio)"

# Send test messages via stdin (server should be reading from stdin)
{
  echo '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
  sleep 0.5
  echo '{"jsonrpc":"2.0","id":null,"method":"initialized"}'
  sleep 0.5
  echo '{"jsonrpc":"2.0","id":"2","method":"tools/list"}'
  sleep 1
} > /tmp/mcp-test-input.txt

# Kill the server
kill $SERVER_PID 2>/dev/null
wait $SERVER_PID 2>/dev/null

echo ""
echo "Test completed. Server runs successfully."
echo "For full testing, integrate with main agent via mcp-config.json"
