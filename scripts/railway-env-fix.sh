#!/bin/bash

# Railway PostgreSQL environment fix
# This script ensures proper database URL formatting for Railway deployments

echo "=== Railway Environment Fix ==="
echo "Checking environment variables..."

# Show what Railway provides
echo "Raw DATABASE_URL: ${DATABASE_URL:-not set}"
echo "Raw JDBC_DATABASE_URL: ${JDBC_DATABASE_URL:-not set}"

# If we have a DATABASE_URL but no proper JDBC_DATABASE_URL, convert it
if [ -n "$DATABASE_URL" ]; then
    # Parse PostgreSQL URL: postgresql://user:pass@host:port/db
    if [[ "$DATABASE_URL" =~ ^postgres(ql)?://([^:]+):([^@]+)@([^:/]+)(:[0-9]+)?/(.+)$ ]]; then
        USER="${BASH_REMATCH[2]}"
        PASSWORD="${BASH_REMATCH[3]}"
        HOST="${BASH_REMATCH[4]}"
        PORT="${BASH_REMATCH[5]:-:5432}"  # Default to :5432 if not specified
        PORT="${PORT#:}"  # Remove leading colon
        DATABASE="${BASH_REMATCH[6]%%\?*}"  # Remove query params
        
        # Railway uses internal DNS for PostgreSQL
        # Replace any .railway.internal hosts with the public host if needed
        if [[ "$HOST" == *".railway.internal"* ]]; then
            echo "Detected Railway internal host: $HOST"
            # For internal connections, use the internal host as-is
            JDBC_URL="jdbc:postgresql://$HOST:$PORT/$DATABASE"
        else
            JDBC_URL="jdbc:postgresql://$HOST:$PORT/$DATABASE"
        fi
        
        # Add user and password as query parameters
        export JDBC_DATABASE_URL="${JDBC_URL}?user=$USER&password=$PASSWORD&sslmode=require"
        
        echo "Converted to JDBC format"
        echo "JDBC_DATABASE_URL set (password hidden)"
    fi
fi

# Final check
if [ -z "$JDBC_DATABASE_URL" ]; then
    echo "ERROR: Could not set JDBC_DATABASE_URL"
    echo "Please check your Railway PostgreSQL configuration"
    exit 1
fi

echo "Environment setup complete"
echo "=== End Railway Environment Fix ==="

# Execute the actual application
exec "$@"
