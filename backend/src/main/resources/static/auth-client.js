import { createClient } from "https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2/+esm"

let client

export async function getSupabase() {
    if (client) return client

    const response = await fetch("/auth/config")
    if (!response.ok) throw new Error("Unable to load Supabase configuration")

    const config = await response.json()
    client = createClient(config.url, config.publishableKey)
    return client
}

export function nextPath() {
    const next = new URLSearchParams(window.location.search).get("next")
    return next?.startsWith("/") && !next.startsWith("//") ? next : "/docs/index.html"
}
