import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL         = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const FIREBASE_PROJECT_ID  = Deno.env.get("FIREBASE_PROJECT_ID")!;
const FIREBASE_CLIENT_EMAIL = Deno.env.get("FIREBASE_CLIENT_EMAIL")!;
const FIREBASE_PRIVATE_KEY  = Deno.env.get("FIREBASE_PRIVATE_KEY")!;

// ── FCM v1: generate OAuth2 access token from service account ─────────────────
async function getFcmAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header  = { alg: "RS256", typ: "JWT" };
  const payload = {
    iss:   FIREBASE_CLIENT_EMAIL,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud:   "https://oauth2.googleapis.com/token",
    iat:   now,
    exp:   now + 3600,
  };

  const encode = (obj: object) =>
    btoa(JSON.stringify(obj)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");

  const signingInput = `${encode(header)}.${encode(payload)}`;

  // Parse PEM private key
  const pem = FIREBASE_PRIVATE_KEY.replace(/\\n/g, "\n");
  const pemBody = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const keyBytes = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));

  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const sigBytes = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    new TextEncoder().encode(signingInput)
  );

  const sig = btoa(String.fromCharCode(...new Uint8Array(sigBytes)))
    .replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");

  const jwt = `${signingInput}.${sig}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  const tokenData = await tokenRes.json();
  if (!tokenData.access_token) throw new Error(`Token error: ${JSON.stringify(tokenData)}`);
  return tokenData.access_token;
}

// ── Send FCM v1 notification ──────────────────────────────────────────────────
async function sendFcmNotification(
  accessToken: string,
  fcmToken: string,
  title: string,
  body: string,
  data: Record<string, string> = {}
): Promise<boolean> {
  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`,
    {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${accessToken}`,
        "Content-Type":  "application/json",
      },
      body: JSON.stringify({
        message: {
          token: fcmToken,
          notification: { title, body },
          android: { notification: { sound: "default" } },
          data,
        },
      }),
    }
  );
  return res.ok;
}

// ── Main handler ──────────────────────────────────────────────────────────────
serve(async (_req) => {
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

  const today    = new Date();
  const in7Days  = new Date(today);
  in7Days.setDate(today.getDate() + 7);
  const todayStr    = today.toISOString().split("T")[0];
  const in7DaysStr  = in7Days.toISOString().split("T")[0];

  // Fetch unpaid cuotas due within 7 days
  const { data: cuotas, error } = await supabase
    .from("cuota")
    .select("id, fkusuario, monto, fechavencimiento")
    .eq("estatus", "pendiente")
    .gte("fechavencimiento", todayStr)
    .lte("fechavencimiento", in7DaysStr);

  if (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
  }

  let accessToken: string;
  try {
    accessToken = await getFcmAccessToken();
  } catch (e) {
    return new Response(JSON.stringify({ error: `FCM auth failed: ${e.message}` }), { status: 500 });
  }

  const results: { userId: number; sent: boolean }[] = [];

  for (const cuota of cuotas ?? []) {
    const { data: usuario } = await supabase
      .from("usuario")
      .select("fcmtoken, nombre")
      .eq("id", cuota.fkusuario)
      .single();

    if (!usuario?.fcmtoken) continue;

    const daysLeft = Math.round(
      (new Date(cuota.fechavencimiento).getTime() - today.getTime()) / 86_400_000
    );

    const body = daysLeft === 0
      ? "Tu cuota mensual vence HOY. Evita recargos."
      : `Tu cuota vence en ${daysLeft} día(s). Monto: $${cuota.monto?.toFixed(2)}.`;

    const sent = await sendFcmNotification(
      accessToken,
      usuario.fcmtoken,
      "Recordatorio de pago",
      body,
      { type: "payment_reminder", cuotaId: String(cuota.id) }
    );

    results.push({ userId: cuota.fkusuario, sent });
  }

  return new Response(
    JSON.stringify({ processed: results.length, results }),
    { headers: { "Content-Type": "application/json" } }
  );
});
