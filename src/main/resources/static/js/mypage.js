const ROLE_LABELS = { USER: "일반회원", ADMIN: "관리자" };
const SMS_STATUS_LABELS = { SUCCESS: "발송 성공", FAILED: "발송 실패" };

if (!isLoggedIn()) {
    window.location.href = "/auth/login.html";
}

const state = { boardsPage: 0, commentsPage: 0, likesPage: 0, notificationsPage: 0 };

async function loadProfile() {
    const response = await authFetch("/api/users/me");
    if (!response.ok) {
        return;
    }
    const user = await response.json();
    document.getElementById("mypage-profile").innerHTML = `
        <h1>마이페이지</h1>
        <p>${escapeHtml(user.name)} (${escapeHtml(user.email)}) · ${ROLE_LABELS[user.role] || user.role}</p>
        <div class="phone-edit-form">
            <label for="phone-input">댓글 알림을 받을 전화번호 (국가 코드 포함, 예: +821012345678)</label>
            <div class="phone-edit-row">
                <input type="text" id="phone-input" value="${escapeHtml(user.phoneNumber || "")}" placeholder="+821012345678">
                <button type="button" id="phone-save-btn">저장</button>
            </div>
            <p class="error-message" id="phone-error"></p>
        </div>
    `;
    document.getElementById("phone-save-btn").addEventListener("click", savePhoneNumber);
}

async function savePhoneNumber() {
    const phoneNumber = document.getElementById("phone-input").value.trim();
    const errorMessage = document.getElementById("phone-error");
    errorMessage.textContent = "";

    const response = await authFetch("/api/users/me/phone", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phoneNumber }),
    });

    if (!response.ok) {
        const error = await response.json().catch(() => null);
        errorMessage.textContent = error?.message || "전화번호 저장에 실패했습니다.";
        return;
    }
    errorMessage.textContent = "저장되었습니다.";
}

async function loadMyBoards() {
    const response = await authFetch(`/api/users/me/boards?page=${state.boardsPage}&size=10`);
    if (!response.ok) {
        return;
    }
    const pageResponse = await response.json();
    renderMyBoards(pageResponse.content);
    renderPagination(document.getElementById("my-board-pagination"), pageResponse, (page) => {
        state.boardsPage = page;
        loadMyBoards();
    });
}

function renderMyBoards(rows) {
    const tbody = document.getElementById("my-board-list-body");
    const emptyMessage = document.getElementById("my-board-empty");

    if (rows.length === 0) {
        tbody.innerHTML = "";
        emptyMessage.hidden = false;
        return;
    }
    emptyMessage.hidden = true;

    tbody.innerHTML = rows.map((board) => `
        <tr>
            <td class="col-category"><span class="category-badge category-${board.category}">${categoryLabel(board.category)}</span></td>
            <td class="col-title"><a href="/board/detail.html?id=${board.id}">${escapeHtml(board.title)}</a></td>
            <td class="col-date">${formatDateTime(board.createdAt)}</td>
            <td class="col-views">${board.viewCount}</td>
        </tr>
    `).join("");
}

async function loadMyComments() {
    const response = await authFetch(`/api/users/me/comments?page=${state.commentsPage}&size=10`);
    if (!response.ok) {
        return;
    }
    const pageResponse = await response.json();
    renderMyComments(pageResponse.content);
    renderPagination(document.getElementById("my-comment-pagination"), pageResponse, (page) => {
        state.commentsPage = page;
        loadMyComments();
    });
}

function renderMyComments(rows) {
    const list = document.getElementById("my-comment-list");
    const emptyMessage = document.getElementById("my-comment-empty");

    if (rows.length === 0) {
        list.innerHTML = "";
        emptyMessage.hidden = false;
        return;
    }
    emptyMessage.hidden = true;

    list.innerHTML = rows.map((comment) => `
        <a class="comment my-comment-link" href="/board/detail.html?id=${comment.boardId}">
            <div class="comment-header">
                <span class="comment-author">게시글: ${escapeHtml(comment.boardTitle)}</span>
                <span class="comment-date">${formatDateTime(comment.createdAt)}</span>
            </div>
            <p class="comment-content">${escapeHtml(comment.content)}</p>
        </a>
    `).join("");
}

