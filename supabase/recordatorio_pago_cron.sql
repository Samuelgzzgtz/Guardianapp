-- GuardianApp — Payment Reminder Cron Job
-- Run this script in Supabase SQL Editor (Dashboard → SQL Editor)
--
-- Prerequisites (enable once from Dashboard → Database → Extensions):
--   pg_cron   — cron job scheduler
--   pg_net    — HTTP requests from SQL
--
-- Replace <SERVICE_ROLE_KEY> with the actual key from:
--   Supabase Dashboard → Settings → API → service_role (secret)

select cron.schedule(
  'recordatorio-pago-semanal',
  '0 9 */7 * *',   -- every 7 days at 09:00 UTC
  $$
  select net.http_post(
    url     := 'https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/recordatorio-pago',
    headers := jsonb_build_object(
      'Authorization', 'Bearer <SERVICE_ROLE_KEY>',
      'Content-Type',  'application/json'
    ),
    body := '{}'::jsonb
  )
  $$
);

-- Verify the job was registered:
--   select jobid, jobname, schedule, command from cron.job;

-- Remove the job if needed:
--   select cron.unschedule('recordatorio-pago-semanal');
