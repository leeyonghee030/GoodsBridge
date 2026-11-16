(() => {
  const trigger = document.getElementById('notification-trigger');
  const panel = document.getElementById('notification-panel');
  const closeButton = document.getElementById('notification-close');
  const badge = document.getElementById('notification-badge');
  const unreadLabel = document.getElementById('notification-unread-label');
  const list = document.getElementById('notification-list');

  if (!trigger || !panel || !closeButton || !badge || !unreadLabel || !list) return;

  let unreadCount = 0;
  let loadedNotifications = [];
  let eventSource;

  function setUnreadCount(count) {
    unreadCount = Math.max(0, Number(count) || 0);
    badge.hidden = unreadCount === 0;
    badge.textContent = unreadCount > 99 ? '99+' : String(unreadCount);
    unreadLabel.textContent = unreadCount ? `읽지 않은 알림 ${unreadCount}개` : '모두 확인했어요';
  }

  function relativeTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const minutes = Math.max(0, Math.floor((Date.now() - date.getTime()) / 60000));
    if (minutes < 1) return '방금 전';
    if (minutes < 60) return `${minutes}분 전`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}시간 전`;
    const days = Math.floor(hours / 24);
    if (days < 7) return `${days}일 전`;
    return new Intl.DateTimeFormat('ko-KR', { month: 'numeric', day: 'numeric' }).format(date);
  }

  function showState(message) {
    list.replaceChildren();
    const state = document.createElement('p');
    state.className = 'notification-state';
    state.textContent = message;
    list.append(state);
  }

  function renderNotifications() {
    list.replaceChildren();
    if (loadedNotifications.length === 0) {
      showState('새로운 알림이 없습니다.');
      return;
    }

    loadedNotifications.forEach((notification) => {
      const card = document.createElement('button');
      card.type = 'button';
      card.className = 'notification-card';
      card.setAttribute('aria-label', `${notification.title || '알림'} 읽음 처리`);

      const heading = document.createElement('span');
      heading.className = 'notification-card-header';
      const dot = document.createElement('span');
      dot.className = 'notification-unread-dot';
      dot.setAttribute('aria-hidden', 'true');
      const title = document.createElement('span');
      title.className = 'notification-card-title';
      title.textContent = notification.title || '알림';
      const time = document.createElement('time');
      time.className = 'notification-card-time';
      time.dateTime = notification.createdAt || '';
      time.textContent = relativeTime(notification.createdAt);
      heading.append(dot, title, time);

      const content = document.createElement('span');
      content.className = 'notification-card-content';
      content.textContent = notification.content || '';
      card.append(heading, content);
      card.addEventListener('click', () => markAsRead(notification.id, card, notification.url));
      list.append(card);
    });
  }

  async function refreshCount() {
    const response = await apiFetch('/api/notifications/unread/count');
    if (!response.ok) throw new Error('인증이 필요하거나 알림 개수를 불러오지 못했습니다.');
    setUnreadCount(await response.json());
  }

  async function loadNotifications() {
    showState('알림을 불러오는 중입니다.');
    try {
      const response = await apiFetch('/api/notifications/unread');
      if (!response.ok) throw new Error('알림을 불러오지 못했습니다. 다시 로그인해 주세요.');
      loadedNotifications = await response.json();
      renderNotifications();
      await refreshCount();
    } catch (error) {
      showState(error.message || '알림을 불러오지 못했습니다.');
    }
  }

  async function markAsRead(notificationId, card, url) {
    card.disabled = true;
    try {
      const response = await apiFetch(`/api/notifications/${notificationId}/read`, { method: 'PATCH' });
      if (!response.ok) throw new Error('읽음 처리에 실패했습니다.');
      loadedNotifications = loadedNotifications.filter((item) => item.id !== notificationId);
      renderNotifications();
      await refreshCount();
      if (url) location.href = url;
    } catch (error) {
      card.disabled = false;
      showState(error.message || '읽음 처리에 실패했습니다.');
    }
  }

  function openPanel() {
    panel.hidden = false;
    trigger.setAttribute('aria-expanded', 'true');
    trigger.setAttribute('aria-label', '알림 닫기');
    loadNotifications();
  }

  function closePanel() {
    panel.hidden = true;
    trigger.setAttribute('aria-expanded', 'false');
    trigger.setAttribute('aria-label', '알림 열기');
  }

  trigger.addEventListener('click', () => {
    if (panel.hidden) openPanel();
    else closePanel();
  });
  closeButton.addEventListener('click', closePanel);
  document.addEventListener('click', (event) => {
    if (!panel.hidden && !event.target.closest('.notification-area')) closePanel();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !panel.hidden) {
      closePanel();
      trigger.focus();
    }
  });

  refreshCount()
    .then(() => {
      eventSource = new EventSource('/api/notifications/subscribe');
      eventSource.addEventListener('notification', (event) => {
        try {
          const notification = JSON.parse(event.data);
          if (!loadedNotifications.some((item) => item.id === notification.id)) {
            loadedNotifications.unshift(notification);
          }
          setUnreadCount(unreadCount + 1);
          if (!panel.hidden) renderNotifications();
        } catch (error) {
          console.error('알림 데이터를 읽지 못했습니다.', error);
        }
      });
    })
    .catch(() => {
      // 로그인하지 않은 화면에서는 알림 API와 SSE 연결을 시도하지 않습니다.
    });
})();
