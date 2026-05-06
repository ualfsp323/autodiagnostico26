# This script checks if the MySQL container is running, starts it if not,
# fetches its port, and runs the DataPopulationServiceIntegrationTest.

# ==============================================================================
# WARNING: THIS SCRIPT WAS AI GENERATED, AND THE HUMAN WHO VALIDATED DOESN'T HAVE
# THE EXPERTISE IN POWERSHELL TO ENSURE ITS CORRECTNESS. USE WITH CAUTION.
# ==============================================================================


# Stop on any error
$ErrorActionPreference = "Stop"

# --- Directories ---
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ROOT_DIR = Resolve-Path "$SCRIPT_DIR\..\.."
$BACKEND_DIR = $ROOT_DIR

# --- Docker network ---
$DOCKER_NETWORK = "autodiagnostico-net"

# --- MySQL config ---
$MYSQL_CONTAINER = "autodiagnostico-db"
$MYSQL_ROOT_PASSWORD = "rootpassword"
$MYSQL_DB = "autodiagnostico"
$MYSQL_PORT = 3306

# --- Maven config ---
$MAVEN_CONTAINER = "maven-runner"

# --- Function to check if a port is open ---
function Test-PortOpen {
    param(
        [string]$Host,
        [int]$Port
    )
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect($Host, $Port)
        $tcp.Close()
        return $true
    } catch {
        return $false
    }
}

# --- Docker network setup ---
Write-Host "Ensuring Docker network '$DOCKER_NETWORK' exists..."
if (-not (docker network inspect $DOCKER_NETWORK -ErrorAction SilentlyContinue)) {
    docker network create $DOCKER_NETWORK | Out-Null
    Write-Host "Docker network '$DOCKER_NETWORK' created."
} else {
    Write-Host "Docker network '$DOCKER_NETWORK' already exists."
}

# --- MySQL Section ---
Write-Host "Checking MySQL container '$MYSQL_CONTAINER'..."
$mysqlRunning = docker ps --filter "name=$MYSQL_CONTAINER" --filter "status=running" --format "{{.Names}}" | Select-String $MYSQL_CONTAINER

if ($mysqlRunning) {
    Write-Host "MySQL container is already running."
} else {
    Write-Host "Starting MySQL container '$MYSQL_CONTAINER'..."

    # Remove old container if it exists
    docker rm -f $MYSQL_CONTAINER -ErrorAction SilentlyContinue | Out-Null

    # Run MySQL container on the network
    docker run --name $MYSQL_CONTAINER `
        --network $DOCKER_NETWORK `
        -e "MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD" `
        -e "MYSQL_DATABASE=$MYSQL_DB" `
        -p "$MYSQL_PORT`:3306" `
        -d mysql:8 | Out-Null

    Write-Host "Waiting for MySQL to initialize..."
    Start-Sleep -Seconds 15

    Write-Host "MySQL container '$MYSQL_CONTAINER' is running on network '$DOCKER_NETWORK'."
}

# --- Maven Section ---
Write-Host "Running Maven tests in Docker..."

# Remove old Maven container if exists (temporary)
docker rm -f $MAVEN_CONTAINER -ErrorAction SilentlyContinue | Out-Null

# Run Maven container on the network
docker run --name $MAVEN_CONTAINER --rm `
    --network $DOCKER_NETWORK `
    -v "$BACKEND_DIR`:/usr/src/mymaven" `
    -w /usr/src/mymaven `
    maven:3.9.15-eclipse-temurin-21 `
    mvn clean test -Dtest=DataPopulationServiceIntegrationTest `
    -Dspring.datasource.url="jdbc:mysql://$MYSQL_CONTAINER:$MYSQL_PORT/$MYSQL_DB?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true" `
    -Dspring.datasource.username=root `
    -Dspring.datasource.password="$MYSQL_ROOT_PASSWORD"