// 좌석 조회(US-03)·예약(US-04)·취소(US-05) 화면. 공통 api() 함수는 /js/api.js
(() => {
    const STATE_LABEL = {
        AVAILABLE: '예약 가능', IN_USE: '사용 중', MINE: '내 예약', RESERVED: '예약됨', UNAVAILABLE: '사용 불가',
    };
    const $ = (id) => document.getElementById(id);
    let current = null; // 마지막 조회 결과

    // 30분 단위 시간 선택지 (06:00 ~ 23:30)
    function timeOptions(select) {
        for (let m = 6 * 60; m <= 23 * 60 + 30; m += 30) {
            const t = String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
            select.add(new Option(t, t));
        }
    }

    function localDate(d) {
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }

    function initForm() {
        timeOptions($('startAt'));
        timeOptions($('endAt'));
        const now = new Date();
        $('date').value = localDate(now);
        // 기본값: 다음 30분 단위부터 2시간
        const next = Math.min(Math.ceil((now.getHours() * 60 + now.getMinutes()) / 30), 46);
        const startIdx = Math.max(0, next - 12);
        $('startAt').selectedIndex = Math.min(startIdx, $('startAt').length - 2);
        $('endAt').selectedIndex = Math.min($('startAt').selectedIndex + 4, $('endAt').length - 1);
    }

    function query() {
        return { date: $('date').value, startAt: $('startAt').value, endAt: $('endAt').value };
    }

    async function load() {
        const q = query();
        try {
            current = await api(`/api/seats?${new URLSearchParams(q)}`);
            render();
        } catch (e) {
            showNotice(e.message);
        }
    }

    function showNotice(message) {
        $('notice').textContent = message || '';
        $('notice').hidden = !message;
    }

    function render() {
        const p = current.policy;
        const exam = p.periodType === 'EXAM';
        $('policy').innerHTML = `현재 정책: <b>${exam ? '시험기간' : '평상시'}</b> · `
            + `${p.lastBookableDate}까지 예약 가능 · <b class="${exam ? 'emph' : ''}">최대 ${p.maxHours}시간</b>`;
        $('date').max = p.lastBookableDate;
        showNotice(current.bookable ? '' : current.notice);

        const grid = $('grid');
        grid.innerHTML = '';
        const byLocation = new Map();
        current.seats.forEach((s) => {
            if (!byLocation.has(s.location)) byLocation.set(s.location, []);
            byLocation.get(s.location).push(s);
        });
        byLocation.forEach((seats, location) => {
            const section = document.createElement('section');
            section.innerHTML = `<h2>${location}</h2>`;
            const row = document.createElement('div');
            row.className = 'seat-grid';
            seats.forEach((s) => {
                const btn = document.createElement('button');
                btn.type = 'button';
                btn.className = `seat ${s.state}`;
                btn.title = STATE_LABEL[s.state];
                btn.innerHTML = `${s.seatCode}<small>${STATE_LABEL[s.state]}</small>`;
                if (s.state === 'AVAILABLE' && current.bookable) {
                    btn.onclick = () => reserve(s);
                } else if (s.state === 'MINE') {
                    btn.onclick = () => openCancel(s);
                } else {
                    btn.disabled = true;
                }
                row.appendChild(btn);
            });
            section.appendChild(row);
            grid.appendChild(section);
        });
    }

    async function reserve(seat) {
        const q = query();
        if (!confirm(`${seat.seatCode} 좌석을 ${q.date} ${q.startAt}~${q.endAt}에 예약할까요?`)) return;
        try {
            await api('/api/reservations', { method: 'POST', body: { seatId: seat.seatId, ...q } });
            alert('예약이 확정되었습니다. 예약 시작 후 15분 안에 QR로 체크인해 주세요.');
        } catch (e) {
            alert(e.message); // 이미 예약됨·시간 겹침·이용 제한 등 서버 안내
        }
        load();
    }

    function openCancel(seat) {
        if (seat.myReservationStatus !== 'RESERVED') {
            alert('체크인한 예약은 취소할 수 없습니다.');
            return;
        }
        const dialog = $('cancelDialog');
        $('cancelTarget').textContent = `${seat.seatCode} · ${current.date} 예약`;
        dialog.onclose = async () => {
            if (dialog.returnValue !== 'confirm') return;
            try {
                const reason = $('cancelReason').value;
                await api(`/api/reservations/${seat.myReservationId}?reason=${reason}`, { method: 'DELETE' });
                alert('예약이 취소되었습니다.');
            } catch (e) {
                alert(e.message);
            }
            load();
        };
        dialog.showModal();
    }

    document.addEventListener('DOMContentLoaded', () => {
        initForm();
        $('search').addEventListener('submit', (e) => { e.preventDefault(); load(); });
        load();
    });
})();
