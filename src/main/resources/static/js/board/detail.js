const boardId = getQueryParam("id");
let currentUser = null;

async function loadCurrentUser() {
    if (!isLoggedIn()) {
        return;
    }
    const response = await authFetch("/api/users/me");
    if (response.ok) {
        currentUser = await response.json();
    }
}

function canManage(authorId) {
    if (!currentUser) {
        return false;
    }
    return currentUser.id === authorId;
}

function canDelete(authorId) {
    if (!currentUser) {
        return false;
    }
    return currentUser.id === authorId || currentUser.role === "ADMIN";
}

async function loadBoard() {
    if (!boardId) {
        document.getElementById("not-found-message").hidden = false;
        return;
    }

    const response = await fetch(`/api/boards/${boardId}`);
    if (!response.ok) {
        document.getElementById("not-found-message").hidden = false;
        return;
    }
    const board = await response.json();
    renderBoard(board);
    await loadComments();
}

function renderBoard(board) {
    document.getElementById("board-detail").hidden = false;
    document.getElementById("detail-category").textContent = categoryLabel(board.category);
    document.getElementById("detail-category").className = `category-badge category-${board.category}`;
    document.getElementById("detail-title").textContent = board.title;
    document.getElementById("detail-author").textContent = board.authorName;
    document.getElementById("detail-date").textContent = formatDateTime(board.createdAt);
    document.getElementById("detail-views").textContent = `조회 ${board.viewCount}`;
    document.getElementById("detail-content").innerHTML = escapeHtml(board.content).replaceAll("\n", "<br>");

    const attachmentList = document.getElementById("attachment-list");
    if (board.attachments.length === 0) {
        attachmentList.innerHTML = "";
    } else {
        attachmentList.innerHTML = `
            <h3>첨부파일</h3>
            <ul>
                ${board.attachments.map((attachment) => `
                    <li data-attachment-id="${attachment.id}">
                        <a href="${attachment.downloadUrl}">${escapeHtml(attachment.originalFilename)}</a>
                        ${canDelete(board.authorId) ? `<button type="button" class="link-button attachment-delete-button">삭제</button>` : ""}
                    </li>
                `).join("")}
            </ul>
        `;
        if (canDelete(board.authorId)) {
            attachmentList.querySelectorAll(".attachment-delete-button").forEach((btn) => {
                btn.addEventListener("click", async () => {
                    const attachmentId = btn.closest("li").dataset.attachmentId;
                    if (!confirm("첨부파일을 삭제하시겠습니까?")) {
                        return;
                    }
                    const response = await authFetch(`/api/boards/${boardId}/attachments/${attachmentId}`, { method: "DELETE" });
                    if (response.ok) {
                        loadBoard();
                    }
                });
            });
        }
    }

    if (canManage(board.authorId) || canDelete(board.authorId)) {
        const actions = document.getElementById("board-owner-actions");
        actions.hidden = false;
        const editLink = document.getElementById("edit-link");
        if (canManage(board.authorId)) {
            editLink.href = `/board/edit.html?id=${board.id}`;
        } else {
            editLink.remove();
        }
    }

    document.getElementById("delete-button").addEventListener("click", async () => {
        if (!confirm("게시글을 삭제하시겠습니까?")) {
            return;
        }
        const response = await authFetch(`/api/boards/${boardId}`, { method: "DELETE" });
        if (response.ok) {
            window.location.href = "/board/list.html";
        }
    });

    if (isLoggedIn()) {
        document.getElementById("comment-form").hidden = false;
        document.getElementById("comment-login-notice").hidden = true;
    }
}

async function loadComments() {
    const response = await fetch(`/api/boards/${boardId}/comments`);
    if (!response.ok) {
        return;
    }
    const comments = await response.json();
    renderComments(comments);
}

function renderComments(comments) {
    const list = document.getElementById("comment-list");
    if (comments.length === 0) {
        list.innerHTML = `<p class="empty-message">등록된 댓글이 없습니다.</p>`;
        return;
    }

    list.innerHTML = comments.map((comment) => `
        <div class="comment" data-comment-id="${comment.id}">
            <div class="comment-header">
                <span class="comment-author">${escapeHtml(comment.authorName)}</span>
                <span class="comment-date">${formatDateTime(comment.createdAt)}</span>
            </div>
            <p class="comment-content">${escapeHtml(comment.content)}</p>
            <div class="comment-actions">
                ${canManage(comment.authorId) ? `<button type="button" class="link-button comment-edit-button">수정</button>` : ""}
                ${canDelete(comment.authorId) ? `<button type="button" class="link-button comment-delete-button">삭제</button>` : ""}
            </div>
        </div>
    `).join("");

    list.querySelectorAll(".comment-edit-button").forEach((btn) => {
        btn.addEventListener("click", () => startCommentEdit(btn.closest(".comment")));
    });
    list.querySelectorAll(".comment-delete-button").forEach((btn) => {
        btn.addEventListener("click", async () => {
            const commentId = btn.closest(".comment").dataset.commentId;
            if (!confirm("댓글을 삭제하시겠습니까?")) {
                return;
            }
            const response = await authFetch(`/api/comments/${commentId}`, { method: "DELETE" });
            if (response.ok) {
                loadComments();
            }
        });
    });
}

function startCommentEdit(commentElement) {
    const commentId = commentElement.dataset.commentId;
    const contentElement = commentElement.querySelector(".comment-content");
    const originalContent = contentElement.textContent;

    commentElement.querySelector(".comment-actions").hidden = true;
    contentElement.outerHTML = `
        <div class="comment-edit-form">
            <textarea rows="2" maxlength="1000">${escapeHtml(originalContent)}</textarea>
            <div class="comment-edit-actions">
                <button type="button" class="btn btn-secondary comment-cancel-button">취소</button>
                <button type="button" class="btn comment-save-button">저장</button>
            </div>
        </div>
    `;

    commentElement.querySelector(".comment-cancel-button").addEventListener("click", loadComments);
    commentElement.querySelector(".comment-save-button").addEventListener("click", async () => {
        const content = commentElement.querySelector("textarea").value.trim();
        if (!content) {
            return;
        }
        const response = await authFetch(`/api/comments/${commentId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ content }),
        });
        if (response.ok) {
            loadComments();
        }
    });
}

document.getElementById("comment-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const textarea = document.getElementById("comment-content");
    const content = textarea.value.trim();
    if (!content) {
        return;
    }

    const response = await authFetch(`/api/boards/${boardId}/comments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content }),
    });
    if (response.ok) {
        textarea.value = "";
        loadComments();
    }
});

(async () => {
    await loadCurrentUser();
    await loadBoard();
})();
