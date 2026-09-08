(() => {
  const pages = [...document.querySelectorAll('.page')];
  let current = 'home';
  let focusIndex = 0;

  function currentPage() { return document.querySelector(`.page[data-page="${current}"]`); }
  function focusables() { return [...currentPage().querySelectorAll('.focusable')]; }

  function show(page) {
    current = page;
    pages.forEach(p => p.classList.toggle('active', p.dataset.page === page));
    focusIndex = 0;
    setTimeout(() => focusables()[0]?.focus(), 0);
  }

  function moveFocus(dx, dy) {
    const items = focusables();
    if (!items.length) return;
    const cols = current === 'home' ? 2 : 1;
    let row = Math.floor(focusIndex / cols);
    let col = focusIndex % cols;
    if (dx) col = Math.max(0, Math.min(cols - 1, col + dx));
    if (dy) row = Math.max(0, Math.min(Math.ceil(items.length / cols) - 1, row + dy));
    const next = Math.min(items.length - 1, row * cols + col);
    focusIndex = next;
    items[focusIndex].focus();
  }

  document.addEventListener('keydown', e => {
    const key = e.key;
    const code = e.keyCode;
    document.getElementById('status').textContent = `KEY: ${key} (${code})`;
    if (current === 'info') document.getElementById('keyInfo').textContent = `Остання кнопка: ${key} · keyCode: ${code}`;

    if (key === 'ArrowUp' || code === 38) { e.preventDefault(); moveFocus(0, -1); }
    else if (key === 'ArrowDown' || code === 40) { e.preventDefault(); moveFocus(0, 1); }
    else if (key === 'ArrowLeft' || code === 37) { e.preventDefault(); moveFocus(-1, 0); }
    else if (key === 'ArrowRight' || code === 39) { e.preventDefault(); moveFocus(1, 0); }
    else if (key === 'Enter' || code === 13) { e.preventDefault(); document.activeElement?.click(); }
    else if (key === 'Escape' || code === 8 || key === 'Backspace') { e.preventDefault(); if (current !== 'home') show('home'); }
  });

  document.querySelectorAll('[data-target]').forEach(btn => btn.addEventListener('click', () => show(btn.dataset.target)));
  document.querySelectorAll('.back-btn').forEach(btn => btn.addEventListener('click', () => show('home')));
  show('home');
})();
