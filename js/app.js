// GoodsBridge 뷰단 목업 - 공용 mock DB + UI 헬퍼
// 실제 백엔드 없음. 전부 localStorage 로 시뮬레이션.

const DB_KEY = 'gb_mockdb_v1';
const USER_KEY = 'gb_current_user';
const LOG_KEY = 'gb_api_log';

const SEED = {
  users: [
    { id: 1, kakaoId: 'kakao_1001', nickname: '유저A', role: 'USER' },
    { id: 2, kakaoId: 'kakao_1002', nickname: '유저B', role: 'USER' },
    { id: 3, kakaoId: 'kakao_2001', nickname: '민지샵장(가게주인)', role: 'USER' },
    { id: 4, kakaoId: 'kakao_9001', nickname: '운영자(관리자)', role: 'ADMIN' },
  ],
  shops: [
    { id: 1, ownerUserId: 3, name: '민지굿즈샵 홍대점', region: '서울 홍대', address: '서울 마포구 와우산로 1', status: 'APPROVED' },
  ],
  subscriptions: [
    { id: 1, shopId: 1, setupFeePaid: true, startDate: '2026-01-01', endDate: '2027-01-01', status: 'ACTIVE' },
  ],
  categories: [
    { id: 1, name: '포토카드' },
    { id: 2, name: '아크릴스탠드' },
    { id: 3, name: '인형' },
  ],
  items: [
    { id: 1, sellerUserId: 1, shopId: null, categoryId: 1, title: '세븐틴 민규 포카', description: '미개봉 새 상품', price: 15000, saleType: 'NORMAL', status: 'ON_SALE' },
    { id: 2, sellerUserId: 2, shopId: null, categoryId: 1, title: '뉴진스 다니엘 포카', description: '급처, 모서리 약간 눌림', price: 20000, saleType: 'NORMAL', status: 'ON_SALE' },
    { id: 3, sellerUserId: 3, shopId: 1, categoryId: 2, title: '한정판 아크릴 스탠드', description: '홍대점 오픈런 이벤트 상품', price: 10000, saleType: 'EVENT', status: 'ON_SALE' },
    { id: 4, sellerUserId: 3, shopId: 1, categoryId: 3, title: '시그니처 인형', description: '경매 출품작', price: 50000, saleType: 'AUCTION', status: 'ON_SALE' },
  ],
  exchangeItems: [
    { id: 1, userId: 1, itemId: 1, wantDescription: '카리나 포카로 교환 원해요', shopId: 1, status: 'OPEN' },
  ],
  exchangeRequests: [],
  exchangeComments: [], // 목업 전용 — 기획서/18개 테이블 ERD엔 없는 가정 추가. gaps.html 참고
  shopEvents: [
    { id: 1, shopId: 1, itemId: 3, capacity: 3, currentCount: 2, feeAmount: 5000, deadline: '2026-11-10T18:00', status: 'OPEN' },
  ],
  eventParticipations: [
    { id: 1, shopEventId: 1, userId: 1, ticketNumber: 1, paymentId: 'pay_ev_1', status: 'JOINED' },
    { id: 2, shopEventId: 1, userId: 2, ticketNumber: 2, paymentId: 'pay_ev_2', status: 'JOINED' },
  ],
  auctions: [
    { id: 1, shopId: 1, itemId: 4, startPrice: 50000, currentPrice: 50000, depositAmount: 5000, currentWinnerUserId: null, deadline: '2026-11-10T20:00', status: 'ACTIVE' },
  ],
  bids: [],
  boards: [
    { id: 1, authorId: 4, title: '홍대점 입점을 환영합니다', content: '많이 이용해주세요 :)', createdAt: '2026-10-20' },
  ],
  replies: [],
  purchases: [],
  payments: [],
  refunds: [],
  settlements: [],
  chatLogs: [],
  notifications: [], // 목업 전용 — 신규 제안 기능(#34 Notification). 기획서/18개 테이블 ERD엔 없음, gaps.html 참고
};

