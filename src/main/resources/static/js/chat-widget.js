// AI 도우미 채팅 위젯. 모든 화면의 오른쪽 아래에 고정되고, 누르면 채팅 창이 열린다.
// 마크업은 fragments/footer.mustache에 있고(로그인한 사용자에게만), 이 파일은 동작만 맡는다.
// 서버 API: POST /api/chat (질문 → 답변), GET /api/chat/history (내 대화 기록)
(() => {
    const root = document.getElementById('chat-widget');
    if (!root) return;

    const toggle = document.getElementById('chat-toggle');
    const panel = document.getElementById('chat-panel');
    const listEl = document.getElementById('chat-list');
    const form = document.getElementById('chat-form');
    const input = document.getElementById('chat-input');
    const sendBtn = document.getElementById('chat-send');
    let historyLoaded = false;
    let sending = false;

    // AI 답변의 **굵게** 표시만 살려서 보여준다. 내용은 모두 textContent로 넣어서 HTML이 섞이지 않는다.
    function fillText(el, text) {
        text.split(/(\*\*[^*]+\*\*)/g).forEach(part => {
            if (/^\*\*[^*]+\*\*$/.test(part)) {
                const strong = document.createElement('strong');
                strong.textContent = part.slice(2, -2);
                el.appendChild(strong);
            } else if (part) {
                el.appendChild(document.createTextNode(part));
            }
        });
    }

    // role: USER / ASSISTANT / TOOL / ERROR
    function addMessage(role, text) {
        const el = document.createElement('div');
        el.className = 'chat-msg is-' + role.toLowerCase();
        if (role === 'TOOL') el.textContent = '🔧 ' + text; else fillText(el, text);
        listEl.appendChild(el);
        listEl.scrollTop = listEl.scrollHeight;
        return el;
    }

    // 처음 열 때 한 번만 지난 대화(본인 것)를 불러온다
    async function loadHistory() {
        historyLoaded = true;
        try {
            const res = await fetch('/api/chat/history', { credentials: 'same-origin' });
            if (res.ok) (await res.json()).forEach(log => addMessage(log.role, log.content));
        } catch (e) { /* 기록을 못 불러와도 대화는 할 수 있다 */ }
        if (!listEl.children.length) {
            addMessage('ASSISTANT', '안녕하세요! 교환 신청 현황을 확인하거나 취소하는 걸 도와드려요. 예) "내 교환 신청 보여줘"');
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
            const res = await fetch('/api/chat', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: content })
            });
            const body = await res.json().catch(() => ({}));
            waiting.remove();
            if (res.ok) addMessage('ASSISTANT', body.reply);
            else addMessage('ERROR', body.message || '요청을 처리하지 못했어요.');
        } catch (e) {
            waiting.remove();
            addMessage('ERROR', '서버와 통신하지 못했어요.');
        } finally {
            sending = false;
            sendBtn.disabled = false;
            input.focus();
        }
    });

    // 다른 화면(/test/chat 등)에서 위젯을 열 수 있게 노출한다
    window.GBChat = { open: open, close: close };

    // /test/chat 화면은 위젯을 바로 열어 둔다
    if (location.pathname === '/test/chat') open();
})();
