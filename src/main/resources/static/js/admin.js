document.addEventListener('DOMContentLoaded', () => {
    const message = document.getElementById('admin-message');
    const showError = (error) => {
        message.textContent = error.message;
        message.classList.add('error-message');
    };
    const request = async (action) => {
        message.textContent = '';
        message.classList.remove('error-message');
        try {
            await action();
        } catch (error) {
            showError(error);
        }
    };
    const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[char]);
    const formatDateTime = (value) => value ? new Date(value).toLocaleString('ko-KR') : '-';

    async function loadDashboard() {
        const dashboard = await api('/api/admin/seats');
        const metrics = [
            ['현재 이용 인원', `${dashboard.currentUsers}명`],
            ['잔여 좌석', `${dashboard.remainingSeats}석`],
            ['체크인 대기', `${dashboard.checkInPendingSeats}석`],
            ['가동률', `${dashboard.utilizationRate}%`]
        ];
        document.getElementById('dashboard-metrics').innerHTML = metrics
            .map(([label, value]) => `<article class="metric"><span>${label}</span><strong>${value}</strong></article>`)
            .join('');
        document.getElementById('seat-grid').innerHTML = dashboard.seats.map((seat) => `
            <article class="seat-card seat-${seat.status.toLowerCase()}">
                <strong>${escapeHtml(seat.seatCode)}</strong>
                <span>${seatStatusLabel(seat.status)}</span>
                ${seat.studentNo ? `<small>${escapeHtml(seat.studentNo)}</small>` : ''}
            </article>`).join('');
    }

    function seatStatusLabel(status) {
        return {
            AVAILABLE: '예약 가능',
            IN_USE: '사용 중',
            CHECK_IN_PENDING: '체크인 대기',
            UNAVAILABLE: '사용 불가'
        }[status] || status;
    }

    async function loadStudents(studentNo = '') {
        const query = studentNo ? `?studentNo=${encodeURIComponent(studentNo)}` : '';
        const students = await api(`/api/admin/users${query}`);
        const tbody = document.getElementById('student-results');
        tbody.innerHTML = students.map((student) => `
            <tr>
                <td>${student.userId}</td>
                <td>${escapeHtml(student.studentNo)}</td>
                <td>${escapeHtml(student.role)} · ${escapeHtml(student.accountStatus)}</td>
                <td>
                    <select class="user-role" aria-label="${escapeHtml(student.studentNo)} 역할">
                        ${['STUDENT', 'ADMIN', 'KIOSK'].map((role) =>
                            `<option value="${role}" ${role === student.role ? 'selected' : ''}>${role}</option>`
                        ).join('')}
                    </select>
                    <button type="button" class="button secondary update-role"
                        data-user-id="${student.userId}">역할 변경</button>
                </td>
                <td>${student.noShowCount}
                    ${student.role === 'STUDENT' ? `<button type="button" class="button secondary reset-no-show"
                        data-user-id="${student.userId}">횟수 초기화</button>` : ''}
                </td>
                <td>${student.restricted ? `제한 중 (${escapeHtml(formatDateTime(student.restrictionUntil))})` : '정상'}</td>
                <td>${student.currentReservations.map((reservation) => `
                    <div class="history-entry">
                        ${escapeHtml(reservation.seatCode)} · ${escapeHtml(reservation.status)}
                        · ${escapeHtml(formatDateTime(reservation.startAt))}
                        ${['RESERVED', 'CHECKED_IN'].includes(reservation.status)
                            ? `<button type="button" class="button secondary cancel-reservation"
                                data-reservation-id="${reservation.reservationId}">강제 취소</button>` : ''}
                    </div>`).join('')}
                    ${student.currentReservations.length ? '' : '<span>없음</span>'}
                </td>
                <td>
                    ${student.role === 'STUDENT' ? `
                        <input type="datetime-local" class="restriction-until"
                            aria-label="${escapeHtml(student.studentNo)} 제한 해제 시각">
                        <button type="button" class="button secondary update-restriction"
                            data-user-id="${student.userId}" data-restricted="true">제한 설정</button>
                        <button type="button" class="button secondary update-restriction"
                            data-user-id="${student.userId}" data-restricted="false">제한 해제</button>` : ''}
                </td>
            </tr>`).join('');
        if (students.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8">조회된 사용자가 없습니다.</td></tr>';
        }
    }

    async function loadPolicies() {
        const settings = await api('/api/admin/policies');
        const normal = settings.normal;
        document.getElementById('policy-summary').innerHTML = `
            <p>평상시: 오늘부터 ${normal.bookingDays}일 이내, 최대 ${normal.maxHours}시간</p>
            <p>시험기간: 오늘부터 ${settings.examBookingDays}일 이내, 최대 ${settings.examMaxHours}시간</p>
            <ul>${settings.exams.map((exam) => `<li>${escapeHtml(exam.startDate)} ~ ${escapeHtml(exam.endDate)}</li>`).join('')}</ul>
            ${settings.exams.length ? '' : '<p>설정된 시험기간이 없습니다.</p>'}`;
        document.getElementById('clear-exam-policy').disabled = settings.exams.length === 0;
        const activeExam = settings.exams[0];
        if (activeExam) {
            document.getElementById('exam-start-date').value = activeExam.startDate;
            document.getElementById('exam-end-date').value = activeExam.endDate;
        } else {
            document.getElementById('exam-start-date').value = '';
            document.getElementById('exam-end-date').value = '';
        }
    }

    document.getElementById('student-search-form').addEventListener('submit', (event) => {
        event.preventDefault();
        request(() => loadStudents(document.getElementById('student-no').value.trim()));
    });

    const cancellationDialog = document.getElementById('force-cancel-dialog');
    const cancellationForm = document.getElementById('force-cancel-form');
    let cancellationReservationId = null;

    document.getElementById('student-results').addEventListener('click', (event) => {
        const restrictionButton = event.target.closest('.update-restriction');
        const resetNoShowButton = event.target.closest('.reset-no-show');
        const roleButton = event.target.closest('.update-role');
        const cancelButton = event.target.closest('.cancel-reservation');
        if (restrictionButton) {
            request(async () => {
                const row = restrictionButton.closest('tr');
                const restricted = restrictionButton.dataset.restricted === 'true';
                const localDate = row.querySelector('.restriction-until').value;
                await api(`/api/admin/${restrictionButton.dataset.userId}/restriction`, {
                    method: 'PATCH',
                    body: {
                        restricted,
                        restrictionUntil: restricted && localDate ? `${localDate}:00` : null
                    }
                });
                await loadStudents(document.getElementById('student-no').value.trim());
                await loadDashboard();
            });
        } else if (resetNoShowButton) {
            request(async () => {
                await api(`/api/admin/${resetNoShowButton.dataset.userId}/no-show-count`, {
                    method: 'PATCH'
                });
                await loadStudents(document.getElementById('student-no').value.trim());
                message.textContent = '노쇼 횟수를 초기화했습니다.';
            });
        } else if (roleButton) {
            request(async () => {
                const row = roleButton.closest('tr');
                const role = row.querySelector('.user-role').value;
                await api(`/api/admin/${roleButton.dataset.userId}/role`, {
                    method: 'PATCH',
                    body: { role }
                });
                await loadStudents(document.getElementById('student-no').value.trim());
                message.textContent = '사용자 역할을 변경했습니다.';
            });
        } else if (cancelButton) {
            cancellationReservationId = cancelButton.dataset.reservationId;
            const row = cancelButton.closest('tr');
            const studentNo = row.cells[1].textContent;
            document.getElementById('force-cancel-reservation').textContent =
                `${studentNo} 학생의 예약 ${cancellationReservationId}번을 취소합니다.`;
            document.getElementById('force-cancel-reason').value = 'FACILITY_INSPECTION';
            document.getElementById('force-cancel-penalty').checked = false;
            cancellationDialog.showModal();
        }
    });

    document.getElementById('force-cancel-dismiss').addEventListener('click', () => {
        cancellationDialog.close();
    });

    cancellationForm.addEventListener('submit', (event) => {
        event.preventDefault();
        if (!cancellationReservationId) return;
        request(async () => {
            await api(`/api/admin/reservations/${encodeURIComponent(cancellationReservationId)}/cancel`, {
                method: 'POST',
                body: {
                    reason: document.getElementById('force-cancel-reason').value,
                    applyNoShowPenalty: document.getElementById('force-cancel-penalty').checked
                }
            });
            cancellationDialog.close();
            cancellationReservationId = null;
            message.textContent = '예약을 강제 취소했습니다.';
            await loadStudents(document.getElementById('student-no').value.trim());
            await loadDashboard();
        });
    });

    document.getElementById('statistics-form').addEventListener('submit', (event) => {
        event.preventDefault();
        request(async () => {
            const startAt = document.getElementById('statistics-start').value;
            const endAt = document.getElementById('statistics-end').value;
            const query = new URLSearchParams({ startAt, endAt });
            const statistics = await api(`/api/admin/statistics?${query}`);
            document.getElementById('statistics-average').textContent =
                `전체 평균 가동률: ${statistics.averageUtilizationRate}%`;
            document.getElementById('statistics-results').innerHTML = statistics.points.length
                ? statistics.points.map((point) => `<tr>
                    <td>${escapeHtml(formatDateTime(point.bucketStart))}</td>
                    <td>${point.userCount}명</td>
                    <td>${point.utilizationRate}%</td>
                </tr>`).join('')
                : '<tr><td colspan="3">해당 기간의 이용 데이터가 없습니다.</td></tr>';
        });
    });

    document.getElementById('policy-form').addEventListener('submit', (event) => {
        event.preventDefault();
        request(async () => {
            await api('/api/admin/policies', {
                method: 'PUT',
                body: {
                    startDate: document.getElementById('exam-start-date').value,
                    endDate: document.getElementById('exam-end-date').value
                }
            });
            await loadPolicies();
            message.textContent = '시험기간 정책을 저장했습니다.';
        });
    });

    document.getElementById('clear-exam-policy').addEventListener('click', () => {
        request(async () => {
            await api('/api/admin/policies/exam', { method: 'DELETE' });
            await loadPolicies();
            message.textContent = '시험기간 정책을 해제했습니다. 기존 예약은 변경되지 않습니다.';
        });
    });

    request(async () => {
        await Promise.all([loadDashboard(), loadStudents(), loadPolicies()]);
    });
    const now = new Date();
    const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    const toLocalInput = (date) => {
        const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
        return local.toISOString().slice(0, 16);
    };
    document.getElementById('statistics-end').value = toLocalInput(now);
    document.getElementById('statistics-start').value = toLocalInput(yesterday);
    window.setInterval(() => request(loadDashboard), 10_000);
});
