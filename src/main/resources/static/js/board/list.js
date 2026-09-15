const state = {
    page: Number(getQueryParam("page")) || 0,
    keyword: getQueryParam("keyword") || "",
    category: getQueryParam("category") || "",
};

function syncUrl() {
    const params = new URLSearchParams();
    if (state.page > 0) params.set("page", state.page);
    if (state.keyword) params.set("keyword", state.keyword);
    if (state.category) params.set("category", state.category);
    const query = params.toString();
    history.replaceState(null, "", query ? `?${query}` : window.location.pathname);
}

async function loadList() {
    syncUrl();
    const params = new URLSearchParams({ page: state.page, size: 10 });
    if (state.keyword) params.set("keyword", state.keyword);
    if (state.category) params.set("category", state.category);

    const response = await fetch(`/api/boards?${params.toString()}`);
    if (!response.ok) {
        return;
    }
    const pageResponse = await response.json();
    renderRows(pageResponse.content);
    renderPagination(document.getElementById("pagination"), pageResponse, (page) => {
        state.page = page;
        loadList();
    });
}

function renderRows(rows) {
    const tbody = document.getElementById("board-list-body");
    const emptyMessage = document.getElementById("empty-message");

    if (rows.length === 0) {
        tbody.innerHTML = "";
        emptyMessage.hidden = false;
        return;
    }
    emptyMessage.hidden = true;

    tbody.innerHTML = rows.map((board) => `
        <tr>
            <td class="col-category"><span class="category-badge category-${board.category}">${categoryLabel(board.category)}</span></td>
            <td class="col-title"><a href="/board/detail.html?id=${board.id}">${escapeHtml(board.title)}</a> <span class="comment-count">${board.commentCount > 0 ? `[${board.commentCount}]` : ""}</span></td>
            <td class="col-author">${escapeHtml(board.authorName)}</td>
            <td class="col-date">${formatDateTime(board.createdAt)}</td>
            <td class="col-views">${board.viewCount}</td>
        </tr>
    `).join("");
}

document.getElementById("category-select").value = state.category;
document.getElementById("keyword-input").value = state.keyword;

document.getElementById("search-form").addEventListener("submit", (event) => {
    event.preventDefault();
    state.page = 0;
    state.keyword = document.getElementById("keyword-input").value.trim();
    state.category = document.getElementById("category-select").value;
    loadList();
});

if (!isLoggedIn()) {
    document.getElementById("write-link").addEventListener("click", (event) => {
        event.preventDefault();
        window.location.href = "/auth/login.html";
    });
}

loadList();
