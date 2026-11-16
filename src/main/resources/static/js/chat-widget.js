// AI 도우미 채팅 위젯. 모든 화면의 오른쪽 아래에 고정되고, 누르면 채팅 창이 열린다.
// 마크업은 fragments/footer.mustache에 있고(로그인한 사용자에게만), 이 파일은 동작만 맡는다.
//
// 두 가지 방식으로 쓸 수 있다.
//  1) 글로 묻기(AI): POST /api/chat (Gemini가 도구를 불러 답한다), GET /api/chat/history (내 대화 기록)
//  2) 버튼으로 쓰기(AI 없이): 빠른 메뉴 버튼 → 기존 교환 API를 직접 호출한다.
//     무료 AI 키는 하루 사용량이 정해져 있어서, 한도가 다 되어도(서버가 503) 같은 기능을 버튼으로 쓸 수 있게 했다.
//     버튼 방식의 취소도 AI 방식과 똑같이 "설명 → 확인 → 실행" 순서다.
(() => {
    const root = document.getElementById('chat-widget');
    if (!root) return;

    const toggle = document.getElementById('chat-toggle');
    const listEl = document.getElementById('chat-list');
    const form = document.getElementById('chat-form');
    const input = document.getElementById('chat-input');
    const sendBtn = document.getElementById('chat-send');
    const myUserId = Number(root.dataset.userId);
    let historyLoaded = false;
    let sending = false;

    const won = n => Number(n || 0).toLocaleString('ko-KR') + '원';
    const STATUS = {
        PENDING: '대기 중', REJECTED: '거절됨', CANCELLED: '취소됨', PROPOSAL_EXPIRED: '제안 만료',
        CONFIRMED: '확정됨', COMPLETED: '완료됨', EXPIRED_PARTIAL: '부분 만료', EXPIRED_BOTH: '양쪽 만료'
    };

    // 요소 하나를 만든다. 내용은 모두 textContent로 넣어서 HTML이 섞이지 않는다.
    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }

    // AI 답변의 **굵게** 표시만 살려서 보여준다.
    function fillText(node, text) {
        text.split(/(\*\*[^*]+\*\*)/g).forEach(part => {
            if (/^\*\*[^*]+\*\*$/.test(part)) {
                node.appendChild(el('strong', null, part.slice(2, -2)));
            } else if (part) {
                node.appendChild(document.createTextNode(part));
            }
        });
    }

    function scrollDown() {
        listEl.scrollTop = listEl.scrollHeight;
    }

    // role: USER / ASSISTANT / TOOL / ERROR
    function addMessage(role, text) {
        const node = el('div', 'chat-msg is-' + role.toLowerCase());
        if (role === 'TOOL') node.textContent = '🔧 ' + text; else fillText(node, text);
        listEl.appendChild(node);
        scrollDown();
        return node;
    }

    // 말풍선 모양의 카드 (목록·확인 창처럼 버튼이 들어가는 안내)
    function addCard() {
        const card = el('div', 'chat-msg is-assistant is-card');
        listEl.appendChild(card);
        return card;
    }

    function addButton(parent, label, className, onClick) {
        const button = el('button', className, label);
        button.type = 'button';
        button.addEventListener('click', onClick);
        parent.appendChild(button);
        return button;
    }

    async function api(path, options) {
        const res = await fetch(path, Object.assign({ credentials: 'same-origin' }, options));
        const body = await res.json().catch(() => ({}));
        return { ok: res.ok, status: res.status, body: body };
    }

    // ===================== 버튼 방식 (AI 없이) =====================

    async function loadMyRequests() {
        const res = await api('/api/exchange-requests/me');
        if (!res.ok) throw new Error(res.body.message || '교환 신청을 불러오지 못했어요.');
        return res.body.slice().sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || ''))).slice(0, 10);
    }

    const isMyPending = r => r.proposerUserId === myUserId && r.status === 'PENDING';

    // 신청 한 줄: 게시글 제목, 상태, 내 역할. cancellable이면 취소 버튼이 붙는다.
    function requestRow(parent, r, cancellable) {
        const row = el('div', 'chat-row');
        const info = el('div', 'chat-row-info');
        info.appendChild(el('strong', null, '#' + r.id + ' ' + (r.exchangeItemTitle || r.itemTitle || '교환 신청')));
        info.appendChild(el('small', null, (STATUS[r.status] || r.status) + ' · '
            + (r.proposerUserId === myUserId ? '내가 신청' : '내 게시글에 신청') + (r.shopName ? ' · ' + r.shopName : '')));
        row.appendChild(info);
        if (cancellable && isMyPending(r)) {
            addButton(row, '취소', 'chat-mini', () => askCancel(r));
        }
        parent.appendChild(row);
    }

    // [내 교환 신청 보기] 읽기만 한다
    async function showMyRequests() {
        addMessage('USER', '내 교환 신청 보기');
        try {
            const list = await loadMyRequests();
            if (!list.length) { addMessage('ASSISTANT', '교환 신청이 아직 없어요.'); return; }
            const card = addCard();
            card.appendChild(el('div', 'chat-card-title', '최근 교환 신청 ' + list.length + '건'));
            list.forEach(r => requestRow(card, r, true));
            scrollDown();
        } catch (e) {
            addMessage('ERROR', e.message);
        }
    }

    // [교환 신청 취소] 내가 신청한 대기 중인 건만 보여주고 고르게 한다
    async function showCancelable() {
        addMessage('USER', '교환 신청 취소하기');
        try {
            const list = (await loadMyRequests()).filter(isMyPending);
            if (!list.length) { addMessage('ASSISTANT', '취소할 수 있는 신청이 없어요. 내가 신청한 대기 중인 건만 취소할 수 있어요.'); return; }
            const card = addCard();
            card.appendChild(el('div', 'chat-card-title', '취소할 신청을 골라 주세요'));
            list.forEach(r => requestRow(card, r, true));
            scrollDown();
        } catch (e) {
            addMessage('ERROR', e.message);
        }
    }

    // 1단계: 취소하면 어떻게 되는지 설명하고 확인을 받는다 (취소는 되돌릴 수 없다)
    function askCancel(r) {
        const refund = r.depositAmountB || 0;
        const card = addCard();
        card.appendChild(el('div', 'chat-card-title', '#' + r.id + ' 신청을 취소할까요?'));
        const ul = el('ul', 'chat-card-list');
        ['신청 상태가 \'취소됨\'으로 바뀌어요.',
            '낸 보증금 ' + won(refund) + '이 환불돼요.',
            '게시글 주인에게 취소 알림이 가요.',
            '한 번 취소하면 되돌릴 수 없어요. 다시 하려면 새로 신청하고 보증금을 다시 내야 해요.'
        ].forEach(t => ul.appendChild(el('li', null, t)));
        card.appendChild(ul);
        const actions = el('div', 'chat-card-actions');
        addButton(actions, '취소하기', 'chat-mini is-danger', async function () {
            actions.querySelectorAll('button').forEach(b => { b.disabled = true; });   // 두 번 누르지 못하게
            // 2단계: 사용자가 확인했을 때만 실제로 취소한다 (권한·상태 검사는 서버가 한다)
            const res = await api('/api/exchange-requests/' + r.id + '/cancel', { method: 'POST' });
            actions.remove();
            if (res.ok) addMessage('ASSISTANT', '#' + r.id + ' 신청을 취소했어요. 보증금 ' + won(refund) + '은 환불돼요.');
            else addMessage('ERROR', res.body.message || '취소하지 못했어요.');
        });
        addButton(actions, '그만두기', 'chat-mini', () => {
            actions.remove();
            addMessage('ASSISTANT', '취소하지 않고 그대로 두었어요.');
        });
        card.appendChild(actions);
        scrollDown();
    }

    const quickActions = { 'my-requests': showMyRequests, 'cancel-request': showCancelable };
    document.getElementById('chat-quick').addEventListener('click', ev => {
        const button = ev.target.closest('[data-quick]');
        if (button) quickActions[button.dataset.quick]();
    });

    // AI를 쓸 수 없을 때 안내하고, 같은 기능을 버튼으로 바로 쓰게 한다
    function offerButtons(serverMessage) {
        const quota = (serverMessage || '').includes('한도');   // 서버가 한도 소진과 그 밖의 실패를 구분해서 안내한다
        const card = addCard();
        card.appendChild(el('div', 'chat-card-title', quota ? 'AI 사용 한도가 소진되었어요' : '지금은 AI 답변을 쓸 수 없어요'));
        card.appendChild(el('p', 'chat-card-text', serverMessage
            || 'AI 응답을 가져오지 못했어요. 아래 버튼으로 같은 기능을 쓸 수 있어요.'));
        const actions = el('div', 'chat-card-actions');
        addButton(actions, '내 교환 신청 보기', 'chat-mini', showMyRequests);
        addButton(actions, '교환 신청 취소하기', 'chat-mini', showCancelable);
        card.appendChild(actions);
        scrollDown();
    }

    // ===================== 글로 묻기 (AI) =====================

    // 가게 계정인지 확인한다 (헤더가 쓰는 것과 같은 API: 내 가게가 있으면 가게 사장님)
    async function isShopAccount() {
        try {
            const res = await api('/api/shops/me');
            return res.ok && Array.isArray(res.body.content) && res.body.content.length > 0;
        } catch (e) {
            return false;
        }
    }

    // 지금의 AI 기능(교환 조회·취소)은 손님용이라 가게 계정에는 쓸 수 있는 기능이 없다. 가게용은 업데이트 후 제공한다.
    function showShopNotice() {
        root.classList.add('is-shop');   // 입력창과 빠른 메뉴를 숨긴다
        const card = addCard();
        card.appendChild(el('div', 'chat-card-title', '가게용 AI 도우미는 준비 중이에요'));
        card.appendChild(el('p', 'chat-card-text', '가게 계정에서 쓸 수 있는 AI 도우미는 업데이트 후 사용할 수 있어요. 조금만 기다려 주세요.'));
        card.appendChild(el('p', 'chat-card-text', '그동안 환불 승인·발송·경매·이벤트·교환은 가게 대시보드와 각 관리 화면에서 처리해 주세요.'));
        const actions = el('div', 'chat-card-actions');
        const link = el('a', 'chat-link', '가게 대시보드로 가기');
        link.href = '/shop/dashboard';
        actions.appendChild(link);
        card.appendChild(actions);
    }

    // 처음 열 때 한 번만 지난 대화(본인 것)를 불러온다
    async function loadHistory() {
        historyLoaded = true;
        if (await isShopAccount()) {
            showShopNotice();
            return;
        }
        try {
            const res = await api('/api/chat/history');
            if (res.ok) res.body.forEach(log => addMessage(log.role, log.content));
        } catch (e) { /* 기록을 못 불러와도 대화는 할 수 있다 */ }
        if (!listEl.children.length) {
            addMessage('ASSISTANT', '안녕하세요! 글로 물어보시거나 아래 빠른 메뉴 버튼을 눌러 교환 신청을 확인·취소할 수 있어요.');
        }
    }

    function open() {
        root.classList.add('is-open');
        toggle.setAttribute('aria-expanded', 'true');
        toggle.setAttribute('aria-label', 'AI 도우미 닫기');
        if (!historyLoaded) loadHistory();
        setTimeout(() => input.focus(), 50);
    }

    function close() {
        root.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
        toggle.setAttribute('aria-label', 'AI 도우미 열기');
    }

    toggle.addEventListener('click', () => root.classList.contains('is-open') ? close() : open());
    document.getElementById('chat-close').addEventListener('click', close);
    document.addEventListener('keydown', ev => { if (ev.key === 'Escape' && root.classList.contains('is-open')) close(); });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const content = input.value.trim();
        if (!content || sending) return;
        sending = true;
        sendBtn.disabled = true;
        input.value = '';
        addMessage('USER', content);
        const waiting = addMessage('ASSISTANT', '생각하는 중…');
        try {
            const res = await api('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: content })
            });
            waiting.remove();
            if (res.ok) {
                addMessage('ASSISTANT', res.body.reply);
            } else if (res.status === 503) {
                offerButtons(res.body.message);   // AI 한도·연결 문제: 이유를 알리고 버튼으로 같은 기능을 쓰게 한다
            } else {
                addMessage('ERROR', res.body.message || '요청을 처리하지 못했어요.');
            }
        } catch (e) {
            waiting.remove();
            addMessage('ERROR', '서버와 통신하지 못했어요.');
        } finally {
            sending = false;
            sendBtn.disabled = false;
            input.focus();
        }
    });

    // 새 대화: 내 대화 기록을 모두 지운다 (환불·취소 같은 교환 내역은 그대로 남는다)
    document.getElementById('chat-reset').addEventListener('click', async () => {
        const message = '대화 기록을 모두 지울까요?\n환불·취소 같은 교환 내역은 그대로 남아요.';
        const agreed = typeof gbConfirm === 'function' ? await gbConfirm(message) : window.confirm(message);
        if (!agreed) return;
        const res = await fetch('/api/chat/history', { method: 'DELETE', credentials: 'same-origin' });
        if (!res.ok) { addMessage('ERROR', '대화를 지우지 못했어요.'); return; }
        listEl.replaceChildren();
        addMessage('ASSISTANT', '대화를 모두 지웠어요. 새로 시작해 볼까요?');
    });

    // 다른 화면(/test/chat 등)에서 위젯을 열 수 있게 노출한다
    window.GBChat = { open: open, close: close };

    // /test/chat 화면은 위젯을 바로 열어 둔다
    if (location.pathname === '/test/chat') open();
})();
