-- Yahoo Fantasy Bot Transaction Diagnostics
-- Run this script to check transaction processing status

-- 1. Check OAuth token status
SELECT 
    'OAuth Token Status' as check_type,
    CASE 
        WHEN COUNT(*) = 0 THEN 'NO TOKEN FOUND - Need to authenticate!'
        WHEN (retrieved + expires_in * 1000) > EXTRACT(EPOCH FROM NOW()) * 1000 THEN 'Token is VALID'
        ELSE 'Token is EXPIRED - needs refresh'
    END as status,
    CASE 
        WHEN COUNT(*) > 0 THEN 
            'Retrieved: ' || to_timestamp(retrieved/1000)::text || 
            ', Expires: ' || to_timestamp((retrieved + expires_in * 1000)/1000)::text
        ELSE 'No token data'
    END as details
FROM token 
WHERE retrieved = (SELECT MAX(retrieved) FROM token);

-- 2. Check last transaction check time
SELECT 
    'Last Transaction Check' as check_type,
    CASE 
        WHEN time IS NULL THEN 'NEVER CHECKED'
        WHEN time = -1 THEN 'ERROR STATE (-1)'
        ELSE 'Checked ' || EXTRACT(EPOCH FROM NOW() - to_timestamp(time/1000))::int || ' seconds ago'
    END as status,
    CASE 
        WHEN time IS NOT NULL AND time != -1 THEN to_timestamp(time/1000)::text
        ELSE 'No valid timestamp'
    END as last_check_time
FROM latest_time_checked 
ORDER BY time DESC 
LIMIT 1;

-- 3. Check recent message history (last 24 hours)
SELECT 
    'Recent Messages (24h)' as check_type,
    COUNT(*) as total_messages,
    COUNT(CASE WHEN success = true THEN 1 END) as successful,
    COUNT(CASE WHEN success = false THEN 1 END) as failed
FROM message_history
WHERE timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000);

-- 4. Recent transaction messages
SELECT 
    'Transaction Type' as category,
    transaction_type,
    COUNT(*) as count,
    MAX(to_timestamp(timestamp/1000)) as most_recent
FROM message_history
WHERE message_type = 'TRANSACTION'
  AND timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000)
GROUP BY transaction_type
ORDER BY most_recent DESC;

-- 5. Recent errors
SELECT 
    'Recent Errors' as category,
    to_timestamp(timestamp/1000) as error_time,
    messaging_service,
    transaction_type,
    error_message
FROM message_history
WHERE success = false
  AND timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000)
ORDER BY timestamp DESC
LIMIT 10;

-- 6. Configured messaging services
SELECT 
    'Messaging Services' as check_type,
    COUNT(DISTINCT messaging_service) as configured_services,
    string_agg(DISTINCT messaging_service, ', ') as services
FROM message_history
WHERE timestamp > (EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000);

-- 7. Alert configuration
SELECT 
    'Configured Alerts' as check_type,
    COUNT(*) as total_alerts,
    string_agg(alert_type || ' at ' || hour || ':' || LPAD(minute::text, 2, '0'), ', ') as alert_times
FROM alerts;

-- 8. Transaction processing timeline (last 10 checks)
SELECT 
    'Check Timeline' as category,
    to_timestamp(time/1000) as check_time,
    LEAD(time) OVER (ORDER BY time) - time as ms_until_next_check,
    CASE 
        WHEN LEAD(time) OVER (ORDER BY time) - time > 30000 THEN 'GAP DETECTED'
        ELSE 'Normal'
    END as status
FROM latest_time_checked
ORDER BY time DESC
LIMIT 10;
