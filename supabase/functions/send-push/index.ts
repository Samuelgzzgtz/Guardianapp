import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const FCM_SERVER_KEY = Deno.env.get("FCM_SERVER_KEY") ?? "";
const SUPABASE_URL   = Deno.env.get("SUPABASE_URL")   ?? "";
const SUPABASE_KEY   = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

serve(async (req) => {
  const { record } = await req.json();
  const destinatarioId: number = record.fkusuario;

  const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);
  const { data: user } = await supabase
    .from("usuario")
    .select("fcm_token, nombre")
    .eq("id", destinatarioId)
    .single();

  if (!user?.fcm_token) {
    return new Response("no fcm token for user", { status: 200 });
  }

  const payload = {
    to: user.fcm_token,
    notification: {
      title: record.titulo  ?? "GuardianApp",
      body:  record.mensaje ?? "",
    },
    data: {
      notificacion_id: String(record.id),
    },
  };

  const fcmRes = await fetch("https://fcm.googleapis.com/fcm/send", {
    method: "POST",
    headers: {
      "Authorization": `key=${FCM_SERVER_KEY}`,
      "Content-Type":  "application/json",
    },
    body: JSON.stringify(payload),
  });

  const result = await fcmRes.json();
  return new Response(JSON.stringify(result), { status: 200 });
});
