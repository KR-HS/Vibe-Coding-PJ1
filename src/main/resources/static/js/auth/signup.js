document.getElementById("signup-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const errorMessage = document.getElementById("error-message");
    errorMessage.textContent = "";

    const name = document.getElementById("name").value;
    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;

    const response = await fetch("/api/auth/signup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, email, password }),
    });

    if (!response.ok) {
        const body = await response.json().catch(() => null);
        errorMessage.textContent = body?.message || "회원가입에 실패했습니다. 입력값을 확인해주세요.";
        return;
    }

    window.location.href = "/auth/login.html";
});
