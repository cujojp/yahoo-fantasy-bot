#!/usr/bin/env kotlin

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class Team(
    val name: String,
    val id: Int,
    val waiverPriority: Int?,
    val faabBalance: Int?,
    val numberOfMoves: Int,
    val numberOfTrades: Int,
    val winProbability: Double,
    val points: Double,
    val projectedPoints: Double
)

fun generateTeamData(team: Element): Team {
    try {
        println("[TEST] Generating team data from element: ${team.tagName()}")
        
        val id = team.select("team_id").text().toIntOrNull() ?: 0
        val name = team.select("name").text()
        val waiverPriority = team.select("waiver_priority").text().toIntOrNull()
        val faabBalance = team.select("faab_balance").text().toIntOrNull()
        val numberOfMoves = team.select("number_of_moves").text().toIntOrNull() ?: 0
        val numberOfTrades = team.select("number_of_trades").text().toIntOrNull() ?: 0
        val winProbability = team.select("win_probability").text().toDoubleOrNull()?.times(100) ?: 0.0
        val points = team.select("team_points").select("total").text().toDoubleOrNull() ?: 0.0
        val projectedPoints = team.select("team_projected_points").select("total").text().toDoubleOrNull() ?: 0.0
        
        println("[TEST] Extracted values:")
        println("  id: $id")
        println("  name: $name")
        println("  waiverPriority: $waiverPriority")
        println("  faabBalance: $faabBalance")
        println("  numberOfMoves: $numberOfMoves")
        println("  numberOfTrades: $numberOfTrades")
        println("  winProbability: $winProbability")
        println("  points: $points")
        println("  projectedPoints: $projectedPoints")
        
        if (name.isEmpty()) {
            println("[TEST] WARNING: Team name is empty")
        }
        
        return Team(
            name,
            id,
            waiverPriority,
            faabBalance,
            numberOfMoves,
            numberOfTrades,
            winProbability,
            points,
            projectedPoints
        )
    } catch (e: Exception) {
        println("[TEST] Error generating team data: ${e.message}")
        e.printStackTrace()
        throw e
    }
}

// Test with a sample team XML
val sampleTeamXml = """
<team>
 <team_key>461.l.115188.t.1</team_key>
 <team_id>1</team_id>
 <name>Dickstinctly</name>
 <url>https://football.fantasysports.yahoo.com/f1/115188/1</url>
 <team_logos>
  <team_logo>
   <size>large</size>
   <url>https://yahoofantasysports-res.cloudinary.com/image/upload/t_s192sq/fantasy-logos/aa1d862655730cc377d778f514686b4aff396324c90c6eb6ee7e1d1401b0b86f.png</url>
  </team_logo>
 </team_logos>
 <previous_season_team_rank>3</previous_season_team_rank>
 <division_id>1</division_id>
 <waiver_priority>8</waiver_priority>
 <number_of_moves>1</number_of_moves>
 <number_of_trades>0</number_of_trades>
 <roster_adds>
  <coverage_type>week</coverage_type>
  <coverage_value>1</coverage_value>
  <value>0</value>
 </roster_adds>
 <league_scoring_type>head</league_scoring_type>
 <draft_position>6</draft_position>
 <has_draft_grade>1</has_draft_grade>
 <draft_grade>B-</draft_grade>
 <draft_recap_url>https://football.fantasysports.yahoo.com/f1/115188/1/draftrecap</draft_recap_url>
 <managers>
  <manager>
   <manager_id>1</manager_id>
   <nickname>Kyle</nickname>
   <guid>PVDU5PLO4K6CET4GCFBHWMYQRA</guid>
   <image_url>https://s.yimg.com/ag/images/default_user_profile_pic_64sq.jpg</image_url>
   <felo_score>780</felo_score>
   <felo_tier>gold</felo_tier>
  </manager>
 </managers>
 <win_probability>0.50</win_probability>
 <team_points>
  <coverage_type>week</coverage_type>
  <week>1</week>
  <total>0.00</total>
 </team_points>
 <team_projected_points>
  <coverage_type>week</coverage_type>
  <week>1</week>
  <total>113.56</total>
 </team_projected_points>
</team>
"""

try {
    val doc = Jsoup.parse(sampleTeamXml)
    val teamElement = doc.select("team").first()!!
    val team = generateTeamData(teamElement)
    println("\n[TEST] Successfully created team: $team")
} catch (e: Exception) {
    println("\n[TEST] Failed to create team: ${e.message}")
    e.printStackTrace()
}
