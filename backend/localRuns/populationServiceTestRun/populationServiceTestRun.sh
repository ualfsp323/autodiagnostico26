#!/bin/bash
set -e

# Directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../" && pwd)"
BACKEND_DIR="$ROOT_DIR"

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

# --- MySQL Section ---
echo "Checking MySQL on localhost:$MYSQL_PORT..."
if port_open "localhost" "$MYSQL_PORT"; then
    echo "MySQL is already accessible."
else
    echo "MySQL not accessible, starting Docker container..."
    
    # Remove any old container just in case
    sudo docker rm -f "$MYSQL_CONTAINER" >/dev/null 2>&1 || true

    # Run MySQL in a temporary Docker container
    sudo docker run --name "$MYSQL_CONTAINER" \
        -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
        -e MYSQL_DATABASE="$MYSQL_DB" \
        -p "$MYSQL_PORT":3306 \
        -d mysql:8

    echo "Waiting for MySQL to initialize..."
    sleep 15  # Give MySQL time to start

    echo "MySQL container '$MYSQL_CONTAINER' is running."
fi

# --- Maven Section ---
echo "Checking if Maven is running in Docker..."
# We run Maven in a temporary container if not already running
# We'll mount the backend directory and run the test
sudo docker rm -f "$MAVEN_CONTAINER" >/dev/null 2>&1 || true

echo "Starting Maven Docker container for one-time test..."
sudo docker run --name "$MAVEN_CONTAINER" --rm -v "$BACKEND_DIR":/usr/src/mymaven -w /usr/src/mymaven maven:3.9.15-eclipse-temurin-21 \
    mvn clean test -Dtest=DataPopulationServiceIntegrationTest \
    -Dspring.datasource.url="jdbc:mysql://host.docker.internal:$MYSQL_PORT/$MYSQL_DB?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
