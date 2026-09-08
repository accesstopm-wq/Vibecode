(() => {
  const app = document.getElementById('app');
  const lastKey = document.getElementById('lastKey');
  const history = document.getElementById('history');
  const items = [...document.querySelectorAll('.test-item')];
  let focusIndex = 0;
  const events = [];

  function codeOf(e) {
    return Number(e.keyCode || e.which || 0);
  }

  function log(e, type) {
    const code = codeOf(e);
    const key = e.key || e.keyIdentifier || '(none)';
    const line = `${type}: key=${key} | code=${code} | which=${e.which || 0}`;
    lastKey.textContent = line;
    events.unshift(line);
    events.splice(10);
    history.textContent = events.join('\n');
  }

  function renderFocus() {
    items.forEach((item, i) => item.classList.toggle('remote-focus', i === focusIndex));
  }

  function move(dx, dy) {
    const cols = 3;
    let row = Math.floor(focusIndex / cols);
    let col = focusIndex % cols;
    row = Math.max(0, Math.min(1, row + dy));
    col = Math.max(0, Math.min(cols - 1, col + dx));
    focusIndex = row * cols + col;
    renderFocus();
    lastKey.textContent += ` | FOCUS=${items[focusIndex].dataset.name}`;
  }

  function handleKey(e) {
    log(e, e.type);

    const code = codeOf(e);
    let handled = true;

    if (code === 37) move(-1, 0);
    else if (code === 38) move(0, -1);
    else if (code === 39) move(1, 0);
    else if (code === 40) move(0, 1);
    else if (code === 13) {
      lastKey.textContent += ` | OK=${items[focusIndex].dataset.name}`;
    } else if (code === 8 || code === 27 || code === 10009) {
      lastKey.textContent += ' | BACK';
    } else {
      handled = false;
    }

    if (handled) {
      if (e.cancelable) e.preventDefault();
      e.stopPropagation();
    }
  }

  // One listener only. This matches the basic navigation pattern from the VIDAA guide.
  document.addEventListener('keydown', handleKey, false);
  document.addEventListener('keyup', e => log(e, 'keyup'), false);
  document.addEventListener('keypress', e => log(e, 'keypress'), false);

  // Keep keyboard focus on a non-native element so browser button navigation cannot interfere.
  app.focus();
  renderFocus();
})();
