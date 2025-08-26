#!/bin/bash

# Convert Railway's DATABASE_URL to JDBC format if needed
if [ -n "$DATABASE_URL" ] && [ -z "$JDBC_DATABASE_URL" ]; then
    # Check if DATABASE_URL starts with postgres:// or postgresql://
    if [[ "$DATABASE_URL" =~ ^postgres(ql)?://([^:]+):([^@]+)@([^:]+):([0-9]+)/(.+)$ ]]; then
        USER="${BASH_REMATCH[2]}"
        PASSWORD="${BASH_REMATCH[3]}"
        HOST="${BASH_REMATCH[4]}"
        PORT="${BASH_REMATCH[5]}"
        DATABASE="${BASH_REMATCH[6]}"
        
        # Remove any query parameters from database name
        DATABASE="${DATABASE%%\?*}"
        
        export JDBC_DATABASE_URL="jdbc:postgresql://$HOST:$PORT/$DATABASE?user=$USER&password=$PASSWORD"
        echo "Converted DATABASE_URL to JDBC_DATABASE_URL"
    else
        # If it's already in JDBC format or another format, use as-is
        export JDBC_DATABASE_URL="$DATABASE_URL"
    fi
fi

# Start the backend
exec java -jar build/libs/backend.jar
