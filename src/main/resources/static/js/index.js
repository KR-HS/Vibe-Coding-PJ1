async function renderLoggedIn() {
    const response = await authFetch("/api/users/me");
    if (!response.ok) {
        renderLoggedOut();
        return;
    }
    const user = await response.json();

    document.getElementById("welcome-message").textContent = `${user.name}님, 환영합니다.`;
    document.getElementById("nav-links").innerHTML = `
        <a class="btn btn-secondary" href="/board/list.html">게시판</a>
        <a class="btn btn-secondary" href="/mypage.html">마이페이지</a>
        ${user.role === "ADMIN" ? `<a class="btn btn-secondary" href="/admin/users.html">관리자</a>` : ""}
        <button id="logout-button" class="btn btn-secondary">로그아웃</button>
    `;
    document.getElementById("home-actions").innerHTML = `<a class="btn" href="/board/list.html">게시판 바로가기</a>`;
    document.getElementById("logout-button").addEventListener("click", async () => {
        await authFetch("/api/auth/logout", { method: "POST" });
        clearTokens();
        window.location.reload();
    });
}

function renderLoggedOut() {
    clearTokens();
    document.getElementById("welcome-message").textContent = "로그인하고 게시판을 이용해보세요.";
    document.getElementById("nav-links").innerHTML = `
        <a class="btn btn-secondary" href="/board/list.html">게시판</a>
        <a class="btn btn-secondary" href="/auth/login.html">로그인</a>
        <a class="btn" href="/auth/signup.html">회원가입</a>
    `;
    document.getElementById("home-actions").innerHTML = `
        <a class="btn btn-secondary" href="/board/list.html">게시판 둘러보기</a>
        <a class="btn btn-secondary" href="/auth/login.html">로그인</a>
        <a class="btn" href="/auth/signup.html">회원가입</a>
    `;
}

if (isLoggedIn()) {
    renderLoggedIn();
} else {
    renderLoggedOut();
}
