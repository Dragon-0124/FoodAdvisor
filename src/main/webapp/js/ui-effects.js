/* FoodAdvisor subtle interaction effects. Visual only; does not change app data. */
(() => {
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  document.documentElement.classList.add('fa-effects-ready');

  // A very soft, outline-only click ring appears at every primary mouse click.
  // It is fixed to the viewport so clicks on empty space are visible too.
  document.addEventListener('pointerdown', (event) => {
    if (event.button !== 0 || reducedMotion) return;
    const ripple = document.createElement('span');
    ripple.className = 'fa-click-ripple';
    ripple.style.left = `${event.clientX}px`;
    ripple.style.top = `${event.clientY}px`;
    document.body.appendChild(ripple);
    ripple.addEventListener('animationend', () => ripple.remove(), { once: true });
  }, { passive: true });

  // Native details menus get a smooth visual opening/closing treatment.
  document.querySelectorAll('details').forEach((details) => {
    details.addEventListener('toggle', () => {
      details.classList.toggle('fa-details-open', details.open);
    });
  });
})();
