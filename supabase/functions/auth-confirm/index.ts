import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async (req) => {
  const url = new URL(req.url)
  const error    = url.searchParams.get("error")
  const errorDesc = url.searchParams.get("error_description")
  const code     = url.searchParams.get("code")   // PKCE flow: confirmación válida

  // Error explícito enviado por Supabase (token inválido o expirado)
  if (error) {
    const msg = errorDesc
      ? decodeURIComponent(errorDesc.replace(/\+/g, " "))
      : "El enlace de verificación es inválido o ya expiró."
    return new Response(buildPage("error", msg), {
      headers: { "Content-Type": "text/html; charset=utf-8" },
    })
  }

  // PKCE: Supabase redirigió aquí con un code → confirmación exitosa
  if (code) {
    return new Response(buildPage("success", ""), {
      headers: { "Content-Type": "text/html; charset=utf-8" },
    })
  }

  // Sin parámetros: acceso directo o flujo implícito (JS detecta el hash)
  return new Response(buildPage("detect", ""), {
    headers: { "Content-Type": "text/html; charset=utf-8" },
  })
})

// ── Page builder ──────────────────────────────────────────────────────────────
function buildPage(state: "success" | "error" | "detect", errorMsg: string): string {
  return `<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>GuardianApp — Verificación de cuenta</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    background: linear-gradient(135deg, #1A1A2E 0%, #16213E 50%, #0F3460 100%);
    min-height: 100vh;
    display: flex; align-items: center; justify-content: center;
    padding: 24px;
  }
  .card {
    background: #fff; border-radius: 24px; padding: 56px 48px;
    max-width: 480px; width: 100%; text-align: center;
    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  }
  .logo-wrap { display: flex; align-items: center; justify-content: center; gap: 10px; margin-bottom: 32px; }
  .logo-text { font-size: 18px; font-weight: 700; letter-spacing: 1px; color: #1A1A2E; }
  .icon-wrap {
    width: 88px; height: 88px; border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    margin: 0 auto 28px;
  }
  .icon-wrap.green { background: #E8F5E9; }
  .icon-wrap.red   { background: #FFEBEE; }
  .icon-wrap.gray  { background: #F3F4F6; }
  h1 { font-size: 24px; font-weight: 700; color: #1A1A2E; margin-bottom: 14px; line-height: 1.3; }
  p  { font-size: 15px; color: #6B7280; line-height: 1.7; margin-bottom: 32px; }
  .btn {
    display: inline-block;
    background: linear-gradient(135deg, #1A73E8, #0F3460);
    color: #fff; font-size: 15px; font-weight: 600;
    padding: 15px 40px; border-radius: 14px;
    text-decoration: none; letter-spacing: 0.3px;
    transition: opacity 0.2s; margin-bottom: 8px;
  }
  .btn:hover { opacity: 0.88; }
  .divider { height: 1px; background: #F0F0F0; margin: 28px 0; }
  .footer { font-size: 13px; color: #9CA3AF; }
  .footer a { color: #1A73E8; text-decoration: none; }
  @media (max-width: 480px) { .card { padding: 36px 24px; } h1 { font-size: 20px; } }
</style>
</head>
<body>
<div class="card">
  <div class="logo-wrap">
    <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
      <circle cx="18" cy="18" r="18" fill="#1A1A2E"/>
      <path d="M18 8L10 12v8c0 4.4 3.4 8.5 8 9.5 4.6-1 8-5.1 8-9.5v-8l-8-4z" fill="#1A73E8" opacity="0.9"/>
      <path d="M14 18l2.5 2.5L22 15" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
    <span class="logo-text">GuardianApp</span>
  </div>

  <div class="icon-wrap ${state === "error" ? "red" : state === "success" ? "green" : "gray"}" id="iconWrap">
    ${state === "success" ? iconCheck() : state === "error" ? iconX() : iconLock()}
  </div>

  <h1 id="title">${
    state === "success" ? "¡Cuenta verificada exitosamente!" :
    state === "error"   ? "Link de verificación inválido" :
    "Verificando enlace..."
  }</h1>

  <p id="msg">${
    state === "success" ? "Tu correo electrónico ha sido confirmado.<br>Ya puedes iniciar sesión en GuardianApp." :
    state === "error"   ? errorMsg :
    "Por favor espera un momento."
  }</p>

  <a href="guardianapp://home" class="btn" id="btn" style="${state === "error" ? "display:none" : ""}">
    Abrir la aplicación
  </a>

  <div class="divider"></div>
  <div class="footer">¿Necesitas ayuda? <a href="mailto:soporte@guardianapp.com">Contáctanos</a></div>
</div>

<script>
  // Flujo implícito: Supabase pone el resultado en el hash del URL
  if (${state === "detect"}) {
    const hash = new URLSearchParams(window.location.hash.replace('#', ''));
    const accessToken = hash.get('access_token');
    const hashError   = hash.get('error');
    const hashErrorDesc = hash.get('error_description');

    if (accessToken) {
      // Confirmación exitosa (flujo implícito)
      setSuccess();
    } else if (hashError) {
      // Error en el hash
      const desc = hashErrorDesc || 'El enlace expiró o ya fue usado.';
      setError(desc);
    } else {
      // Acceso directo sin ningún parámetro válido
      setNoAuth();
    }
  }

  function setSuccess() {
    document.getElementById('iconWrap').className = 'icon-wrap green';
    document.getElementById('iconWrap').innerHTML = \`${iconCheck()}\`;
    document.getElementById('title').textContent = '¡Cuenta verificada exitosamente!';
    document.getElementById('msg').innerHTML = 'Tu correo electrónico ha sido confirmado.<br>Ya puedes iniciar sesión en GuardianApp.';
    document.getElementById('btn').style.display = '';
  }

  function setError(desc) {
    document.getElementById('iconWrap').className = 'icon-wrap red';
    document.getElementById('iconWrap').innerHTML = \`${iconX()}\`;
    document.getElementById('title').textContent = 'Link de verificación inválido';
    document.getElementById('msg').textContent = desc;
    document.getElementById('btn').style.display = 'none';
  }

  function setNoAuth() {
    document.getElementById('iconWrap').className = 'icon-wrap gray';
    document.getElementById('iconWrap').innerHTML = \`${iconLock()}\`;
    document.getElementById('title').textContent = 'No autenticado';
    document.getElementById('msg').textContent = 'Este enlace no es válido o ya fue utilizado. Si necesitas verificar tu correo, solicita un nuevo enlace desde la app.';
    document.getElementById('btn').style.display = 'none';
  }
</script>
</body>
</html>`
}

// ── SVG icons ─────────────────────────────────────────────────────────────────
function iconCheck() {
  return `<svg width="44" height="44" viewBox="0 0 44 44" fill="none">
    <circle cx="22" cy="22" r="22" fill="#4CAF50"/>
    <path d="M12 22.5L18.5 29L32 16" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`
}

function iconX() {
  return `<svg width="44" height="44" viewBox="0 0 44 44" fill="none">
    <circle cx="22" cy="22" r="22" fill="#EF5350"/>
    <path d="M15 15l14 14M29 15l-14 14" stroke="white" stroke-width="3" stroke-linecap="round"/>
  </svg>`
}

function iconLock() {
  return `<svg width="44" height="44" viewBox="0 0 44 44" fill="none">
    <circle cx="22" cy="22" r="22" fill="#9CA3AF"/>
    <rect x="13" y="20" width="18" height="14" rx="3" fill="white"/>
    <path d="M16 20v-4a6 6 0 0 1 12 0v4" stroke="white" stroke-width="2.5" stroke-linecap="round"/>
    <circle cx="22" cy="27" r="2" fill="#9CA3AF"/>
  </svg>`
}
