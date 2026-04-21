package com.example.gab.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
    const val SUPABASE_URL = "https://spbrzuxvlljowwjawmkv.supabase.co"
    const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNwYnJ6dXh2bGxqb3d3amF3bWt2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY2OTAzNjEsImV4cCI6MjA5MjI2NjM2MX0.BgdO7Efx4AxU-pO9OT6AhCRgmpWZhgN0kV2Y0yrsS0M"
    // Reemplaza con tu service_role key: Supabase Dashboard → Project Settings → API
    const val SUPABASE_SERVICE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNwYnJ6dXh2bGxqb3d3amF3bWt2Iiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3NjY5MDM2MSwiZXhwIjoyMDkyMjY2MzYxfQ.IfcYgVtvqY7FP6V_hrlcYYg3ZCf68G5p3l6SOu2yfEY"

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}
