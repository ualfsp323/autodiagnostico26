#!/bin/bash
set -e

# Directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../" && pwd)"
BACKEND_DIR="$ROOT_DIR"

# Docker network
DOCKER_NETWORK="autodiagnostico-net"

# MySQL config
MYSQL_CONTAINER="autodiagnostico-db"
MYSQL_ROOT_PASSWORD="rootpassword"
MYSQL_DB="autodiagnostico"
MYSQL_PORT=3306

# Maven config
MAVEN_CONTAINER="maven-runner"

# Function to check if port is open
function port_open() {
    nc -zv "$1" "$2" >/dev/null 2>&1
}

# --- Docker network setup ---
echo "Ensuring Docker network '$DOCKER_NETWORK' exists..."
if ! sudo docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1; then
    sudo docker network create "$DOCKER_NETWORK"
    echo "Docker network '$DOCKER_NETWORK' created."
else
    echo "Docker network '$DOCKER_NETWORK' already exists."
fi

# --- MySQL Section ---
echo "Checking MySQL container '$MYSQL_CONTAINER'..."
if sudo docker ps --filter "name=$MYSQL_CONTAINER" --filter "status=running" | grep "$MYSQL_CONTAINER" >/dev/null; then
    echo "MySQL container is already running."
else
    echo "Starting MySQL container '$MYSQL_CONTAINER'..."

    # Remove any old container just in case
    sudo docker rm -f "$MYSQL_CONTAINER" >/dev/null 2>&1 || true

    # Run MySQL on the same network
    sudo docker run --name "$MYSQL_CONTAINER" \
        --network "$DOCKER_NETWORK" \
        -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
        -e MYSQL_DATABASE="$MYSQL_DB" \
        -p "$MYSQL_PORT":3306 \
        -d mysql:8

    echo "Waiting for MySQL to initialize..."
    sleep 15  # Give MySQL time to start

    echo "MySQL container '$MYSQL_CONTAINER' is running on network '$DOCKER_NETWORK'."
fi

# --- Maven Section ---
echo "Running Maven tests in Docker..."

# Remove any old Maven container (temporary)
sudo docker rm -f "$MAVEN_CONTAINER" >/dev/null 2>&1 || true

# Run Maven container on the same network
sudo docker run --name "$MAVEN_CONTAINER" --rm \
    --network "$DOCKER_NETWORK" \
    -v "$BACKEND_DIR":/usr/src/mymaven \
    -w /usr/src/mymaven \
    maven:3.9.15-eclipse-temurin-21 \
    mvn clean test -Dtest=DataPopulationServiceIntegrationTest \
    -Dspring.datasource.url="jdbc:mysql://$MYSQL_CONTAINER:$MYSQL_PORT/$MYSQL_DB?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true" \
    -Dspring.datasource.username=root \
    -Dspring.datasource.password="$MYSQL_ROOT_PASSWORD"