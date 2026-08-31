function csrfToken() {
    const cookie = document.cookie
        .split("; ")
        .find((entry) => entry.startsWith("XSRF-TOKEN="))
    return cookie ? decodeURIComponent(cookie.substring("XSRF-TOKEN=".length)) : null
}

async function requireSession() {
    const response = await fetch("/auth/session", { cache: "no-store", credentials: "same-origin" })
    if (response.ok) return

    window.location.replace("/auth/login")
    throw new Error("Authentication required")
}

await requireSession()

window.ui = SwaggerUIBundle({
    url: "/openapi",
    dom_id: "#swagger-ui",
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: "StandaloneLayout",
    requestInterceptor: async (request) => {
        const path = new URL(request.url, window.location.origin).pathname
        if (!path.startsWith("/api/")) return request

        await requireSession()
        if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(request.method.toUpperCase())) {
            request.headers["X-XSRF-TOKEN"] = csrfToken()
        }
        return request
    },
})

document.querySelector("#logout").addEventListener("click", async () => {
    await fetch("/auth/logout", {
        method: "POST",
        credentials: "same-origin",
        headers: { "X-XSRF-TOKEN": csrfToken() },
    })
    window.location.replace("/login.html")
})
