const CATEGORY_LABELS = { FREE: "자유", NOTICE: "공지", QNA: "질문" };

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function formatDateTime(isoString) {
    if (!isoString) {
        return "";
    }
    return isoString.replace("T", " ").slice(0, 16);
}

function categoryLabel(category) {
    return CATEGORY_LABELS[category] || category;
}

function getQueryParam(name) {
    return new URLSearchParams(window.location.search).get(name);
}

function renderPagination(container, pageResponse, onPageClick) {
    const { page, totalPages } = pageResponse;
    if (totalPages <= 1) {
        container.innerHTML = "";
        return;
    }

    const buttons = [];
    buttons.push(`<button type="button" class="page-btn" data-page="${page - 1}" ${page <= 0 ? "disabled" : ""}>이전</button>`);
    for (let i = 0; i < totalPages; i++) {
        buttons.push(
            `<button type="button" class="page-btn ${i === page ? "active" : ""}" data-page="${i}">${i + 1}</button>`
        );
    }
    buttons.push(`<button type="button" class="page-btn" data-page="${page + 1}" ${page >= totalPages - 1 ? "disabled" : ""}>다음</button>`);

    container.innerHTML = buttons.join("");
    container.querySelectorAll(".page-btn").forEach((btn) => {
        btn.addEventListener("click", () => {
            if (!btn.disabled) {
                onPageClick(Number(btn.dataset.page));
            }
        });
    });
}
