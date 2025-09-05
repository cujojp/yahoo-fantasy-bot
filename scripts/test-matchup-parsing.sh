#!/bin/bash

echo "=== Testing Matchup XML Parsing ==="
echo ""

if [ ! -f "yahoo-api-test-results/teams-matchups.xml" ]; then
    echo "ERROR: Run test-yahoo-api-direct.sh first to get the XML files"
    exit 1
fi

echo "1. Analyzing teams/matchups structure:"
echo "---"

# Count total teams
team_count=$(grep -c "<team>" yahoo-api-test-results/teams-matchups.xml)
echo "Total team elements: $team_count"

# Get unique team IDs
echo "Unique teams:"
grep "<team_id>" yahoo-api-test-results/teams-matchups.xml | sort -u | head -5

echo ""
echo "2. Matchup structure for first team:"
echo "---"

# Extract first team's matchups
xmllint --xpath "//teams/team[1]/matchups/matchup[position()<=3]" yahoo-api-test-results/teams-matchups.xml 2>/dev/null | xmllint --format - 2>/dev/null

echo ""
echo "3. Current week matchups:"
echo "---"

# Find current week
current_week=$(xmllint --xpath "//league/current_week/text()" yahoo-api-test-results/teams-matchups.xml 2>/dev/null)
echo "Current week: $current_week"
echo ""

# Count matchups for current week
echo "Matchups for week $current_week:"
xmllint --xpath "//teams/team[1]/matchups/matchup[week=$current_week]" yahoo-api-test-results/teams-matchups.xml 2>/dev/null | xmllint --format - 2>/dev/null

echo ""
echo "4. Comparing with scoreboard structure:"
echo "---"

if [ -f "yahoo-api-test-results/scoreboard.xml" ]; then
    scoreboard_matchups=$(grep -c "<matchup>" yahoo-api-test-results/scoreboard.xml)
    echo "Scoreboard has $scoreboard_matchups total matchup elements"
    
    echo ""
    echo "Scoreboard matchup structure:"
    xmllint --xpath "//scoreboard/matchups/matchup[1]" yahoo-api-test-results/scoreboard.xml 2>/dev/null | xmllint --format - 2>/dev/null | head -50
fi

echo ""
echo "=== Key Findings ==="
echo "1. Each team in /teams/matchups has ALL their matchups (14 weeks)"
echo "2. We need to filter for current week (week=$current_week)"
echo "3. Scoreboard only has current week matchups"
echo "4. Both have win_probability and projected points"
