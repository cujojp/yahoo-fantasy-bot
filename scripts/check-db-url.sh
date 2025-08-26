#!/bin/bash

echo "=== Database URL Diagnostic ==="
echo ""

# Check raw environment variables
echo "1. Raw environment variables:"
echo "   DATABASE_URL: ${DATABASE_URL:-not set}"
echo "   JDBC_DATABASE_URL: ${JDBC_DATABASE_URL:-not set}"
echo ""

# Parse DATABASE_URL if it exists
if [ -n "$DATABASE_URL" ]; then
    echo "2. Parsing DATABASE_URL:"
    if [[ "$DATABASE_URL" =~ ^postgres(ql)?://([^:]+):([^@]+)@([^:/]+)(:[0-9]+)?/(.+)$ ]]; then
        USER="${BASH_REMATCH[2]}"
        PASSWORD="${BASH_REMATCH[3]}"
        HOST="${BASH_REMATCH[4]}"
        PORT="${BASH_REMATCH[5]:-:5432}"
        PORT="${PORT#:}"
        DATABASE="${BASH_REMATCH[6]%%\?*}"
        
        echo "   User: $USER"
        echo "   Host: $HOST"
        echo "   Port: $PORT"
        echo "   Database: $DATABASE"
        echo ""
        
        # Test connection with psql if available
        if command -v psql &> /dev/null; then
            echo "3. Testing connection with psql:"
            PGPASSWORD="$PASSWORD" psql -h "$HOST" -p "$PORT" -U "$USER" -d "postgres" -c "\l" 2>&1 | grep -E "(railway|postgres)" || echo "   Connection test failed"
        else
            echo "3. psql not available for connection test"
        fi
    else
        echo "   Could not parse DATABASE_URL format"
    fi
else
    echo "2. DATABASE_URL not set"
fi

echo ""
echo "4. Available databases (if connected):"
echo "   Run this script on Railway to see available databases"
echo ""
echo "=== End Diagnostic ==="
