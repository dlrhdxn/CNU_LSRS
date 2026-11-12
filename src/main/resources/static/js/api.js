/**
 * 화면에서 REST API를 호출하는 공통 함수.
 * 성공하면 응답 JSON(없으면 null)을 돌려주고, 실패하면 서버의 {code, message}를 담은 Error를 던진다.
 *
 * 사용 예:
 *   try {
 *     const reservation = await api('/api/reservations', { method: 'POST', body: { seatId, startAt, endAt } });
 *   } catch (e) {
 *     alert(e.message);   // 서버가 보낸 안내 메시지
 *   }
 */
async function api(url, { method = 'GET', body } = {}) {
    const options = { method, headers: {}, credentials: 'same-origin' };
    if (body !== undefined) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }

    const response = await fetch(url, options);
    const text = await response.text();
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch (ignored) {
            data = null; // JSON이 아닌 응답(예: HTML 오류 페이지)
        }
    }

    if (!response.ok) {
        const error = new Error((data && data.message) || '요청을 처리하지 못했습니다.');
        error.code = data && data.code;
        error.status = response.status;
        throw error;
    }
    return data;
}
