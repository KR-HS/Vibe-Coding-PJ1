if (!isLoggedIn()) {
    window.location.href = "/auth/login.html";
}

document.getElementById("write-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const errorMessage = document.getElementById("error-message");
    errorMessage.textContent = "";

    const request = {
        title: document.getElementById("title").value,
        content: document.getElementById("content").value,
        category: document.getElementById("category").value,
    };

    const formData = new FormData();
    formData.append("request", new Blob([JSON.stringify(request)], { type: "application/json" }));
    for (const file of document.getElementById("files").files) {
        formData.append("files", file);
    }

    const response = await authFetch("/api/boards", {
        method: "POST",
        body: formData,
    });

    if (!response.ok) {
        const body = await response.json().catch(() => null);
        errorMessage.textContent = body?.message || "게시글 등록에 실패했습니다. 입력값을 확인해주세요.";
        return;
    }

    const location = response.headers.get("Location");
    const boardId = location ? location.split("/").pop() : null;
    window.location.href = boardId ? `/board/detail.html?id=${boardId}` : "/board/list.html";
});
