import { createClient } from "https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.112.4/+esm"

let client

export async function getSupabase() {
    if (client) return client

    const response = await fetch("/auth/config")
    if (!response.ok) throw new Error("Unable to load Supabase configuration")

    const config = await response.json()
    client = createClient(config.url, config.publishableKey, {
        auth: { detectSessionInUrl: true, flowType: "pkce" },
    })
    return client
}
