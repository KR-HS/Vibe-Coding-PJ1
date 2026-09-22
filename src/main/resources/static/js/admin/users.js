const PROVIDER_LABELS = { LOCAL: "자체", GOOGLE: "Google", NAVER: "Naver" };
const ROLE_LABELS = { USER: "일반회원", ADMIN: "관리자" };

if (!isLoggedIn()) {
    window.location.href = "/auth/login.html";
}

const state = { page: 0, keyword: "" };
let currentUserId = null;

async function loadCurrentUser() {
    const response = await authFetch("/api/users/me");
    if (response.ok) {
        const user = await response.json();
        currentUserId = user.id;
    }
}

async function loadUsers() {
    const params = new URLSearchParams({ page: state.page, size: 10 });
    if (state.keyword) params.set("keyword", state.keyword);

    const response = await authFetch(`/api/admin/users?${params.toString()}`);
    if (response.status === 403) {
        document.getElementById("error-message").textContent = "관리자만 접근할 수 있습니다.";
        setTimeout(() => { window.location.href = "/"; }, 1500);
        return;
    }
    if (!response.ok) {
        return;
    }
    const pageResponse = await response.json();
    renderUsers(pageResponse.content);
    renderPagination(document.getElementById("pagination"), pageResponse, (page) => {
        state.page = page;
        loadUsers();
    });
}

function renderUsers(rows) {
    const tbody = document.getElementById("user-list-body");
    const emptyMessage = document.getElementById("empty-message");

    if (rows.length === 0) {
        tbody.innerHTML = "";
        emptyMessage.hidden = false;
        return;
    }
    emptyMessage.hidden = true;

    tbody.innerHTML = rows.map((user) => `
        <tr data-user-id="${user.id}">
            <td class="col-author">${escapeHtml(user.email)}</td>
            <td class="col-author">${escapeHtml(user.name)}</td>
            <td class="col-category">${PROVIDER_LABELS[user.provider] || user.provider}</td>
            <td class="col-category">${ROLE_LABELS[user.role] || user.role}</td>
            <td class="col-date">${formatDateTime(user.createdAt)}</td>
            <td class="col-author">
                ${user.id === currentUserId
                    ? "-"
                    : `<button type="button" class="link-button role-toggle-button" data-next-role="${user.role === "ADMIN" ? "USER" : "ADMIN"}">${user.role === "ADMIN" ? "일반회원으로 변경" : "관리자로 변경"}</button>`}
            </td>
        </tr>
    `).join("");

    tbody.querySelectorAll(".role-toggle-button").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const userId = btn.closest("tr").dataset.userId;
            const nextRole = btn.dataset.nextRole;
            if (!confirm(`이 회원의 권한을 ${ROLE_LABELS[nextRole]}(으)로 변경하시겠습니까?`)) {
                return;
            }
            const response = await authFetch(`/api/admin/users/${userId}/role`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ role: nextRole }),
            });
            if (response.ok) {
                loadUsers();
            }
        });
    });
}

document.getElementById("search-form").addEventListener("submit", (event) => {
    event.preventDefault();
    state.page = 0;
    state.keyword = document.getElementById("keyword-input").value.trim();
    loadUsers();
});

(async () => {
    await loadCurrentUser();
    await loadUsers();
})();