// 이전 버전 localStorage에 저장된 데이터에 새 필드/테이블이 없을 수 있어 보정
function migrateDB(db) {
  if (!db.notifications) db.notifications = [];
  if (!db.purchases) db.purchases = [];
  if (!db.exchangeComments) db.exchangeComments = [];
  (db.exchangeItems || []).forEach(e => {
    if (e.shopId === undefined) e.shopId = (db.shops[0] && db.shops[0].id) || null;
  });
  (db.auctions || []).forEach(a => {
    if (a.depositAmount === undefined) a.depositAmount = Math.round((a.startPrice || 0) * 0.1) || 5000;
  });
  return db;
}
function loadDB() {
  const raw = localStorage.getItem(DB_KEY);
  if (!raw) {
    const fresh = JSON.parse(JSON.stringify(SEED));
    localStorage.setItem(DB_KEY, JSON.stringify(fresh));
    return fresh;
  }
  try { return migrateDB(JSON.parse(raw)); } catch (e) { return JSON.parse(JSON.stringify(SEED)); }
}
function saveDB(db) { localStorage.setItem(DB_KEY, JSON.stringify(db)); }
function resetDB() { localStorage.removeItem(DB_KEY); localStorage.removeItem(LOG_KEY); location.reload(); }
function nextId(db, table) {
  const rows = db[table];
  return rows.length ? Math.max(...rows.map(r => r.id)) + 1 : 1;
}

function currentUserId() {
  const v = localStorage.getItem(USER_KEY);
  return v ? Number(v) : 1;
}
function setCurrentUser(id) { localStorage.setItem(USER_KEY, String(id)); location.reload(); }
function currentUser() {
  const db = loadDB();
  return db.users.find(u => u.id === currentUserId()) || db.users[0];
}
function userName(db, id) {
  const u = db.users.find(x => x.id === id);
  return u ? u.nickname : `유저${id}`;
}
function isAdmin() { return currentUser().role === 'ADMIN'; }
function isShopOwner(db, shopId) {
  const shop = db.shops.find(s => s.id === shopId);
  return shop && shop.ownerUserId === currentUserId();
}

// ---- 알림 (#34 Notification, 목업 전용 신규 제안 기능) ----
function addNotification(db, userId, content) {
  db.notifications.push({ id: nextId(db, 'notifications'), userId, content, read: false, createdAt: new Date().toISOString() });
}
function unreadNotificationCount(db, userId) {
  return db.notifications.filter(n => n.userId === userId && !n.read).length;
}

function won(n) { return (n || 0).toLocaleString('ko-KR') + '원'; }

