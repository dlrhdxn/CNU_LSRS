function formatDuration(totalSeconds) {
    const seconds = Math.max(0, Math.floor(totalSeconds));
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const remainder = seconds % 60;
    return `${hours}시간 ${minutes}분 ${remainder}초`;
}

function updateRemainingTimes() {
    const elapsedSeconds = Math.floor(performance.now() / 1000);
    document.querySelectorAll(".reservation-remaining").forEach((element) => {
        const start = Number(element.dataset.start);
        const end = Number(element.dataset.end);
        const serverNow = Number(element.dataset.now);
        const current = serverNow + elapsedSeconds * 1000;

        if (current < start) {
            element.textContent = `예약 시작까지 ${formatDuration((start - current) / 1000)}`;
        } else if (current < end) {
            element.textContent = `이용 종료까지 ${formatDuration((end - current) / 1000)}`;
        } else {
            element.textContent = "이용 시간이 종료되었습니다.";
        }
    });

    document.querySelectorAll(".qr-countdown").forEach((element) => {
        const deadlineRemaining = Number(element.dataset.seconds);
        const remaining = Math.max(0, deadlineRemaining - elapsedSeconds);
        element.textContent = remaining > 0
            ? `체크인 가능 시간 ${formatDuration(remaining)} 남음`
            : "체크인 가능 시간이 종료되었습니다. 페이지를 새로고침해 주세요.";
        if (remaining === 0) {
            const card = element.closest(".reservation-card");
            card?.querySelector("a[href*='/qr']")?.remove();
        }
    });
}

updateRemainingTimes();
window.setInterval(updateRemainingTimes, 1000);
