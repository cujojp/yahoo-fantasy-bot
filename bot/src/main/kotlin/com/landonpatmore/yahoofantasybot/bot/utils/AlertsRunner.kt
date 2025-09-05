/*
 * MIT License
 *
 * Copyright (c) 2025 Kaleb White
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.landonpatmore.yahoofantasybot.bot.utils

import com.landonpatmore.yahoofantasybot.bot.bridges.ConfigurationBridge
import com.landonpatmore.yahoofantasybot.bot.utils.alerts.CloseScoreAlert
import com.landonpatmore.yahoofantasybot.bot.utils.alerts.MatchUpAlert
import com.landonpatmore.yahoofantasybot.bot.utils.alerts.ScoreAlert
import com.landonpatmore.yahoofantasybot.bot.utils.alerts.StandingsAlert
import com.landonpatmore.yahoofantasybot.bot.utils.models.Configuration
import com.landonpatmore.yahoofantasybot.shared.database.models.Alert
import org.quartz.*
import org.quartz.JobBuilder.newJob
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.impl.StdSchedulerFactory
import org.quartz.impl.matchers.GroupMatcher
import java.util.*
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.TimeUnit


class AlertsRunner(private val configurationBridge: ConfigurationBridge) {

    private val scheduler = StdSchedulerFactory.getDefaultScheduler()
    private var lastAlertConfiguration: List<Alert> = emptyList()

    fun start() {
        println("[AlertsRunner] Starting AlertsRunner...")
        scheduler.start()
        println("[AlertsRunner] Quartz scheduler started")
        println("[AlertsRunner] Scheduler metadata: ${scheduler.metaData}")
        println("[AlertsRunner] Scheduler running: ${scheduler.isStarted}")
        println("[AlertsRunner] Scheduler standby: ${scheduler.isInStandbyMode}")
        
        configurationBridge.eventStream
            .ofType(Configuration.Alerts::class.java)
            .map {
                it.alerts
            }.subscribe { alerts ->
                println("[AlertsRunner] Received configuration update with ${alerts.size} alerts")
                
                // Only regenerate jobs if configuration actually changed
                if (!areAlertsEqual(lastAlertConfiguration, alerts)) {
                    println("[AlertsRunner] Configuration changed, regenerating jobs")
                    generateJobs(alerts)
                    lastAlertConfiguration = alerts.toList()
                } else {
                    println("[AlertsRunner] Configuration unchanged, keeping existing jobs")
                }
            }
        println("[AlertsRunner] AlertsRunner started and listening for configuration updates")
        
        // Start a periodic job status checker
        Observable.interval(30, 30, TimeUnit.SECONDS)
            .subscribe {
                checkJobStatuses()
            }
    }
    
    private fun checkJobStatuses() {
        println("[AlertsRunner] === JOB STATUS CHECK ===")
        println("[AlertsRunner] Current time: ${java.time.LocalDateTime.now()}")
        
        for (groupName: String in scheduler.jobGroupNames) {
            for (jobKey: JobKey in scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                val triggers = scheduler.getTriggersOfJob(jobKey)
                triggers.forEach { trigger ->
                    println("[AlertsRunner] Job ${jobKey.name}: Next fire time: ${trigger.nextFireTime}, State: ${scheduler.getTriggerState(trigger.key)}")
                }
            }
        }
        println("[AlertsRunner] ======================")
    }
    
    private fun areAlertsEqual(list1: List<Alert>, list2: List<Alert>): Boolean {
        if (list1.size != list2.size) return false
        
        val sorted1 = list1.sortedBy { it.uuid }
        val sorted2 = list2.sortedBy { it.uuid }
        
        return sorted1.zip(sorted2).all { (a1, a2) ->
            a1.type == a2.type &&
            a1.hour == a2.hour &&
            a1.minute == a2.minute &&
            a1.startMonth == a2.startMonth &&
            a1.endMonth == a2.endMonth &&
            a1.dayOfWeek == a2.dayOfWeek &&
            a1.uuid == a2.uuid
        }
    }

    private fun generateJobs(alerts: List<Alert>) {
        println("[AlertsRunner] Generating jobs for ${alerts.size} alerts")
        removeJobs(alerts.map { it.uuid })
        alerts.forEach { alert ->
            println("[AlertsRunner] Processing alert: type=${alert.type}, uuid=${alert.uuid}")
            generateCron(alert)?.let { cron ->
                println("[AlertsRunner] Generated cron for alert ${alert.uuid}: $cron")
                val jobClass = when (alert.type) {
                    Alert.SCORE -> ScoreAlert::class.java
                    Alert.CLOSE_SCORE -> CloseScoreAlert::class.java
                    Alert.STANDINGS -> StandingsAlert::class.java
                    Alert.MATCHUP -> MatchUpAlert::class.java
                    else -> null
                }
                println("[AlertsRunner] Job class for alert ${alert.uuid}: ${jobClass?.simpleName ?: "null"}")
                createJob(jobClass, alert.uuid, cron)
            } ?: println("[AlertsRunner] Invalid cron for alert ${alert.uuid}, not creating alert")
        }
    }

    // sec min hour ? start-end day *
    private fun generateCron(alert: Alert): String? {
        val day = getDay(alert.dayOfWeek)

        if (validateMinute(alert.minute) && validateHour(alert.hour) && validateMonth(alert.startMonth) && validateMonth(
                alert.endMonth
            ) && validateDay(day) // TODO: timezone validation once added
        ) {
            // Handle month ranges that wrap around the year (e.g., Aug-Mar)
            val monthRange = if (alert.startMonth > alert.endMonth) {
                // Wrap around: e.g., 8-3 becomes 8-12,1-3
                "${alert.startMonth}-12,1-${alert.endMonth}"
            } else {
                "${alert.startMonth}-${alert.endMonth}"
            }
            
            val cron = "0 ${alert.minute} ${alert.hour} ? $monthRange $day *"
            println("[AlertsRunner] Generated cron expression: $cron")
            return cron
        }
        return null
    }

    private fun validateMinute(minute: Int): Boolean {
        return minute in 0..59
    }

    private fun validateHour(hour: Int): Boolean {
        return hour in 0..23
    }

    private fun validateMonth(month: Int): Boolean {
        return month in 1..12
    }

    private fun validateDay(day: String?): Boolean {
        return day != null
    }

    private fun getDay(day: Int): String? {
        return when (day) {
            1 -> "SUN"
            2 -> "MON"
            3 -> "TUE"
            4 -> "WED"
            5 -> "THU"
            6 -> "FRI"
            7 -> "SAT"
            else -> null
        }
    }

    private fun removeJobs(uuids: List<String>) {
        println("[AlertsRunner] Removing existing jobs...")
        var removedCount = 0
        
        for (groupName: String in scheduler.jobGroupNames) {
            for (jobKey: JobKey in scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                // Remove all existing jobs to avoid conflicts
                scheduler.deleteJob(jobKey)
                removedCount++
                println("[AlertsRunner] Removed job: ${jobKey.name}")
            }
        }
        
        println("[AlertsRunner] Removed $removedCount existing jobs")
    }

    /**
     * Creates a job to be scheduled.
     * @param jobClass job class
     * @param cron cron string
     */
    private fun createJob(jobClass: Class<out org.quartz.Job>?, jobKey: String, cron: String) {
        if (jobClass == null) {
            println("[AlertsRunner] Job class is null for key $jobKey, skipping job creation")
            return
        }

        println("[AlertsRunner] Creating job for ${jobClass.simpleName} with key $jobKey and cron $cron")
        
        val jobDetail = newJob(jobClass)
            .withIdentity(jobKey)
            .build()

        val trigger = newTrigger()
            .startNow()
            .withSchedule(
                CronScheduleBuilder.cronSchedule(cron)
                    .inTimeZone(TimeZone.getTimeZone("UTC"))
            ).build()

        println("[AlertsRunner] Job detail created: ${jobDetail.key}, Trigger created with next fire time: ${trigger.nextFireTime}")
        scheduleJob(Job(jobDetail, trigger))
    }

    /**
     * Runs the jobs that were created.
     */
    private fun scheduleJob(job: Job) {
        try {
            println("[AlertsRunner] Attempting to schedule job ${job.jobDetail.key}")
            val date = scheduler.scheduleJob(job.jobDetail, job.trigger)
            println("[AlertsRunner] Successfully scheduled job ${job.jobDetail.key}, first fire time: $date")
        } catch (e: SchedulerException) {
            println("[AlertsRunner] Failed to schedule job ${job.jobDetail.key}: ${e.message}")
            e.printStackTrace()
        }
    }

    private class Job(val jobDetail: JobDetail, val trigger: Trigger)

}