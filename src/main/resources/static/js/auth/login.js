document.getElementById("login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const errorMessage = document.getElementById("error-message");
    errorMessage.textContent = "";

    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;

    const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
    });

    if (!response.ok) {
        errorMessage.textContent = "이메일 또는 비밀번호가 올바르지 않습니다.";
        return;
    }

    const tokens = await response.json();
    saveTokens(tokens.accessToken, tokens.refreshToken);
    window.location.href = "/";
});
