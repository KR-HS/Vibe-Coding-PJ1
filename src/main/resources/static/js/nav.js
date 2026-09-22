async function renderNav() {
    const navLinks = document.getElementById("nav-links");
    if (!navLinks) {
        return;
    }

    if (!isLoggedIn()) {
        renderLoggedOutNav(navLinks);
        return;
    }

    const response = await authFetch("/api/users/me");
    if (!response.ok) {
        renderLoggedOutNav(navLinks);
        return;
    }
    const user = await response.json();

    navLinks.innerHTML = `
        <a class="btn btn-secondary" href="/board/list.html">게시판</a>
        <a class="btn btn-secondary" href="/mypage.html">마이페이지</a>
        ${user.role === "ADMIN" ? `<a class="btn btn-secondary" href="/admin/users.html">관리자</a>` : ""}
        <button id="logout-button" class="btn btn-secondary">로그아웃</button>
    `;
    document.getElementById("logout-button").addEventListener("click", async () => {
        await authFetch("/api/auth/logout", { method: "POST" });
        clearTokens();
        window.location.href = "/";
    });
}

function renderLoggedOutNav(navLinks) {
    clearTokens();
    navLinks.innerHTML = `
        <a class="btn btn-secondary" href="/board/list.html">게시판</a>
        <a class="btn btn-secondary" href="/auth/login.html">로그인</a>
        <a class="btn" href="/auth/signup.html">회원가입</a>
    `;
}

renderNav();
