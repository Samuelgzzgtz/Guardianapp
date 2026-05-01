import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async (_req) => {
  return new Response(HTML_PAGE, {
    headers: { "Content-Type": "text/html; charset=utf-8" },
  })
})

const HTML_PAGE = `<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Cuenta Verificada — GuardianApp</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    background: linear-gradient(135deg, #1A1A2E 0%, #16213E 50%, #0F3460 100%);
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
  }
  .card {
    background: #FFFFFF;
    border-radius: 24px;
    padding: 56px 48px;
    max-width: 480px;
    width: 100%;
    text-align: center;
    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  }
  .logo-wrap {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    margin-bottom: 32px;
  }
  .logo-icon {
    width: 36px;
    height: 36px;
  }
  .logo-text {
    font-size: 18px;
    font-weight: 700;
    letter-spacing: 1px;
    color: #1A1A2E;
  }
  .icon-wrap {
    width: 88px;
    height: 88px;
    background: #E8F5E9;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 28px;
  }
  h1 {
    font-size: 24px;
    font-weight: 700;
    color: #1A1A2E;
    margin-bottom: 14px;
    line-height: 1.3;
  }
  p {
    font-size: 15px;
    color: #6B7280;
    line-height: 1.7;
    margin-bottom: 32px;
  }
  .btn {
    display: inline-block;
    background: linear-gradient(135deg, #1A73E8, #0F3460);
    color: #FFFFFF;
    font-size: 15px;
    font-weight: 600;
    padding: 15px 40px;
    border-radius: 14px;
    text-decoration: none;
    letter-spacing: 0.3px;
    transition: opacity 0.2s;
    margin-bottom: 8px;
  }
  .btn:hover { opacity: 0.88; }
  .divider { height: 1px; background: #F0F0F0; margin: 28px 0; }
  .footer { font-size: 13px; color: #9CA3AF; }
  .footer a { color: #1A73E8; text-decoration: none; }
  @media (max-width: 480px) {
    .card { padding: 36px 24px; }
    h1 { font-size: 20px; }
  }
</style>
</head>
<body>
<div class="card">
  <div class="logo-wrap">
    <svg class="logo-icon" viewBox="0 0 36 36" fill="none">
      <circle cx="18" cy="18" r="18" fill="#1A1A2E"/>
      <path d="M18 8L10 12v8c0 4.4 3.4 8.5 8 9.5 4.6-1 8-5.1 8-9.5v-8l-8-4z" fill="#1A73E8" opacity="0.9"/>
      <path d="M14 18l2.5 2.5L22 15" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
    <span class="logo-text">GuardianApp</span>
  </div>
  <div class="icon-wrap">
    <svg width="44" height="44" viewBox="0 0 44 44" fill="none">
      <circle cx="22" cy="22" r="22" fill="#4CAF50"/>
      <path d="M12 22.5L18.5 29L32 16" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
  </div>
  <h1>¡Cuenta verificada exitosamente!</h1>
  <p>Tu correo electrónico ha sido confirmado.<br>Ya puedes acceder a GuardianApp.</p>
  <a href="guardianapp://home" class="btn">Abrir la aplicación</a>
  <div class="divider"></div>
  <div class="footer">
    ¿Problemas para acceder? <a href="mailto:soporte@guardianapp.com">Contáctanos</a>
  </div>
</div>
</body>
</html>`
