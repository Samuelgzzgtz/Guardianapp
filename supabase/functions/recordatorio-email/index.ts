/**
 * recordatorio-email — Envía correos de recordatorio de pago via Resend.
 *
 * Configurar en Supabase Dashboard → Edge Functions → Secrets:
 *   RESEND_API_KEY  = re_xxxxxxxxxxxx   (obtener en https://resend.com)
 *   FROM_EMAIL      = noreply@tudominio.com  (dominio verificado en Resend)
 *
 * Invocar desde la app: POST /functions/v1/recordatorio-email (con service_role key)
 */
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL         = Deno.env.get("SUPABASE_URL")!
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
const RESEND_API_KEY       = Deno.env.get("RESEND_API_KEY") ?? ""
const FROM_EMAIL           = Deno.env.get("FROM_EMAIL") ?? "noreply@guardianapp.com"

serve(async (_req) => {
  if (!RESEND_API_KEY) {
    return new Response(
      JSON.stringify({ error: "RESEND_API_KEY no configurado. Ver comentarios en el Edge Function." }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    )
  }

  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY)

  const today   = new Date()
  const in7Days = new Date(today)
  in7Days.setDate(today.getDate() + 7)
  const todayStr   = today.toISOString().split("T")[0]
  const in7DaysStr = in7Days.toISOString().split("T")[0]

  // Cuotas pendientes con vencimiento en los próximos 7 días
  const { data: cuotas, error } = await supabase
    .from("cuota")
    .select("id, fkusuario, monto, fechavencimiento, periodo")
    .eq("estatus", "pendiente")
    .gte("fechavencimiento", todayStr)
    .lte("fechavencimiento", in7DaysStr)

  if (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 })
  }

  const results: { userId: number; email: string; sent: boolean; error?: string }[] = []

  for (const cuota of cuotas ?? []) {
    const { data: usuario } = await supabase
      .from("usuario")
      .select("nombre, email")
      .eq("id", cuota.fkusuario)
      .single()

    if (!usuario?.email) continue

    const daysLeft = Math.round(
      (new Date(cuota.fechavencimiento).getTime() - today.getTime()) / 86_400_000
    )

    const urgencyText = daysLeft === 0
      ? "⚠️ Tu cuota mensual vence HOY"
      : `Tu cuota mensual vence en ${daysLeft} día(s)`

    const emailBody = buildEmailHtml({
      nombre:    usuario.nombre,
      monto:     cuota.monto,
      periodo:   cuota.periodo,
      daysLeft,
      urgencyText,
      fechaVencimiento: cuota.fechavencimiento,
    })

    const res = await fetch("https://api.resend.com/emails", {
      method:  "POST",
      headers: {
        "Authorization": `Bearer ${RESEND_API_KEY}`,
        "Content-Type":  "application/json",
      },
      body: JSON.stringify({
        from:    FROM_EMAIL,
        to:      [usuario.email],
        subject: `${urgencyText} — GuardianApp`,
        html:    emailBody,
      }),
    })

    if (res.ok) {
      results.push({ userId: cuota.fkusuario, email: usuario.email, sent: true })
    } else {
      const errBody = await res.text()
      results.push({ userId: cuota.fkusuario, email: usuario.email, sent: false, error: errBody })
    }
  }

  return new Response(
    JSON.stringify({ processed: results.length, results }),
    { headers: { "Content-Type": "application/json" } }
  )
})

function buildEmailHtml(p: {
  nombre: string
  monto: number
  periodo: string
  daysLeft: number
  urgencyText: string
  fechaVencimiento: string
}): string {
  const color = p.daysLeft === 0 ? "#C62828" : p.daysLeft <= 3 ? "#F57F17" : "#1565C0"
  return `<!DOCTYPE html>
<html lang="es">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>Recordatorio de pago</title></head>
<body style="margin:0;padding:0;background:#F0F4F8;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0">
    <tr><td align="center" style="padding:40px 16px;">
      <table width="560" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.08);">
        <tr><td style="background:linear-gradient(135deg,#1A1A2E,#0F3460);padding:32px;text-align:center;">
          <svg width="48" height="48" viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="18" cy="18" r="18" fill="#1A1A2E"/>
            <path d="M18 8L10 12v8c0 4.4 3.4 8.5 8 9.5 4.6-1 8-5.1 8-9.5v-8l-8-4z" fill="#1A73E8" opacity="0.9"/>
            <path d="M14 18l2.5 2.5L22 15" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <h1 style="color:#fff;margin:12px 0 4px;font-size:22px;font-weight:700;">GuardianApp</h1>
          <p style="color:rgba(255,255,255,0.7);margin:0;font-size:13px;">Sistema de gestión residencial</p>
        </td></tr>
        <tr><td style="padding:40px 32px;">
          <p style="color:#374151;font-size:16px;margin:0 0 8px;">Hola, <strong>${p.nombre}</strong></p>
          <div style="background:${color}15;border-left:4px solid ${color};border-radius:8px;padding:16px 20px;margin:24px 0;">
            <p style="color:${color};font-weight:700;font-size:15px;margin:0 0 4px;">${p.urgencyText}</p>
            <p style="color:#374151;margin:0;font-size:14px;">Periodo: ${p.periodo} · Vence: ${p.fechaVencimiento}</p>
          </div>
          <table width="100%" style="background:#F9FAFB;border-radius:12px;padding:20px;margin:0 0 24px;" cellpadding="0" cellspacing="0">
            <tr>
              <td style="color:#6B7280;font-size:14px;">Monto a pagar</td>
              <td align="right" style="color:#111827;font-size:22px;font-weight:700;">$${p.monto?.toFixed(2)}</td>
            </tr>
          </table>
          <p style="color:#6B7280;font-size:14px;margin:0 0 24px;">
            Realiza tu pago desde la sección <strong>Cuenta</strong> en la app GuardianApp para evitar recargos por mora.
          </p>
          <div style="text-align:center;">
            <a href="guardianapp://cuenta" style="display:inline-block;background:linear-gradient(135deg,#1A73E8,#0F3460);color:#fff;font-size:15px;font-weight:600;padding:14px 36px;border-radius:12px;text-decoration:none;">
              Abrir la app
            </a>
          </div>
        </td></tr>
        <tr><td style="border-top:1px solid #F0F0F0;padding:20px 32px;text-align:center;">
          <p style="color:#9CA3AF;font-size:12px;margin:0;">
            © ${new Date().getFullYear()} GuardianApp — Este es un mensaje automático, no respondas este correo.
          </p>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`
}
