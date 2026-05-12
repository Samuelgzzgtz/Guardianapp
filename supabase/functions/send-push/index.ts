import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL          = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY  = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const FIREBASE_PROJECT_ID   = Deno.env.get("FIREBASE_PROJECT_ID")!;
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

  // Safe base64url encode — no spread of large arrays
  const b64url = (data: string | Uint8Array): string => {
    const str = typeof data === "string" ? data : Array.from(data, b => String.fromCharCode(b)).join("");
    return btoa(str).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  };

  const signingInput = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;

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

  // Fix: avoid spreading large Uint8Array into String.fromCharCode (stack overflow for RSA-2048)
  const sig = b64url(new Uint8Array(sigBytes));
  const jwt = `${signingInput}.${sig}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  const tokenData = await tokenRes.json();
  if (!tokenData.access_token) throw new Error(`FCM OAuth failed: ${JSON.stringify(tokenData)}`);
  return tokenData.access_token;
}

// ── Send FCM v1 notification ──────────────────────────────────────────────────
async function sendFcmNotification(
  accessToken: string,
  fcmToken: string,
  title: string,
  body: string,
  data: Record<string, string> = {}
): Promise<void> {
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
  if (!res.ok) {
    const err = await res.text();
    throw new Error(`FCM send failed (${res.status}): ${err}`);
  }
}

// ── Main handler ──────────────────────────────────────────────────────────────
serve(async (req) => {
  try {
    const payload = await req.json();
    // Support both Supabase Database Webhook (has `record`) and direct HTTP POST
    const record = payload.record ?? payload;
    const destinatarioId: number = record.fkusuario ?? record.userId;

    if (!destinatarioId) {
      return new Response(JSON.stringify({ error: "userId o fkusuario requerido" }), {
        status: 400, headers: { "Content-Type": "application/json" },
      });
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);
    const { data: user, error: dbErr } = await supabase
      .from("usuario")
      .select("fcmtoken, nombre")
      .eq("id", destinatarioId)
      .single();

    if (dbErr) {
      return new Response(JSON.stringify({ error: `DB error: ${dbErr.message}` }), {
        status: 500, headers: { "Content-Type": "application/json" },
      });
    }

    if (!user?.fcmtoken) {
      return new Response(JSON.stringify({ ok: false, reason: "no fcm token for user" }), {
        status: 200, headers: { "Content-Type": "application/json" },
      });
    }

    const accessToken = await getFcmAccessToken();

    await sendFcmNotification(
      accessToken,
      user.fcmtoken,
      record.titulo  ?? record.title  ?? "GuardianApp",
      record.cuerpo  ?? record.mensaje ?? record.body ?? "",
      { notificacion_id: String(record.id ?? "") }
    );

    return new Response(JSON.stringify({ ok: true, usuario: user.nombre }), {
      status: 200, headers: { "Content-Type": "application/json" },
    });

  } catch (e) {
    return new Response(JSON.stringify({ error: String(e) }), {
      status: 500, headers: { "Content-Type": "application/json" },
    });
  }
});
