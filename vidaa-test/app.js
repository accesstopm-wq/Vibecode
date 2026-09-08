(() => {
  const lastKey = document.getElementById('lastKey');
  const history = document.getElementById('history');
  const buttons = [...document.querySelectorAll('button')];
  let focusIndex = 0;
  const events = [];

  function codeOf(e) {
    return Number(e.keyCode || e.which || 0);
  }

  function describe(e, type) {
    const code = codeOf(e);
    const key = e.key || e.keyIdentifier || '(none)';
    const line = `${type}: key=${key} | keyCode=${code} | which=${e.which || 0}`;
    lastKey.textContent = line;
    events.unshift(line);
    events.splice(8);
    history.textContent = events.join('\n');
  }

  function focusButton(i) {
    focusIndex = Math.max(0, Math.min(buttons.length - 1, i));
    buttons[focusIndex].focus();
    lastKey.textContent += ` | FOCUS=${buttons[focusIndex].dataset.name}`;
  }

  function move(dx, dy) {
    const cols = 3;
    let row = Math.floor(focusIndex / cols);
    let col = focusIndex % cols;
    row = Math.max(0, Math.min(1, row + dy));
    col = Math.max(0, Math.min(cols - 1, col + dx));
    focusButton(row * cols + col);
  }

  function handle(e) {
    describe(e, e.type);
    const code = codeOf(e);
    let handled = true;
    if (code === 37) move(-1, 0);
    else if (code === 38) move(0, -1);
    else if (code === 39) move(1, 0);
    else if (code === 40) move(0, 1);
    else if (code === 13) buttons[focusIndex].click();
    else if (code === 8 || code === 10009 || code === 27) {
      lastKey.textContent += ' | BACK';
    } else handled = false;
    if (handled) {
      if (e.cancelable) e.preventDefault();
      e.stopPropagation();
    }
  }

  // Listen in both capture and bubble phases for maximum VIDAA compatibility.
  window.addEventListener('keydown', handle, true);
  document.addEventListener('keydown', handle, false);
  window.addEventListener('keyup', e => describe(e, 'keyup'), true);
  document.addEventListener('keyup', e => describe(e, 'keyup'), false);
  window.addEventListener('keypress', e => describe(e, 'keypress'), true);

  buttons.forEach((button, i) => {
    button.addEventListener('click', () => {
      lastKey.textContent = `CLICK: ${button.dataset.name} | index=${i}`;
    });
  });

  focusButton(0);
})();
