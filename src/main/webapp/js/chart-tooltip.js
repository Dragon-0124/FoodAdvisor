/*
 * Canvas 그래프 점 툴팁 - 마우스를 점 가까이 올리면 날짜·값을 보여줍니다.
 *  사용: FoodAdvisorTooltip.set(canvas, [{ x, y, title, lines: [...] }])  (x, y 는 canvas 안의 CSS 픽셀 좌표)
 *  그래프를 다시 그릴 때마다 set 을 다시 호출하면 됩니다.
 */
(() => {
  const HIT_RADIUS = 18;   // 이 거리(px) 안의 가장 가까운 점을 보여줌
  const targets = new WeakMap();
  let tip = null;
  let ring = null;

  function ensureElements() {
    if (tip) return;
    const style = document.createElement('style');
    style.textContent = `
      .chart-tip { position:fixed; z-index:1000; pointer-events:none; max-width:240px; padding:8px 11px; border-radius:10px;
        background:rgba(30,22,48,.92); color:#fff; font:12px/1.5 "Malgun Gothic","Apple SD Gothic Neo",sans-serif;
        box-shadow:0 6px 18px rgba(20,10,40,.25); white-space:nowrap; }
      .chart-tip b { display:block; margin-bottom:2px; font-size:12px; color:#d9ccf5; font-weight:700; }
      .chart-tip-ring { position:fixed; z-index:999; pointer-events:none; width:16px; height:16px; margin:-8px 0 0 -8px;
        border:3px solid rgba(120,86,216,.35); border-radius:50%; }`;
    document.head.appendChild(style);
    tip = document.createElement('div');
    tip.className = 'chart-tip';
    tip.hidden = true;
    ring = document.createElement('div');
    ring.className = 'chart-tip-ring';
    ring.hidden = true;
    document.body.append(tip, ring);
  }

  function hide() {
    if (tip) { tip.hidden = true; ring.hidden = true; }
  }

  function onMove(event) {
    const canvas = event.currentTarget;
    const points = targets.get(canvas) || [];
    const rect = canvas.getBoundingClientRect();
    const mx = event.clientX - rect.left;
    const my = event.clientY - rect.top;
    let best = null;
    let bestDistance = HIT_RADIUS;
    points.forEach(point => {
      const distance = Math.hypot(point.x - mx, point.y - my);
      if (distance <= bestDistance) {
        best = point;
        bestDistance = distance;
      }
    });
    if (!best) { hide(); return; }

    tip.replaceChildren();
    const title = document.createElement('b');
    title.textContent = best.title;
    tip.appendChild(title);
    best.lines.forEach(line => {
      const row = document.createElement('div');
      row.textContent = line;
      tip.appendChild(row);
    });
    tip.hidden = false;
    ring.hidden = false;
    const px = rect.left + best.x;
    const py = rect.top + best.y;
    ring.style.left = `${px}px`;
    ring.style.top = `${py}px`;
    // 점 위쪽에 띄우고, 화면 밖으로 나가면 안쪽으로 당김
    const width = tip.offsetWidth;
    const height = tip.offsetHeight;
    let left = px - width / 2;
    let top = py - height - 14;
    if (top < 8) top = py + 14;
    left = Math.max(8, Math.min(left, window.innerWidth - width - 8));
    tip.style.left = `${left}px`;
    tip.style.top = `${top}px`;
    canvas.style.cursor = 'pointer';
  }

  function set(canvas, points) {
    ensureElements();
    if (!targets.has(canvas)) {
      canvas.addEventListener('mousemove', onMove);
      canvas.addEventListener('mouseleave', () => { hide(); canvas.style.cursor = ''; });
    }
    targets.set(canvas, points);
    hide();
  }

  window.addEventListener('scroll', hide, { passive: true });

  // 날짜 문자열 표시 도우미
  const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
  function longDate(dateString) {
    const [y, m, d] = dateString.split('-').map(Number);
    return `${y}년 ${m}월 ${d}일 (${WEEKDAYS[new Date(y, m - 1, d).getDay()]})`;
  }

  window.FoodAdvisorTooltip = { set, hide, longDate };
})();
