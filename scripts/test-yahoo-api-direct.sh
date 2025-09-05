#!/bin/bash


# Direct Yahoo API testing using Railway database
echo "=== Yahoo Fantasy API Direct Test Script ==="
echo ""

# Use the public database URL from Railway
DATABASE_PUBLIC_URL="postgresql://postgres:iiFvyOtvSdVjvNIbUhqADexgokPhaasr@ballast.proxy.rlwy.net:37880/railway"

# Get the OAuth token from the database
echo "Fetching OAuth token from Railway database..."
ACCESS_TOKEN=$(psql "$DATABASE_PUBLIC_URL" -t -c "SELECT access_token FROM tokens ORDER BY retrieved_time DESC LIMIT 1;" 2>/dev/null | xargs)

if [ -z "$ACCESS_TOKEN" ]; then
    echo "ERROR: No OAuth token found in database."
    echo "Checking tokens table..."
    psql "$DATABASE_PUBLIC_URL" -c "SELECT retrieved_time, type, substring(access_token, 1, 20) || '...' as token_preview FROM tokens ORDER BY retrieved_time DESC LIMIT 3;"
    exit 1
fi


echo "✓ Token found!"
echo ""

# Get league info - we'll need to check Railway for these values
echo "Checking Railway environment for league info..."

# For now, let's use the values we can see from your logs
LEAGUE_ID="115188"
GAME_KEY="461"
LEAGUE_KEY="${GAME_KEY}.l.${LEAGUE_ID}"

echo "League Key: $LEAGUE_KEY"
echo ""

BASE_URL="https://fantasysports.yahooapis.com/fantasy/v2/league/${LEAGUE_KEY}"

# Function to make API request and save response
make_request() {
    local endpoint=$1
    local output_file=$2
    
    echo "=================="
    echo "Testing: ${BASE_URL}${endpoint}"
    echo ""
    
    # Make the request with full debugging
    response=$(curl -s -w "\n%{http_code}" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -H "Accept: application/xml" \
        "${BASE_URL}${endpoint}")
    
    # Extract status code and body
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    echo "Status: $http_code"
    
    if [ "$http_code" -eq 200 ]; then
        echo "$body" > "$output_file"
        echo "✓ Saved to: $output_file"
        echo ""
        
        # Pretty print first 100 lines
        echo "Response preview:"
        echo "---"
        if command -v xmllint &> /dev/null; then
            echo "$body" | xmllint --format - 2>/dev/null | head -100
        else
            echo "$body" | head -100
        fi
        echo "---"
    else
        echo "✗ ERROR Response:"
        echo "$body"
    fi
    
    echo ""
}

# Create output directory
mkdir -p yahoo-api-test-results

# Test the endpoints
echo "1. Testing /teams/matchups endpoint (NEW - what bot should use):"
make_request "/teams/matchups" "yahoo-api-test-results/teams-matchups.xml"

echo "2. Testing /scoreboard endpoint (OLD - what bot currently uses):"
make_request "/scoreboard" "yahoo-api-test-results/scoreboard.xml"

echo "3. Testing current week from league info:"
make_request "" "yahoo-api-test-results/league-info.xml"

echo ""
echo "=== Quick Analysis ==="
echo ""

if [ -f "yahoo-api-test-results/teams-matchups.xml" ]; then
    echo "Teams/Matchups structure:"
    echo "- Looking for: league > teams > team > matchups > matchup"
    echo "- Checking structure..."
    
    # Check for the expected path
    if grep -q "<league>.*<teams>.*<team>.*<matchups>.*<matchup>" yahoo-api-test-results/teams-matchups.xml; then
        echo "  ✓ Found expected structure!"
    else
        echo "  ✗ Expected structure not found"
        echo "  Actual structure sample:"
        grep -o "<[^>]*>" yahoo-api-test-results/teams-matchups.xml | head -20
    fi
    
    echo ""
fi

if [ -f "yahoo-api-test-results/scoreboard.xml" ]; then
    echo "Scoreboard structure:"
    echo "- Looking for: league > scoreboard > matchups > matchup"
    echo "- Checking structure..."
    
    if grep -q "<league>.*<scoreboard>.*<matchups>.*<matchup>" yahoo-api-test-results/scoreboard.xml; then
        echo "  ✓ Found expected structure!"
    else
        echo "  ✗ Expected structure not found"
    fi
    
    echo ""
fi

echo "All results saved to yahoo-api-test-results/"
echo ""
echo "To examine full responses:"
echo "  cat yahoo-api-test-results/teams-matchups.xml | xmllint --format - | less"
echo "  cat yahoo-api-test-results/scoreboard.xml | xmllint --format - | less"
