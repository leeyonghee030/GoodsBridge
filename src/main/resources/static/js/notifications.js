(() => {
    // ==================================================
    // 1. HTML 요소 가져오기
    // ==================================================

    // 알림 버튼, 알림 패널, 닫기 버튼 등 HTML에서 필요한 요소를 가져온다.
    const trigger = document.getElementById('notification-trigger');
    const panel = document.getElementById('notification-panel');
    const closeButton = document.getElementById('notification-close');
    const badge = document.getElementById('notification-badge');
    const unreadLabel = document.getElementById('notification-unread-label');
    const list = document.getElementById('notification-list');

    // 필요한 요소 중 하나라도 존재하지 않으면
    // 알림 기능을 실행하지 않고 함수를 종료한다.
    if (!trigger || !panel || !closeButton || !badge || !unreadLabel || !list) return;


    // ==================================================
    // 2. 알림 상태 및 데이터 관리 변수
    // ==================================================

    // 현재 읽지 않은 알림 개수
    let unreadCount = 0;

    // "모두 읽음" 버튼을 JavaScript로 생성한다.
    // HTML에 미리 작성하지 않고 동적으로 생성하는 방식이다.
    const readAllButton = document.createElement('button');
    readAllButton.type = 'button';
    readAllButton.className = 'notification-read-all';
    readAllButton.textContent = '모두 읽음';
    readAllButton.hidden = true; // 읽지 않은 알림이 없으면 숨긴다.

    // 읽지 않은 알림 개수 표시 영역과
    // "모두 읽음" 버튼을 묶기 위한 그룹 요소
    const unreadGroup = document.createElement('span');
    unreadGroup.className = 'notification-unread-group';

    // 기존 unreadLabel을 그룹 요소로 교체한 뒤
    // label과 버튼을 그룹 안에 넣는다.
    unreadLabel.replaceWith(unreadGroup);
    unreadGroup.append(unreadLabel, readAllButton);

    // 현재 화면에 표시할 알림 목록
    let loadedNotifications = [];

    // 이미 수신한 알림 ID를 저장하여
    // 동일한 알림이 SSE로 중복 수신되는 것을 방지한다.
    const receivedNotificationIds = new Set();

    // 알림 목록을 서버에서 불러오는 중 새로 수신된 알림을 임시 보관한다.
    // 기존 조회 결과에 새 알림이 덮어써지는 문제를 방지하기 위한 용도다.
    const liveNotificationsDuringLoad = new Map();

    // SSE 연결 객체를 저장할 변수
    let eventSource;


    // ==================================================
    // 3. 읽지 않은 알림 개수 업데이트
    // ==================================================

    function setUnreadCount(count) {
        // 전달받은 값을 숫자로 변환하고 최소 0으로 제한한다.
        unreadCount = Math.max(0, Number(count) || 0);

        // 읽지 않은 알림이 0개이면 뱃지를 숨긴다.
        badge.hidden = unreadCount === 0;

        // 99개 초과 시 99+로 표시한다.
        badge.textContent = unreadCount > 99 ? '99+' : String(unreadCount);

        // 읽지 않은 알림 수에 따라 문구를 변경한다.
        unreadLabel.textContent = unreadCount
            ? `읽지 않은 알림 ${unreadCount}개`
            : '모두 확인했어요';

        // 읽지 않은 알림이 있을 때만 "모두 읽음" 버튼을 표시한다.
        readAllButton.hidden = unreadCount === 0;
    }


    // ==================================================
    // 4. 알림 생성 시간 표시 (상대 시간)
    // ==================================================

    function relativeTime(value) {
        // 전달받은 날짜 문자열을 Date 객체로 변환한다.
        const date = new Date(value);

        // 유효하지 않은 날짜라면 빈 문자열 반환
        if (Number.isNaN(date.getTime())) return '';

        // 현재 시간과 알림 생성 시간의 차이를 분 단위로 계산한다.
        // 음수가 나오면 0으로 처리한다.
        const minutes = Math.max(
            0,
            Math.floor((Date.now() - date.getTime()) / 60000)
        );

        // 1분 미만
        if (minutes < 1) return '방금 전';

        // 1시간 미만
        if (minutes < 60) return `${minutes}분 전`;

        // 시간 단위 계산
        const hours = Math.floor(minutes / 60);

        // 24시간 미만
        if (hours < 24) return `${hours}시간 전`;

        // 일 단위 계산
        const days = Math.floor(hours / 24);

        // 7일 미만
        if (days < 7) return `${days}일 전`;

        // 7일 이상이면 월/일 형식으로 표시한다.
        return new Intl.DateTimeFormat('ko-KR', {
            month: 'numeric',
            day: 'numeric'
        }).format(date);
    }


    // ==================================================
    // 5. 알림 목록 상태 메시지 출력
    // ==================================================

    function showState(message) {
        // 기존 목록을 모두 제거한다.
        list.replaceChildren();

        // 상태를 표시할 p 태그 생성
        const state = document.createElement('p');
        state.className = 'notification-state';
        state.textContent = message;

        // 목록 영역에 상태 메시지 추가
        list.append(state);
    }


    // ==================================================
    // 6. 알림 목록 화면 렌더링
    // ==================================================

    function renderNotifications() {
        // 기존 알림 화면을 초기화한다.
        list.replaceChildren();

        // 알림이 없다면 빈 목록 메시지를 출력하고 종료한다.
        if (loadedNotifications.length === 0) {
            showState('새로운 알림이 없습니다.');
            return;
        }

        // 알림 데이터 하나씩 화면 요소로 변환한다.
        loadedNotifications.forEach((notification) => {

            // 알림 카드 역할의 버튼 생성
            const card = document.createElement('button');
            card.type = 'button';
            card.className = 'notification-card';

            // 스크린 리더가 읽을 수 있는 접근성 라벨 설정
            card.setAttribute(
                'aria-label',
                `${notification.title || '알림'} 읽음 처리`
            );

            // 알림 제목과 시간 표시 영역
            const heading = document.createElement('span');
            heading.className = 'notification-card-header';

            // 읽지 않은 알림을 표시할 점
            const dot = document.createElement('span');
            dot.className = 'notification-unread-dot';
            dot.setAttribute('aria-hidden', 'true');

            // 알림 제목
            const title = document.createElement('span');
            title.className = 'notification-card-title';
            title.textContent = notification.title || '알림';

            // 알림 생성 시간
            const time = document.createElement('time');
            time.className = 'notification-card-time';

            // 날짜 데이터를 time 태그의 datetime 속성에 넣는다.
            time.dateTime = notification.createdAt || '';

            // 사용자가 이해하기 쉬운 상대 시간으로 표시한다.
            time.textContent = relativeTime(notification.createdAt);

            // 제목 영역에 점, 제목, 시간을 순서대로 추가한다.
            heading.append(dot, title, time);

            // 알림 본문
            const content = document.createElement('span');
            content.className = 'notification-card-content';
            content.textContent = notification.content || '';

            // 알림 카드에 제목과 본문을 추가한다.
            card.append(heading, content);

            // 알림을 클릭하면 읽음 처리 함수를 실행한다.
            // 읽음 처리 완료 후 필요하면 해당 URL로 이동한다.
            card.addEventListener('click', () => {
                markAsRead(notification.id, card, notification.url);
            });

            // 완성된 알림 카드를 목록에 추가한다.
            list.append(card);
        });
    }


    // ==================================================
    // 7. 읽지 않은 알림 개수 서버에서 조회
    // ==================================================

    async function refreshCount() {
        // 서버에 읽지 않은 알림 개수를 요청한다.
        const response = await apiFetch('/api/notifications/unread/count');

        // 응답 실패 시 오류 발생
        if (!response.ok) {
            throw new Error('인증이 필요하거나 알림 개수를 불러오지 못했습니다.');
        }

        // 응답 JSON을 읽어 뱃지 및 상태를 업데이트한다.
        setUnreadCount(await response.json());
    }


    // ==================================================
    // 8. 읽지 않은 알림 목록 서버에서 조회
    // ==================================================

    async function loadNotifications() {
        // API 응답을 기다리는 동안 로딩 메시지를 표시한다.
        showState('알림을 불러오는 중입니다.');

        try {
            // 서버에서 읽지 않은 알림 목록을 조회한다.
            const response = await apiFetch('/api/notifications/unread');

            // 요청 실패 시 오류 처리
            if (!response.ok) {
                throw new Error('알림을 불러오지 못했습니다. 다시 로그인해 주세요.');
            }

            // 서버에서 받은 알림 배열
            const fetchedNotifications = await response.json();

            // 조회한 알림들의 ID를 중복 방지 Set에 등록한다.
            fetchedNotifications.forEach((notification) => {
                receivedNotificationIds.add(notification.id);
            });

            // 알림 ID를 기준으로 중복을 방지하는 Map 생성
            const byId = new Map(
                fetchedNotifications.map((notification) => [
                    notification.id,
                    notification
                ])
            );

            // 서버 조회가 진행되는 동안 실시간으로 받은 알림을 추가한다.
            // 같은 ID라면 최신 데이터로 덮어쓴다.
            liveNotificationsDuringLoad.forEach((notification, id) => {
                byId.set(id, notification);
            });

            // 임시 저장했던 실시간 알림 데이터 초기화
            liveNotificationsDuringLoad.clear();

            // Map에 저장된 알림을 배열로 변환하여 화면 데이터로 사용한다.
            loadedNotifications = [...byId.values()];

            // 알림 목록 화면 갱신
            renderNotifications();

            // 읽지 않은 알림 개수도 다시 조회한다.
            await refreshCount();

        } catch (error) {
            // 조회 실패 시 오류 메시지 표시
            showState(error.message || '알림을 불러오지 못했습니다.');
        }
    }


    // ==================================================
    // 9. 개별 알림 읽음 처리
    // ==================================================

    async function markAsRead(notificationId, card, url) {
        // 중복 클릭이나 연속 요청을 방지하기 위해 버튼 비활성화
        card.disabled = true;

        try {
            // 서버에 특정 알림 읽음 처리 요청
            const response = await apiFetch(
                `/api/notifications/${notificationId}/read`,
                { method: 'PATCH' }
            );

            // 요청 실패 시 오류 발생
            if (!response.ok) {
                throw new Error('읽음 처리에 실패했습니다.');
            }

            // 읽음 처리에 성공했으므로 화면 목록에서 해당 알림 제거
            loadedNotifications = loadedNotifications.filter(
                (item) => item.id !== notificationId
            );

            // 변경된 알림 목록 다시 렌더링
            renderNotifications();

            // 서버 기준 unread 개수를 다시 조회하여 뱃지 동기화
            await refreshCount();

            // 알림에 연결된 URL이 있다면 해당 페이지로 이동한다.
            if (url) location.href = url;

        } catch (error) {
            // 실패 시 버튼을 다시 활성화한다.
            card.disabled = false;

            // 실패 메시지 표시
            showState(error.message || '읽음 처리에 실패했습니다.');
        }
    }


    // ==================================================
    // 10. 모든 알림 읽음 처리
    // ==================================================

    readAllButton.addEventListener('click', async () => {
        // 처리 중 중복 클릭 방지
        readAllButton.disabled = true;

        try {
            // 서버에 모든 알림 읽음 처리 요청
            const response = await apiFetch(
                '/api/notifications/read-all',
                { method: 'PATCH' }
            );

            // 요청 실패 시 오류 발생
            if (!response.ok) {
                throw new Error('모두 읽음 처리에 실패했습니다.');
            }

            // 서버에서 모든 읽음 처리가 완료되었으므로
            // 조회 중 임시 저장했던 실시간 알림 데이터 초기화
            liveNotificationsDuringLoad.clear();

            // 목록을 단순히 비우는 대신 서버에서 다시 조회한다.
            // 서버의 실제 상태를 화면에 반영하기 위한 목적이다.
            await loadNotifications();

        } catch (error) {
            // 실패 시 오류 메시지 표시
            showState(error.message || '모두 읽음 처리에 실패했습니다.');

        } finally {
            // 요청이 성공하거나 실패해도 버튼은 다시 활성화한다.
            readAllButton.disabled = false;
        }
    });


    // ==================================================
    // 11. 알림 패널 열기
    // ==================================================

    function openPanel() {
        // 알림 패널 표시
        panel.hidden = false;

        // 접근성 속성 업데이트: 열려 있는 상태
        trigger.setAttribute('aria-expanded', 'true');
        trigger.setAttribute('aria-label', '알림 닫기');

        // 패널을 열 때마다 서버에서 최신 알림 목록 조회
        loadNotifications();
    }


    // ==================================================
    // 12. 알림 패널 닫기
    // ==================================================

    function closePanel() {
        // 알림 패널 숨김
        panel.hidden = true;

        // 접근성 속성 업데이트: 닫혀 있는 상태
        trigger.setAttribute('aria-expanded', 'false');
        trigger.setAttribute('aria-label', '알림 열기');
    }


    // ==================================================
    // 13. 알림 버튼 클릭 이벤트
    // ==================================================

    trigger.addEventListener('click', () => {
        // 현재 패널이 닫혀 있다면 열고,
        // 열려 있다면 닫는다.
        if (panel.hidden) openPanel();
        else closePanel();
    });


    // ==================================================
    // 14. 닫기 버튼 클릭 이벤트
    // ==================================================

    closeButton.addEventListener('click', closePanel);


    // ==================================================
    // 15. 패널 바깥 클릭 시 닫기
    // ==================================================

    document.addEventListener('click', (event) => {
        // 패널이 열려 있고 클릭한 위치가 알림 영역 바깥이면 닫는다.
        if (!panel.hidden && !event.target.closest('.notification-area')) {
            closePanel();
        }
    });


    // ==================================================
    // 16. ESC 키 입력 시 패널 닫기
    // ==================================================

    document.addEventListener('keydown', (event) => {
        // ESC 키를 눌렀을 때 패널이 열려 있으면 닫는다.
        if (event.key === 'Escape' && !panel.hidden) {
            closePanel();

            // 닫은 후 알림 버튼으로 포커스를 이동한다.
            trigger.focus();
        }
    });


    // ==================================================
    // 17. 초기 unread 개수 조회 및 SSE 연결
    // ==================================================

    // 처음 페이지에 진입하면 서버의 읽지 않은 알림 개수를 조회한다.
    refreshCount()

        // 조회에 성공했을 때 SSE 연결을 시작한다.
        .then(() => {

            // 서버의 실시간 알림 구독
            eventSource = new EventSource('/api/notifications/subscribe');

            // 서버에서 notification 이벤트가 도착하면 실행
            eventSource.addEventListener('notification', (event) => {
                try {
                    // SSE 데이터(JSON 문자열)를 JavaScript 객체로 변환
                    const notification = JSON.parse(event.data);

                    // 해당 알림 ID를 이전에 받은 적이 있는지 확인
                    const isNewNotification =
                        !receivedNotificationIds.has(notification.id);

                    // 새 알림인 경우에만 목록과 unread 개수를 갱신한다.
                    if (isNewNotification) {

                        // 중복 수신 방지를 위해 ID 저장
                        receivedNotificationIds.add(notification.id);

                        // Set에 ID가 너무 많이 쌓이지 않도록
                        // 500개를 초과하면 가장 먼저 저장된 ID 제거
                        if (receivedNotificationIds.size > 500) {
                            receivedNotificationIds.delete(
                                receivedNotificationIds.values().next().value
                            );
                        }

                        // 새로운 알림을 목록 맨 앞에 추가
                        loadedNotifications.unshift(notification);

                        // 현재 알림 패널이 열려 있다면
                        // 서버 목록 조회 중 유실 방지를 위해 별도로 임시 저장
                        if (!panel.hidden) {
                            liveNotificationsDuringLoad.set(
                                notification.id,
                                notification
                            );
                        }

                        // 읽지 않은 알림 개수를 1 증가시킨다.
                        setUnreadCount(unreadCount + 1);
                    }

                    // 알림 패널이 열려 있는 경우 화면 목록 즉시 갱신
                    if (!panel.hidden) {
                        renderNotifications();
                    }

                } catch (error) {
                    // SSE 데이터 파싱 또는 처리 오류 기록
                    console.error('알림 데이터를 읽지 못했습니다.', error);
                }
            });
        })

        // 초기 unread 조회 실패 시 SSE 연결을 시도하지 않는다.
        .catch(() => {
            // 로그인하지 않은 화면에서는 알림 API와 SSE 연결을 시도하지 않습니다.
        });

})();
