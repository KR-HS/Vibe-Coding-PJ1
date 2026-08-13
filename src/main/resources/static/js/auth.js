const ACCESS_TOKEN_KEY = "accessToken";
const REFRESH_TOKEN_KEY = "refreshToken";

function saveTokens(accessToken, refreshToken) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

function getAccessToken() {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
}

function getRefreshToken() {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
}

function clearTokens() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
}

function isLoggedIn() {
    return !!getAccessToken();
}

async function reissueAccessToken() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
        return false;
    }
    const response = await fetch("/api/auth/reissue", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) {
        return false;
    }
    const tokens = await response.json();
    saveTokens(tokens.accessToken, tokens.refreshToken);
    return true;
}

async function authFetch(url, options = {}) {
    const headers = { ...(options.headers || {}), Authorization: `Bearer ${getAccessToken()}` };
    let response = await fetch(url, { ...options, headers });

    if (response.status === 401) {
        const reissued = await reissueAccessToken();
        if (reissued) {
            headers.Authorization = `Bearer ${getAccessToken()}`;
            response = await fetch(url, { ...options, headers });
        } else {
            clearTokens();
            window.location.href = "/auth/login.html";
            return response;
        }
    }

    return response;
}
