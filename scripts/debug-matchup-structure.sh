#!/bin/bash

echo "=== Debugging Matchup Team Structure ==="
echo ""

if [ ! -f "yahoo-api-test-results/teams-matchups.xml" ]; then
    echo "Run test-yahoo-api-direct.sh first to get the XML data"
    exit 1
fi

echo "1. Finding a week 1 matchup and examining team structure:"
echo "---"

# Extract just the first week 1 matchup
cat yahoo-api-test-results/teams-matchups.xml | \
    sed -n '/<matchup>/,/<\/matchup>/p' | \
    head -200 | \
    grep -E "<matchup>|<week>1</week>|<teams count|<team>|<team_key>|<team_id>|<name>|win_probability|team_points|team_projected_points|number_of_moves|number_of_trades|waiver_priority|</team>|</teams>|</matchup>" | \
    head -40

echo ""
echo "2. Checking what fields are available in matchup teams vs main teams:"
echo "---"

echo "Fields in a matchup team:"
# Get first team inside a matchup
cat yahoo-api-test-results/teams-matchups.xml | \
    sed -n '/<matchup>/,/<\/matchup>/p' | \
    sed -n '/<teams count="2">/,/<\/teams>/p' | \
    sed -n '/<team>/,/<\/team>/p' | \
    head -50 | \
    grep -o "<[^/>]*>" | \
    sort -u | \
    head -20

echo ""
echo "3. Sample team data from matchup:"
echo "---"
# Get the actual content of first team in matchup
cat yahoo-api-test-results/teams-matchups.xml | \
    sed -n '/<matchup>/,/<\/matchup>/p' | \
    sed -n '/<teams count="2">/,/<\/teams>/p' | \
    sed -n '/<team>/,/<\/team>/p' | \
    head -30

echo ""
echo "=== Key Finding ==="
echo "Check if matchup teams have all required fields:"
echo "- team_id"
echo "- name" 
echo "- win_probability"
echo "- team_points > total"
echo "- team_projected_points > total"
echo "- number_of_moves"
echo "- number_of_trades"
