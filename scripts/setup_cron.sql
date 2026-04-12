-- Run this in Supabase SQL Editor
-- This sets up pg_cron to trigger your GitHub Actions workflow at exactly 10:30 AM EET (07:30 UTC) on weekdays

-- Step 1: Enable the extensions (if not already enabled)
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net;

-- Step 2: Create the cron job that calls GitHub Actions API
-- Replace YOUR_GITHUB_PAT with a GitHub Personal Access Token (fine-grained, with "Actions: write" permission)
SELECT cron.schedule(
  'collect-fuel-prices',           -- job name
  '30 7 * * 1-5',                  -- 07:30 UTC = 10:30 EET, weekdays only
  $$
  SELECT net.http_post(
    url := 'https://api.github.com/repos/gediminasjuska/fuel_prices_lt/actions/workflows/collect-prices.yml/dispatches',
    headers := jsonb_build_object(
      'Authorization', 'Bearer YOUR_GITHUB_PAT',
      'Accept', 'application/vnd.github.v3+json',
      'User-Agent', 'supabase-pg-cron'
    ),
    body := jsonb_build_object('ref', 'master')
  );
  $$
);

-- To verify the job was created:
SELECT * FROM cron.job;

-- To remove the job later:
-- SELECT cron.unschedule('collect-fuel-prices');

-- To check job run history:
-- SELECT * FROM cron.job_run_details ORDER BY start_time DESC LIMIT 10;
