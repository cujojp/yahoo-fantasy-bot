-- Test alert that should fire immediately (every minute)
-- This creates an alert for the current day and near-current time

-- First, let's see what day of week it is (1=Sunday, 2=Monday, etc.)
-- Assuming it's Friday (day 6) based on the logs

-- Delete existing alerts
DELETE FROM alerts;

-- Insert a test alert that fires every minute for testing
-- Type 3 = Matchup alert
-- This will fire every minute during the current hour
INSERT INTO alerts (id, type, hour, minute, start_month, end_month, day_of_week)
VALUES 
  (gen_random_uuid(), 3, EXTRACT(HOUR FROM CURRENT_TIME AT TIME ZONE 'UTC')::integer, EXTRACT(MINUTE FROM CURRENT_TIME AT TIME ZONE 'UTC')::integer + 1, 1, 12, EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'UTC')::integer + 1);

-- Show what was inserted
SELECT * FROM alerts;
