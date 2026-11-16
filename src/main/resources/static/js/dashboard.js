// 대시보드 공통 코드 (관리자 /test/admin, 가게 /shop/dashboard). Chart.js를 먼저 불러온 뒤에 쓴다.
// 사용: Dash.start({ api: '/api/...', render: data => { ... } })
window.Dash = (() => {
    const num = value => Number(value || 0).toLocaleString('ko-KR');
    const fmt = {
        won: value => num(value) + '원',
        count: value => num(value) + '건',
        person: value => num(value) + '명',
        shop: value => num(value) + '곳'
    };

    // 숫자 칸 하나. href가 있으면 처리 화면으로 가는 링크가 된다. 값은 textContent로 넣어 HTML이 섞이지 않게 한다.
    function addStat(container, label, value, note, options = {}) {
        // href면 다른 화면으로 가는 링크, onClick이면 (작은 창을 여는 등) 그 자리에서 동작하는 칸이 된다
        const clickable = options.href || options.onClick;
        const stat = document.createElement(clickable ? 'a' : 'div');
        stat.className = 'stat' + (options.todo ? ' is-todo' : '');
        if (options.href) stat.href = options.href;
        if (options.onClick) {
            stat.href = '#';
            stat.addEventListener('click', event => { event.preventDefault(); options.onClick(); });
        }
        const l = document.createElement('span');
        l.textContent = label;
        const v = document.createElement('strong');
        v.textContent = value;
        const n = document.createElement('small');
        n.textContent = note || '';
        stat.append(l, v, n);
        container.appendChild(stat);
    }

    // 지난달 대비 증감. 지난달이 0이면 비율을 낼 수 없어 '지난달 없음' 또는 '-'로 보여준다.
    function delta(current, previous) {
        if (!previous) return current > 0 ? '지난달 없음' : '-';
        // 3배 이상 늘면 '▲ 642%' 대신 '▲ 지난달의 7.4배'로 보여준다 (지난달 값이 작아 비율이 과하게 커지는 경우)
        if (current / previous >= 3) return '▲ 지난달의 ' + (Math.round(current / previous * 10) / 10) + '배';
        const diff = Math.round((current - previous) / previous * 100);
        return (diff > 0 ? '▲ ' : diff < 0 ? '▼ ' : '') + Math.abs(diff) + '%';
    }

    // 사이트 폰트·색에 맞춰 무채색으로 그린다. 막대 시리즈는 검정과 회색 두 가지만 쓴다.
    Chart.defaults.font.family = 'Pretendard, sans-serif';
    Chart.defaults.font.size = 11;
    Chart.defaults.color = '#8C8C8C';
    const DARK = '#1A1A1A';
    const GRAY = '#C9C9C7';
    const charts = {};

    // series: [{label, data, light}] (light=true면 회색). stacked면 쌓아서 보여준다.
    function drawChart(canvasId, labels, series, format, stacked) {
        if (charts[canvasId]) charts[canvasId].destroy();
        charts[canvasId] = new Chart(document.getElementById(canvasId), {
            type: 'bar',
            data: {
                labels: labels,
                datasets: series.map(s => ({
                    label: s.label, data: s.data, backgroundColor: s.light ? GRAY : DARK, maxBarThickness: 28
                }))
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: { stacked: !!stacked, grid: { display: false }, border: { color: '#E4E4E2' } },
                    y: { stacked: !!stacked, beginAtZero: true, border: { display: false }, grid: { color: '#EFEFED' },
                         ticks: { precision: 0, maxTicksLimit: 4, callback: value => format(value) } }
                },
                plugins: {
                    legend: { display: series.length > 1, position: 'bottom', labels: { boxWidth: 8, boxHeight: 8 } },
                    tooltip: { callbacks: { label: context => context.dataset.label + ': ' + format(context.raw) } }
                }
            }
        });
    }

    const monthLabels = months => months.map(m => m.month.slice(5) + '월');

    // 월 선택 폼과 데이터 조회를 연결한다. render(data)는 응답을 받을 때마다 화면을 다시 그린다.
    function start({ api, render }) {
        const form = document.getElementById('dashboard-form');
        const monthInput = document.getElementById('month');
        const selectedMonth = document.getElementById('selected-month');
        const message = document.getElementById('dashboard-message');
        const errorMessage = { 400: '날짜 형식이 올바르지 않습니다.', 403: '이용 권한이 없습니다.' };

        // 오늘 기준 이번 달(브라우저 현지 시간)을 YYYY-MM으로 만든다.
        const now = new Date();
        monthInput.value = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');

        async function load(month) {
            message.textContent = '통계를 불러오는 중입니다.';
            message.className = 'dash-note';
            try {
                const response = await fetch(api + '?month=' + encodeURIComponent(month), { credentials: 'same-origin' });
                if (!response.ok) {
                    message.textContent = errorMessage[response.status] || '대시보드 통계를 불러오지 못했습니다.';
                    message.className = 'dash-note dash-error';
                    return;
                }
                const data = await response.json();
                selectedMonth.textContent = month + ' 기준 통계';
                message.textContent = '';
                render(data);
            } catch (error) {
                message.textContent = '서버와 통신하지 못했습니다.';
                message.className = 'dash-note dash-error';
            }
        }

        // 월이 비어 있는 경우는 input의 required가 막아 주므로 따로 검사하지 않는다.
        form.addEventListener('submit', event => {
            event.preventDefault();
            load(monthInput.value);
        });
        load(monthInput.value);
    }

    return { fmt, addStat, delta, drawChart, monthLabels, start };
})();
