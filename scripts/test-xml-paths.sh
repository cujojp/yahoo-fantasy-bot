#!/bin/bash

echo "=== Testing XML Path Structure ==="
echo ""

# Create a small test XML file with just one matchup
cat > test-matchup.xml << 'EOF'
<?xml version="1.0"?>
<root>
  <league>
    <current_week>1</current_week>
    <teams>
      <team>
        <matchups>
          <matchup>
            <week>1</week>
            <teams count="2">
              <team>
                <team_key>461.l.115188.t.1</team_key>
                <name>Team One</name>
                <win_probability>0.59</win_probability>
                <team_points>
                  <week>1</week>
                  <total>0.00</total>
                </team_points>
                <team_projected_points>
                  <week>1</week>
                  <total>113.56</total>
                </team_projected_points>
              </team>
              <team>
                <team_key>461.l.115188.t.3</team_key>
                <name>Team Two</name>
                <win_probability>0.41</win_probability>
                <team_points>
                  <week>1</week>
                  <total>1.80</total>
                </team_points>
                <team_projected_points>
                  <week>1</week>
                  <total>116.77</total>
                </team_projected_points>
              </team>
            </teams>
          </matchup>
          <matchup>
            <week>2</week>
            <teams count="2">
              <team><name>Team A</name></team>
              <team><name>Team B</name></team>
            </teams>
          </matchup>
        </matchups>
      </team>
    </teams>
  </league>
</root>
EOF

echo "Test 1: Getting matchup week (should be 1, not multiple values):"
xmllint --xpath "//matchup[1]/week/text()" test-matchup.xml 2>/dev/null || echo "Failed"

echo ""
echo "Test 2: Getting teams in matchup:"
xmllint --xpath "count(//matchup[1]/teams/team)" test-matchup.xml 2>/dev/null || echo "Failed"

echo ""
echo "Test 3: Getting team names:"
xmllint --xpath "//matchup[1]/teams/team/name/text()" test-matchup.xml 2>/dev/null || echo "Failed"

echo ""
echo "Test 4: Getting win probabilities:"
xmllint --xpath "//matchup[1]/teams/team/win_probability/text()" test-matchup.xml 2>/dev/null || echo "Failed"

echo ""
echo "Test 5: Getting projected points:"
xmllint --xpath "//matchup[1]/teams/team/team_projected_points/total/text()" test-matchup.xml 2>/dev/null || echo "Failed"

echo ""
echo "Test 6: Test the actual selector logic:"
echo "- First get matchups from first team"
echo "- Filter for week 1"
echo "- Get teams from each matchup"

# Clean up
rm -f test-matchup.xml

echo ""
echo "=== Key Finding ==="
echo "The issue is that .select('week').text() gets ALL text from nested week elements."
echo "We need .select('week').first().text() to get just the matchup's week."
