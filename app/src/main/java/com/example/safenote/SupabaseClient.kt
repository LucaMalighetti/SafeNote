package com.example.safenote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://gfjzpfxbmzemrsnqxbba.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdmanpwZnhibXplbXJzbnF4YmJhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM2NDgxNzMsImV4cCI6MjEwOTIyNDE3M30.RNcMfMehbWn4HSEH_ya2qyWF2EIeyNq2JLeAV-7Q6o8"
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}
