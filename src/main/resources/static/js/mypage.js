const ROLE_LABELS = { USER: "일반회원", ADMIN: "관리자" };

if (!isLoggedIn()) {
    window.location.href = "/auth/login.html";
}

const state = { boardsPage: 0, commentsPage: 0 };

async function loadProfile() {
    const response = await authFetch("/api/users/me");
    if (!response.ok) {
        return;
    }
    const user = await response.json();
    document.getElementById("mypage-profile").innerHTML = `
        <h1>마이페이지</h1>
        <p>${escapeHtml(user.name)} (${escapeHtml(user.email)}) · ${ROLE_LABELS[user.role] || user.role}</p>
    `;
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

function switchTab(tab) {
    const isBoards = tab === "boards";
    document.getElementById("tab-boards").classList.toggle("active", isBoards);
    document.getElementById("tab-comments").classList.toggle("active", !isBoards);
    document.getElementById("panel-boards").hidden = !isBoards;
    document.getElementById("panel-comments").hidden = isBoards;
}

document.getElementById("tab-boards").addEventListener("click", () => switchTab("boards"));
document.getElementById("tab-comments").addEventListener("click", () => switchTab("comments"));

loadProfile();
loadMyBoards();
loadMyComments();