async function loadMyLikes() {
    const response = await authFetch(`/api/users/me/likes?page=${state.likesPage}&size=10`);
    if (!response.ok) {
        return;
    }
    const pageResponse = await response.json();
    renderMyLikes(pageResponse.content);
    renderPagination(document.getElementById("my-like-pagination"), pageResponse, (page) => {
        state.likesPage = page;
        loadMyLikes();
    });
}

function renderMyLikes(rows) {
    const tbody = document.getElementById("my-like-list-body");
    const emptyMessage = document.getElementById("my-like-empty");

    if (rows.length === 0) {
        tbody.innerHTML = "";
        emptyMessage.hidden = false;
        return;
    }
    emptyMessage.hidden = true;

    tbody.innerHTML = rows.map((board) => `
        <tr>
            <td class="col-category"><span class="category-badge category-${board.category}">${categoryLabel(board.category)}</span></td>
            <td class="col-title"><a href="/board/detail.html?id=${board.id}">${escapeHtml(board.title)}</a></td>
            <td class="col-author">${escapeHtml(board.authorName)}</td>
            <td class="col-date">${formatDateTime(board.createdAt)}</td>
            <td class="col-likes">${board.likeCount}</td>
        </tr>
    `).join("");
}

async function loadMyNotifications() {
    const response = await authFetch(`/api/users/me/notifications?page=${state.notificationsPage}&size=10`);
    if (!response.ok) {
        return;
    }
    const pageResponse = await response.json();
    renderMyNotifications(pageResponse.content);
    renderPagination(document.getElementById("my-notification-pagination"), pageResponse, (page) => {
        state.notificationsPage = page;
        loadMyNotifications();
    });
}

function renderMyNotifications(rows) {
    const tbody = document.getElementById("my-notification-list-body");
    const emptyMessage = document.getElementById("my-notification-empty");

    if (rows.length === 0) {
        tbody.innerHTML = "";
        emptyMessage.hidden = false;
        return;
    }
    emptyMessage.hidden = true;

    tbody.innerHTML = rows.map((notification) => `
        <tr>
            <td class="col-date">${formatDateTime(notification.createdAt)}</td>
            <td class="col-title">
                ${escapeHtml(notification.message)}
                ${notification.errorMessage ? `<div class="sms-error-message">${escapeHtml(notification.errorMessage)}</div>` : ""}
            </td>
            <td class="col-category"><span class="sms-status sms-status-${notification.status}">${SMS_STATUS_LABELS[notification.status] || notification.status}</span></td>
        </tr>
    `).join("");
}

function switchTab(tab) {
    document.getElementById("tab-boards").classList.toggle("active", tab === "boards");
    document.getElementById("tab-comments").classList.toggle("active", tab === "comments");
    document.getElementById("tab-likes").classList.toggle("active", tab === "likes");
    document.getElementById("tab-notifications").classList.toggle("active", tab === "notifications");
    document.getElementById("panel-boards").hidden = tab !== "boards";
    document.getElementById("panel-comments").hidden = tab !== "comments";
    document.getElementById("panel-likes").hidden = tab !== "likes";
    document.getElementById("panel-notifications").hidden = tab !== "notifications";
}

document.getElementById("tab-boards").addEventListener("click", () => switchTab("boards"));
document.getElementById("tab-comments").addEventListener("click", () => switchTab("comments"));
document.getElementById("tab-likes").addEventListener("click", () => switchTab("likes"));
document.getElementById("tab-notifications").addEventListener("click", () => switchTab("notifications"));

loadProfile();
loadMyBoards();
loadMyComments();
loadMyLikes();
loadMyNotifications();
