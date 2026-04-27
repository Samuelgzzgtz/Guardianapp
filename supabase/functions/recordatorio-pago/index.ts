import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const FCM_SERVER_KEY = Deno.env.get("FCM_SERVER_KEY")!;

serve(async (_req) => {
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

  const today = new Date();
  const in7Days = new Date(today);
  in7Days.setDate(today.getDate() + 7);
  const in7DaysStr = in7Days.toISOString().split("T")[0];
  const todayStr = today.toISOString().split("T")[0];

  // Find unpaid cuotas due within 7 days
  const { data: cuotas, error } = await supabase
    .from("cuota")
    .select("id, fkusuario, monto, fechavencimiento")
    .eq("estatus", "pendiente")
    .gte("fechavencimiento", todayStr)
    .lte("fechavencimiento", in7DaysStr);

  if (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 });
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
      : `Tu cuota mensual vence en ${daysLeft} día(s). Monto: $${cuota.monto?.toFixed(2)}.`;

    const fcmRes = await fetch("https://fcm.googleapis.com/fcm/send", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `key=${FCM_SERVER_KEY}`,
      },
      body: JSON.stringify({
        to: usuario.fcmtoken,
        notification: {
          title: "Recordatorio de pago",
          body,
          sound: "default",
        },
        data: { type: "payment_reminder", cuotaId: String(cuota.id) },
      }),
    });

    results.push({ userId: cuota.fkusuario, sent: fcmRes.ok });
  }

  return new Response(
    JSON.stringify({ processed: results.length, results }),
    { headers: { "Content-Type": "application/json" } }
  );
});
