#!/bin/bash

# Convert Railway's DATABASE_URL to JDBC format
# Railway sets both DATABASE_URL and JDBC_DATABASE_URL, but both are in PostgreSQL format
# We need to convert to proper JDBC format

if [ -n "$DATABASE_URL" ] || [ -n "$JDBC_DATABASE_URL" ]; then
    # Use JDBC_DATABASE_URL if set, otherwise use DATABASE_URL
    URL_TO_CONVERT="${JDBC_DATABASE_URL:-$DATABASE_URL}"
    
    echo "Original URL: $URL_TO_CONVERT"
    
    # Check if URL starts with postgres:// or postgresql://
    if [[ "$URL_TO_CONVERT" =~ ^postgres(ql)?://([^:]+):([^@]+)@([^:]+):([0-9]+)/(.+)$ ]]; then
        USER="${BASH_REMATCH[2]}"
        PASSWORD="${BASH_REMATCH[3]}"
        HOST="${BASH_REMATCH[4]}"
        PORT="${BASH_REMATCH[5]}"
        DATABASE="${BASH_REMATCH[6]}"
        
        # Remove any query parameters from database name
        DATABASE="${DATABASE%%\?*}"
        
        export JDBC_DATABASE_URL="jdbc:postgresql://$HOST:$PORT/$DATABASE?user=$USER&password=$PASSWORD"
        echo "Converted to JDBC format: jdbc:postgresql://$HOST:$PORT/$DATABASE?user=***&password=***"
    elif [[ "$URL_TO_CONVERT" =~ ^jdbc: ]]; then
        # Already in JDBC format
        export JDBC_DATABASE_URL="$URL_TO_CONVERT"
        echo "URL already in JDBC format"
    else
        echo "WARNING: Could not parse database URL format"
        export JDBC_DATABASE_URL="$URL_TO_CONVERT"
    fi
else
    echo "WARNING: No DATABASE_URL or JDBC_DATABASE_URL found"
fi

# Debug: Show all database-related env vars
echo "Environment variables:"
echo "DATABASE_URL=${DATABASE_URL:+set}"
echo "JDBC_DATABASE_URL=${JDBC_DATABASE_URL:+set}"

# Start the backend
exec java -jar build/libs/backend.jar
