#!/bin/bash

echo "=== Bot Alert E2E Trigger Script ==="
echo ""
echo "This script will create a database entry to trigger an alert in the next minute"
echo ""

# Use the public database URL from Railway
DATABASE_PUBLIC_URL="postgresql://postgres:iiFvyOtvSdVjvNIbUhqADexgokPhaasr@ballast.proxy.rlwy.net:37880/railway"

# Get current time and calculate next minute
CURRENT_HOUR=$(date -u +%-H)
CURRENT_MINUTE=$(date -u +%-M)
CURRENT_DAY=$(date -u +%u)  # 1=Monday, 7=Sunday
CURRENT_MONTH=$(date -u +%-m)

# Calculate next minute
NEXT_MINUTE=$(( (CURRENT_MINUTE + 1) % 60 ))
if [ $NEXT_MINUTE -eq 0 ]; then
    NEXT_HOUR=$(( (CURRENT_HOUR + 1) % 24 ))
else
    NEXT_HOUR=$CURRENT_HOUR
fi

echo "Current UTC time: $(date -u)"
echo "Will create alert for: ${NEXT_HOUR}:${NEXT_MINUTE} UTC"
echo ""

# Map day of week (1-7 Unix) to (1-7 Sunday-Saturday for cron)
# Unix: 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
# Cron: 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat
CRON_DAY=$(( CURRENT_DAY % 7 + 1 ))

# Create a temporary alert that will fire in the next minute
ALERT_TYPE=${1:-3}  # Default to MatchUp (3), can pass 1 for Score, 2 for Standings

case $ALERT_TYPE in
    1) TYPE_NAME="Score" ;;
    2) TYPE_NAME="Standings" ;;
    3) TYPE_NAME="MatchUp" ;;
    *) TYPE_NAME="Unknown" ;;
esac

echo "Creating temporary $TYPE_NAME alert..."

# Generate a unique UUID for this test
TEST_UUID=$((9000000 + RANDOM))

# First, delete any existing test alerts (clean up from previous minute)
psql "$DATABASE_PUBLIC_URL" -c "DELETE FROM alerts WHERE hour = $NEXT_HOUR AND minute = $NEXT_MINUTE;" 2>/dev/null

# Insert the new alert (using UUID for id column)
SQL_COMMAND="INSERT INTO alerts (id, type, hour, minute, start_month, end_month, day_of_week) VALUES (gen_random_uuid(), $ALERT_TYPE, $NEXT_HOUR, $NEXT_MINUTE, 1, 12, $CRON_DAY);"

echo "Running SQL: $SQL_COMMAND"
psql "$DATABASE_PUBLIC_URL" -c "$SQL_COMMAND"

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Alert created successfully!"
    echo ""
    echo "The bot should pick up this alert within 15 seconds and schedule it."
    echo "The alert will fire at ${NEXT_HOUR}:${NEXT_MINUTE} UTC (in about 1 minute)"
    echo ""
    echo "To monitor logs:"
    echo "  railway logs -s yahoo-fantasy-bot-bot"
    echo ""
    echo "To clean up this test alert:"
    echo "  psql \"\$DATABASE_PUBLIC_URL\" -c \"DELETE FROM alerts WHERE hour = $NEXT_HOUR AND minute = $NEXT_MINUTE;\""
else
    echo "✗ Failed to create alert"
fi