// ---- 마감 카운트다운 (data-deadline 속성 가진 요소를 자동으로 찾아 갱신) ----
function formatCountdown(deadlineStr) {
  if (!deadlineStr || deadlineStr === '미지정') return { text: '마감시간 미지정', cls: '' };
  const target = new Date(deadlineStr);
  if (isNaN(target.getTime())) return { text: deadlineStr, cls: '' };
  const diff = target - new Date();
  if (diff <= 0) return { text: '⏰ 마감됨', cls: 'cancelled' };
  const totalSec = Math.floor(diff / 1000);
  const d = Math.floor(totalSec / 86400);
  const h = Math.floor((totalSec % 86400) / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  let text;
  if (d > 0) text = `${d}일 ${h}시간 남음`;
  else if (h > 0) text = `${h}시간 ${m}분 남음`;
  else if (m > 0) text = `${m}분 ${s}초 남음`;
  else text = `${s}초 남음`;
  const cls = diff < 60 * 60 * 1000 ? 'pending' : 'open';
  return { text, cls };
}
function tickCountdowns() {
  document.querySelectorAll('[data-deadline]').forEach(el => {
    const { text, cls } = formatCountdown(el.getAttribute('data-deadline'));
    el.textContent = text;
    el.className = 'badge ' + cls;
  });
}
function startCountdownTicker() {
  if (window.__gbCountdownStarted) return;
  window.__gbCountdownStarted = true;
  tickCountdowns();
  setInterval(tickCountdowns, 1000);
}

// ---- API 호출 로그 (실제 호출 없음, 어떤 엔드포인트가 쓰이는지 시각화) ----
function apiLog(method, path, note) {
  const log = JSON.parse(localStorage.getItem(LOG_KEY) || '[]');
  log.unshift({ t: new Date().toLocaleTimeString('ko-KR'), method, path, note });
  localStorage.setItem(LOG_KEY, JSON.stringify(log.slice(0, 80)));
  renderApiLog();
  toast(`${method} ${path}`);
}
function renderApiLog() {
  const panel = document.getElementById('apiLogPanel');
  if (!panel) return;
  const log = JSON.parse(localStorage.getItem(LOG_KEY) || '[]');
  panel.innerHTML = log.length
    ? log.map(l => `<div><b>${l.method}</b> ${l.path}<br><span class="note">${l.t} · ${l.note || ''}</span></div>`).join('')
    : '<div class="note">아직 호출된 API가 없습니다. 화면에서 버튼을 눌러보세요.</div>';
}
function toggleApiLog() {
  document.getElementById('apiLogPanel').classList.toggle('show');
}
function toast(msg) {
  let t = document.getElementById('gbToast');
  if (!t) {
    t = document.createElement('div');
    t.id = 'gbToast';
    t.className = 'toast';
    document.body.appendChild(t);
  }
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(window.__gbToastTimer);
  window.__gbToastTimer = setTimeout(() => t.classList.remove('show'), 1600);
}

// ---- 공통 레이아웃 ----
const NAV_ITEMS = [
  ['index.html', '홈'],
  ['login.html', '로그인'],
  ['notifications.html', '🔔 알림'],
  ['shop.html', '가게입점/구독'],
  ['items.html', '상품/구매'],
  ['exchange.html', '교환'],
  ['event.html', '이벤트'],
  ['auction.html', '경매'],
  ['board.html', '게시판'],
  ['payment.html', '결제/환불'],
  ['admin.html', '관리자'],
  ['chat.html', 'AI챗봇'],
  ['gaps.html', '빠진기능 체크'],
];

function renderNav(active) {
  const db = loadDB();
  const me = currentUser();
  const unread = unreadNotificationCount(db, me.id);
  const navHtml = NAV_ITEMS.map(([href, label]) =>
    `<a href="${href}" class="${href === active ? 'active' : ''}">${label}${href === 'notifications.html' && unread ? ` (${unread})` : ''}</a>`
  ).join('');
  const userOptions = db.users.map(u =>
    `<option value="${u.id}" ${u.id === me.id ? 'selected' : ''}>${u.nickname}${u.status === 'WITHDRAWN' ? ' (탈퇴)' : ''} (${u.role})</option>`
  ).join('');
  document.getElementById('nav').innerHTML = `
    <header class="topnav">
      <span class="brand">🌉 GoodsBridge 뷰단목업</span>
      <nav>${navHtml}</nav>
      <div class="role-switch">
        현재 로그인:
        <select class="field" style="width:auto" onchange="setCurrentUser(this.value)">${userOptions}</select>
        <button class="btn secondary" style="padding:3px 8px" onclick="if(confirm('목업 데이터를 전부 초기화할까요?'))resetDB()">DB 초기화</button>
      </div>
    </header>`;
}

function renderIssueBadges(nums, text) {
  const tags = nums.map(n => `<span class="tag">#${n}</span>`).join('');
  return `<div class="issue-badges">관련 이슈 ${tags} ${text ? '· ' + text : ''}</div>`;
}

function mountCommon(active) {
  renderNav(active);
  renderApiLog();
  const body = document.body;
  const btn = document.createElement('button');
  btn.className = 'apilog-toggle';
  btn.textContent = '🛰 API 호출 로그';
  btn.onclick = toggleApiLog;
  body.appendChild(btn);
  const panel = document.createElement('div');
  panel.id = 'apiLogPanel';
  body.appendChild(panel);
  renderApiLog();
  startCountdownTicker();
}

document.addEventListener('DOMContentLoaded', () => {
  if (!localStorage.getItem(USER_KEY)) localStorage.setItem(USER_KEY, '1');
});
