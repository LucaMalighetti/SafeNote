package com.example.safenote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://your-project-url.supabase.co",
        supabaseKey = "your-anon-key"
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}
