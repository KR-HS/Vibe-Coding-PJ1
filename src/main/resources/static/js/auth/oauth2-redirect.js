const params = new URLSearchParams(window.location.search);
const accessToken = params.get("accessToken");
const refreshToken = params.get("refreshToken");
const statusMessage = document.getElementById("status-message");

if (accessToken && refreshToken) {
    saveTokens(accessToken, refreshToken);
    window.location.href = "/";
} else {
    statusMessage.textContent = "로그인에 실패했습니다. 다시 시도해주세요.";
}
