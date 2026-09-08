(() => {
  const pages = [...document.querySelectorAll('.page')];
  let current = 'home';
  let focusIndex = 0;

  const KEY = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACKSPACE: 8, ESC: 27, VIDAA_BACK: 10009 };

  function currentPage() {
    return document.querySelector(`.page[data-page="${current}"]`);
  }

  function focusables() {
    return [...currentPage().querySelectorAll('.focusable')];
  }

  function setFocus(index) {
    const items = focusables();
    if (!items.length) return;
    focusIndex = Math.max(0, Math.min(items.length - 1, index));
    items[focusIndex].focus({ preventScroll: true });
  }

  function show(page) {
    current = page;
    pages.forEach(p => p.classList.toggle('active', p.dataset.page === page));
    focusIndex = 0;
    requestAnimationFrame(() => setFocus(0));
  }

  function moveFocus(dx, dy) {
    const items = focusables();
    if (!items.length) return;
    const cols = current === 'home' ? 2 : 1;
    const rows = Math.ceil(items.length / cols);
    let row = Math.floor(focusIndex / cols);
    let col = focusIndex % cols;
    if (dx) col = Math.max(0, Math.min(cols - 1, col + dx));
    if (dy) row = Math.max(0, Math.min(rows - 1, row + dy));
    setFocus(Math.min(items.length - 1, row * cols + col));
  }

  function activate() {
    const items = focusables();
    if (items[focusIndex]) items[focusIndex].click();
  }

  function goBack() {
    if (current !== 'home') show('home');
  }

  function getCode(e) {
    return Number(e.keyCode || e.which || e.detail || 0);
  }

  function getKey(e) {
    const code = getCode(e);
    const key = String(e.key || e.keyIdentifier || '').toLowerCase();
    if (code === KEY.LEFT || key === 'arrowleft' || key === 'left') return 'left';
    if (code === KEY.UP || key === 'arrowup' || key === 'up') return 'up';
    if (code === KEY.RIGHT || key === 'arrowright' || key === 'right') return 'right';
    if (code === KEY.DOWN || key === 'arrowdown' || key === 'down') return 'down';
    if (code === KEY.ENTER || key === 'enter' || key === 'ok') return 'enter';
    if (code === KEY.BACKSPACE || code === KEY.ESC || code === KEY.VIDAA_BACK || key === 'backspace' || key === 'escape' || key === 'back') return 'back';
    return '';
  }

  function handleRemoteKey(e) {
    const code = getCode(e);
    const keyName = getKey(e);
    document.getElementById('status').textContent = `KEY: ${e.key || 'unknown'} (${code})`;
    if (current === 'info') document.getElementById('keyInfo').textContent = `Остання кнопка: ${e.key || 'unknown'} · keyCode: ${code}`;
    if (!keyName) return;
    if (e.cancelable) e.preventDefault();
    e.stopPropagation();
    switch (keyName) {
      case 'left': moveFocus(-1, 0); break;
      case 'right': moveFocus(1, 0); break;
      case 'up': moveFocus(0, -1); break;
      case 'down': moveFocus(0, 1); break;
      case 'enter': activate(); break;
      case 'back': goBack(); break;
    }
  }

  // Capture phase works better with TV browsers that intercept remote keys.
  window.addEventListener('keydown', handleRemoteKey, true);

  document.querySelectorAll('[data-target]').forEach(btn => {
    btn.addEventListener('click', () => show(btn.dataset.target));
  });
  document.querySelectorAll('.back-btn').forEach(btn => {
    btn.addEventListener('click', () => show('home'));
  });
  document.querySelectorAll('.focusable').forEach(btn => btn.setAttribute('tabindex', '0'));
  show('home');
})();
