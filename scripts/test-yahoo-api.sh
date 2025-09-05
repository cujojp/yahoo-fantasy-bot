#!/bin/bash

# Test Yahoo Fantasy API endpoints directly
# This script will use the OAuth token from the database to make direct API calls

echo "=== Yahoo Fantasy API Test Script ==="
echo ""

# Get the OAuth token from the database
echo "Fetching OAuth token from database..."
TOKEN_DATA=$(psql "$DATABASE_URL" -t -c "SELECT yahoo_token FROM latest_data ORDER BY retrieved DESC LIMIT 1;" 2>/dev/null)

if [ -z "$TOKEN_DATA" ]; then
    echo "ERROR: No OAuth token found in database. Please authenticate first."
    exit 1
fi

# Extract the access token from the JSON
ACCESS_TOKEN=$(echo "$TOKEN_DATA" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ACCESS_TOKEN" ]; then
    echo "ERROR: Could not extract access token from database."
    exit 1
fi

echo "Token found!"
echo ""

# Get league info from environment
LEAGUE_ID="${YAHOO_LEAGUE_ID}"
GAME_KEY="${YAHOO_GAME_KEY}"

if [ -z "$LEAGUE_ID" ] || [ -z "$GAME_KEY" ]; then
    echo "ERROR: YAHOO_LEAGUE_ID or YAHOO_GAME_KEY not set in environment"
    exit 1
fi

LEAGUE_KEY="${GAME_KEY}.l.${LEAGUE_ID}"
BASE_URL="https://fantasysports.yahooapis.com/fantasy/v2/league/${LEAGUE_KEY}"

echo "League Key: $LEAGUE_KEY"
echo ""

# Function to make API request
make_request() {
    local endpoint=$1
    local output_file=$2
    
    echo "Testing: $endpoint"
    
    response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        "${BASE_URL}${endpoint}")
    
    # Extract status code and body
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    echo "Status: $http_code"
    
    if [ "$http_code" -eq 200 ]; then
        echo "$body" > "$output_file"
        echo "Saved to: $output_file"
        
        # Pretty print first 50 lines
        echo "First 50 lines of response:"
        echo "$body" | xmllint --format - 2>/dev/null | head -50 || echo "$body" | head -50
    else
        echo "ERROR Response:"
        echo "$body"
    fi
    
    echo ""
    echo "---"
    echo ""
}

# Create output directory
mkdir -p yahoo-api-test-results

# Test different endpoints
echo "1. Testing /teams/matchups endpoint (what bot uses for MatchUp alerts):"
make_request "/teams/matchups" "yahoo-api-test-results/teams-matchups.xml"

echo "2. Testing /scoreboard endpoint:"
make_request "/scoreboard" "yahoo-api-test-results/scoreboard.xml"

echo "3. Testing /standings endpoint:"
make_request "/standings" "yahoo-api-test-results/standings.xml"

echo "4. Testing /transactions endpoint:"
make_request "/transactions" "yahoo-api-test-results/transactions.xml"

echo "5. Testing league info:"
make_request "" "yahoo-api-test-results/league-info.xml"

echo ""
echo "=== Analysis ==="
echo ""

# Analyze teams/matchups structure
if [ -f "yahoo-api-test-results/teams-matchups.xml" ]; then
    echo "Teams/Matchups structure analysis:"
    
    # Count teams
    team_count=$(grep -c "<team>" yahoo-api-test-results/teams-matchups.xml || echo "0")
    echo "- Total <team> elements: $team_count"
    
    # Count matchups
    matchup_count=$(grep -c "<matchup>" yahoo-api-test-results/teams-matchups.xml || echo "0")
    echo "- Total <matchup> elements: $matchup_count"
    
    # Find current week
    current_week=$(grep -o "<current_week>[0-9]*</current_week>" yahoo-api-test-results/teams-matchups.xml | grep -o "[0-9]*" | head -1)
    echo "- Current week: ${current_week:-not found}"
    
    # Sample matchup weeks
    echo "- Sample matchup weeks:"
    grep -o "<matchup>.*<week>[0-9]*</week>" yahoo-api-test-results/teams-matchups.xml | grep -o "<week>[0-9]*</week>" | head -5
    
    echo ""
fi

# Analyze scoreboard structure
if [ -f "yahoo-api-test-results/scoreboard.xml" ]; then
    echo "Scoreboard structure analysis:"
    
    # Count matchups
    matchup_count=$(grep -c "<matchup>" yahoo-api-test-results/scoreboard.xml || echo "0")
    echo "- Total <matchup> elements: $matchup_count"
    
    # Check structure
    echo "- Checking for scoreboard > matchups structure:"
    grep -q "<scoreboard>.*<matchups>" yahoo-api-test-results/scoreboard.xml && echo "  Found!" || echo "  Not found"
    
    echo ""
fi

echo "All results saved to yahoo-api-test-results/"
echo ""
echo "To view formatted XML:"
echo "  xmllint --format yahoo-api-test-results/teams-matchups.xml | less"
