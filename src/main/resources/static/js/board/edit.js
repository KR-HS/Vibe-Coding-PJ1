const boardId = getQueryParam("id");

if (!isLoggedIn() || !boardId) {
    window.location.href = "/auth/login.html";
}

document.getElementById("cancel-link").href = `/board/detail.html?id=${boardId}`;

async function loadForEdit() {
    const [boardResponse, userResponse] = await Promise.all([
        fetch(`/api/boards/${boardId}`),
        authFetch("/api/users/me"),
    ]);

    if (!boardResponse.ok || !userResponse.ok) {
        window.location.href = `/board/detail.html?id=${boardId}`;
        return;
    }

    const board = await boardResponse.json();
    const user = await userResponse.json();

    if (board.authorId !== user.id) {
        window.location.href = `/board/detail.html?id=${boardId}`;
        return;
    }

    document.getElementById("category").value = board.category;
    document.getElementById("title").value = board.title;
    document.getElementById("content").value = board.content;
}

document.getElementById("edit-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const errorMessage = document.getElementById("error-message");
    errorMessage.textContent = "";

    const request = {
        title: document.getElementById("title").value,
        content: document.getElementById("content").value,
        category: document.getElementById("category").value,
    };

    const response = await authFetch(`/api/boards/${boardId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
    });

    if (!response.ok) {
        const body = await response.json().catch(() => null);
        errorMessage.textContent = body?.message || "게시글 수정에 실패했습니다. 입력값을 확인해주세요.";
        return;
    }

    window.location.href = `/board/detail.html?id=${boardId}`;
});

loadForEdit();
