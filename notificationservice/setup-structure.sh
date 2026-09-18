#!/usr/bin/env bash
set -e

BASE="src/main/java/com/example/notificationservice"
TEST_BASE="src/test/java/com/example/notificationservice"
RES="src/main/resources"

# main packages
mkdir -p "$BASE"/{config,domain,dto,repository,controller,security,exception,scheduler}
mkdir -p "$BASE"/service/dispatch

# mirrored test packages
mkdir -p "$TEST_BASE"/{config,domain,dto,repository,controller,security,exception,scheduler}
mkdir -p "$TEST_BASE"/service/dispatch

# resources
mkdir -p "$RES"/db/migration

# keep otherwise-empty dirs tracked by git
find src -type d -empty -exec touch {}/.gitkeep \;

echo "Package structure created under $BASE"
