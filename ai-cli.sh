#!/bin/bash
# AI Assistant CLI Launcher
# This script runs the AI Assistant in CLI mode with proper terminal support

cd "$(dirname "$0")"
java -jar app/build/libs/app.jar "$@"
