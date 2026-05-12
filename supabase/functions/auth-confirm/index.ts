import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL  = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_ANON = Deno.env.get("SUPABASE_ANON_KEY")!;

// GitHub Pages URL — update after enabling Pages in repo Settings → Pages → Branch: master /docs
const PAGE_BASE = "https://samuelgzzgtz.github.io/Guardianapp/confirm.html";

function redirect(url: string): Response {
  return new Response(null, { status: 302, headers: { "Location": url } });
}

serve(async (req) => {
  const url       = new URL(req.url);
  const error     = url.searchParams.get("error");
  const errorDesc = url.searchParams.get("error_description");
  const tokenHash = url.searchParams.get("token_hash");
  const type      = url.searchParams.get("type") ?? "signup";

  // Error from Supabase (e.g. link expired)
  if (error) {
    const msg = errorDesc
      ? decodeURIComponent(errorDesc.replace(/\+/g, " "))
      : "El enlace expiró o ya fue usado.";
    return redirect(`${PAGE_BASE}?status=error&msg=${encodeURIComponent(msg)}`);
  }

  // OTP / token_hash flow — verify server-side, then redirect
  if (tokenHash) {
    const supabase = createClient(SUPABASE_URL, SUPABASE_ANON);
    const { error: otpError } = await supabase.auth.verifyOtp({
      token_hash: tokenHash,
      type: type as "signup" | "recovery" | "email",
    });
    if (otpError) {
      return redirect(`${PAGE_BASE}?status=error&msg=${encodeURIComponent(otpError.message)}`);
    }
    return redirect(`${PAGE_BASE}?status=success`);
  }

  // No query params — hash fragment flow (access_token in URL hash, read client-side)
  return redirect(PAGE_BASE);
});
