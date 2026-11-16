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

    // 브라우저가 실제로 해석한 주소가 이 사이트인지로 판단한다. "//evil.com"뿐 아니라 "/\evil.com"처럼
    // 브라우저가 슬래시로 바꿔 읽는 값도 바깥 주소가 되므로, 글자 모양만 보고 거르지 않는다.
    function isSameSitePath(path) {
        try {
            return new URL(path, location.origin).origin === location.origin;
        } catch (e) {
            return false;
        }
    }

    // AI 답변의 **굵게**와 [글자](/사이트경로) 링크만 살려서 보여준다. 링크는 이 사이트 안의 경로(/로 시작)만 허용한다.
    function fillText(node, text) {
        text.split(/(\*\*[^*]+\*\*|\[[^\]]+\]\(\/[^)\s]*\))/g).forEach(part => {
            const link = /^\[([^\]]+)\]\((\/[^)\s]*)\)$/.exec(part);
            if (/^\*\*[^*]+\*\*$/.test(part)) {
                node.appendChild(el('strong', null, part.slice(2, -2)));
            } else if (link && isSameSitePath(link[2])) {
                const a = el('a', null, link[1]);
                a.href = link[2];
                node.appendChild(a);
            } else if (part) {
                node.appendChild(document.createTextNode(part));
            }
        });
    }

    function scrollDown() {
        listEl.scrollTop = listEl.scrollHeight;
    }

    // 긴 목록 카드는 맨 위부터 보이게 한다 (맨 아래로 내리면 첫 항목이 가려진다). 카드가 짧으면 자연스럽게 맨 아래에 맞춰진다.
    function scrollToCard(card) {
        listEl.scrollTop += card.getBoundingClientRect().top - listEl.getBoundingClientRect().top - 8;
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

    // 교환 신청 전체를 최근 순으로 가져온다. 분류와 개수 제한은 보여 줄 때 정한다.
    async function loadMyRequests() {
        const res = await api('/api/exchange-requests/me');
        if (!res.ok) throw new Error(res.body.message || '교환 신청을 불러오지 못했어요.');
        return res.body.slice().sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
    }

    // 처리 상태를 사용자가 알아보기 쉬운 4가지로 묶는다 (기본은 처리할 일이 있는 '대기 중')
    const CATEGORIES = [
        { key: 'PENDING', label: '대기 중', statuses: ['PENDING'] },
        { key: 'CONFIRMED', label: '확정', statuses: ['CONFIRMED'] },
        { key: 'DONE', label: '완료', statuses: ['COMPLETED'] },
        { key: 'CLOSED', label: '취소·거절·만료', statuses: ['CANCELLED', 'REJECTED', 'PROPOSAL_EXPIRED', 'EXPIRED_PARTIAL', 'EXPIRED_BOTH'] }
    ];
    const MAX_ROWS = 10;
    const ALL_HISTORY = '/my/exchange';   // 전체 교환 내역 화면

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

    // [내 교환 신청 보기] 읽기만 한다. 기본은 '대기 중'만 보여 주고, 분류 버튼으로 다른 상태를 볼 수 있다.
    async function showMyRequests(categoryKey = 'PENDING', userBubble = '내 교환 신청 보기') {
        addMessage('USER', userBubble);
        try {
            const all = await loadMyRequests();
            if (!all.length) { addMessage('ASSISTANT', '교환 신청이 아직 없어요.'); return; }
            const selected = CATEGORIES.find(c => c.key === categoryKey) || CATEGORIES[0];
            const inCategory = all.filter(r => selected.statuses.includes(r.status));

            const card = addCard();
            card.appendChild(el('div', 'chat-card-title', '교환 신청 · ' + selected.label + ' ' + inCategory.length + '건'));

            // 분류별 건수 버튼: 누르면 그 상태의 신청을 보여 준다
            const chips = el('div', 'chat-card-chips');
            CATEGORIES.forEach(c => {
                const count = all.filter(r => c.statuses.includes(r.status)).length;
                const chip = addButton(chips, c.label + ' ' + count, 'chat-chip' + (c.key === selected.key ? ' is-active' : ''),
                    () => showMyRequests(c.key, c.label + ' 보기'));
                if (c.key === selected.key) chip.disabled = true;   // 지금 보고 있는 분류
            });
            card.appendChild(chips);

            if (!inCategory.length) {
                card.appendChild(el('p', 'chat-card-text', selected.label + ' 신청이 없어요.'));
            }
            inCategory.slice(0, MAX_ROWS).forEach(r => requestRow(card, r, selected.key === 'PENDING'));
            if (inCategory.length > MAX_ROWS) {
                card.appendChild(el('p', 'chat-card-more', '최근 ' + MAX_ROWS + '건만 보여요. 나머지는 전체 내역에서 확인해 주세요.'));
            }

            const actions = el('div', 'chat-card-actions');
            const link = el('a', 'chat-link', '전체 교환 내역 보기');
            link.href = ALL_HISTORY;
            actions.appendChild(link);
            card.appendChild(actions);
            scrollToCard(card);
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
            scrollToCard(card);
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
        scrollToCard(card);
    }

    // 서비스 요약 버튼: AI 없이 정해진 안내를 바로 보여 준다 (내용은 resources/chat/service-guide.md와 맞춘다)
    const GUIDES = {
        'guide-exchange': ['교환 방법', [
            '교환은 이렇게 진행돼요.',
            '1. **교환 글 쓰기**: 내 굿즈와 교환 장소(가게)를 정해 글을 올려요.',
            '2. **제안**: 다른 사용자가 자기 상품으로 교환을 제안해요.',
            '3. **확정**: 글쓴이가 수락하면 확정되고, 양쪽이 보증금을 결제해요.',
            '4. **방문·정산**: 기한 안에 가게를 방문하면 사장님이 출석을 확인해요.',
            '[교환 글 쓰기](/exchange/new) [교환 게시글 보기](/exchange)'].join('\n')],
        'guide-deposit': ['보증금·정산', [
            '보증금은 **내 상품 가격의 20% (최소 2,000원)** 예요.',
            '- 둘 다 방문: 각자 보증금의 75%를 돌려받아요.',
            '- 둘 다 노쇼: 각자 보증금의 50%를 돌려받아요.',
            '- 한쪽만 노쇼: 방문한 사람은 전액 환불, 노쇼한 사람은 돌려받지 못해요.',
            '[내 교환 내역](/my/exchange)'].join('\n')],
        'guide-more': ['경매·이벤트', [
            '**이벤트**: 가게가 여는 선착순 이벤트예요. 로그인한 일반 사용자는 누구나 참가할 수 있고, 참가비를 결제하면 신청이 끝나요. 정원이 차면 신청할 수 없고, 마감 전에는 참가를 취소할 수 있어요.',
            '**경매**: 가게가 올린 상품에 일반 사용자가 입찰해서 참여해요. 첫 입찰 때 보증금을 한 번 내고, 마감 후 가장 높게 부른 사람이 낙찰돼요. 낙찰되면 정해진 기한 안에 잔금을 결제해야 하고, 낙찰되지 못하면 보증금이 환불돼요.',
            '[이벤트 보러 가기](/test/event) [경매 보러 가기](/test/auction)'].join('\n')],
        'guide-browse': ['상품·가게 보기', [
            '둘러볼 곳을 골라 주세요.',
            '[상품](/test/item) [가게](/test/shop) [교환](/exchange) [경매](/test/auction) [이벤트](/test/event)'].join('\n')]
    };
    function showGuide(key) {
        addMessage('USER', GUIDES[key][0]);
        addMessage('ASSISTANT', GUIDES[key][1]);
    }

    const quickActions = { 'my-requests': () => showMyRequests(), 'cancel-request': () => showCancelable() };
    document.getElementById('chat-quick').addEventListener('click', ev => {
        const button = ev.target.closest('[data-quick]');
        if (!button) return;
        const key = button.dataset.quick;
        if (GUIDES[key]) showGuide(key); else quickActions[key]();
    });

    // AI를 쓸 수 없을 때 안내하고, 같은 기능을 버튼으로 바로 쓰게 한다
    function showUnavailableNotice(serverMessage) {
        const quota = (serverMessage || '').includes('한도');   // 서버가 한도 소진과 그 밖의 실패를 구분해서 안내한다
        const card = addCard();
        card.appendChild(el('div', 'chat-card-title', quota ? 'AI 사용 한도가 소진되었어요' : '지금은 AI 답변을 쓸 수 없어요'));
        card.appendChild(el('p', 'chat-card-text', serverMessage
            || 'AI 응답을 가져오지 못했어요. 아래 버튼으로 같은 기능을 쓸 수 있어요.'));
        // 같은 기능의 버튼은 아래 빠른 메뉴에 항상 있으므로 여기서는 안내 문구만 보여 준다
        scrollToCard(card);
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
            addMessage('ASSISTANT', '안녕하세요! 글로 물어보시거나 아래 빠른 메뉴 버튼을 눌러 교환 신청 확인·취소와 서비스 안내를 볼 수 있어요. 대화 기록은 하루가 지나면 지워져요.');
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
                showUnavailableNotice(res.body.message);   // AI 한도·연결 문제: 이유를 알리고 버튼으로 같은 기능을 쓰게 한다
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
