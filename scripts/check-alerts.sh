#!/bin/bash

# Check if alerts are configured in the database

# Load environment variables
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# Use psql to query the database
echo "Checking alerts in database..."
echo ""

# Parse JDBC URL or DATABASE_URL
if [ ! -z "$DATABASE_URL" ]; then
    # Parse DATABASE_URL (postgres://user:pass@host:port/db)
    DB_URL=$DATABASE_URL
elif [ ! -z "$JDBC_DATABASE_URL" ]; then
    # Parse JDBC URL (jdbc:postgresql://host:port/db)
    DB_URL=$(echo $JDBC_DATABASE_URL | sed 's/jdbc://')
else
    echo "No database URL found in environment"
    exit 1
fi

echo "Connecting to: $DB_URL"
echo ""
echo "Alerts table content:"
psql "$DB_URL" -c "SELECT * FROM alerts;"
echo ""
echo "Alerts count:"
psql "$DB_URL" -c "SELECT COUNT(*) as alert_count FROM alerts;"
