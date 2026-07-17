/* Kinbridge — Brain Bloom: wellness-adaptive crossword puzzles. */

/* ---------------- Puzzle data (hand-built, validated for letter conflicts) ---------------- */

const PUZZLES = {
  easy: [
    {
      id: 'easy-home',
      theme: 'At Home',
      words: [
        { direction: 'across', row: 0, col: 0, answer: 'LAMP', clue: 'You turn this on to read at night' },
        { direction: 'down', row: 0, col: 0, answer: 'LID', clue: 'A cover for a pot' },
        { direction: 'down', row: 0, col: 1, answer: 'ARM', clue: 'Part of your body from shoulder to hand' },
        { direction: 'down', row: 0, col: 2, answer: 'MOP', clue: 'Used to clean the floor' },
        { direction: 'down', row: 0, col: 3, answer: 'PEN', clue: 'You write with this' },
      ],
    },
    {
      id: 'easy-kitchen',
      theme: 'In the Kitchen',
      words: [
        { direction: 'across', row: 0, col: 0, answer: 'CAKE', clue: 'A sweet treat, often for birthdays' },
        { direction: 'down', row: 0, col: 0, answer: 'CUP', clue: 'You drink coffee from this' },
        { direction: 'down', row: 0, col: 1, answer: 'ANT', clue: 'A tiny insect that loves picnics' },
        { direction: 'down', row: 0, col: 2, answer: 'KEY', clue: 'Opens a locked door' },
        { direction: 'down', row: 0, col: 3, answer: 'EAR', clue: 'Part of the body used for hearing' },
      ],
    },
  ],
  medium: [
    {
      id: 'medium-weather',
      theme: 'Weather',
      words: [
        { direction: 'across', row: 0, col: 0, answer: 'STORM', clue: 'Heavy wind and rain together' },
        { direction: 'down', row: 0, col: 0, answer: 'SNOW', clue: 'White flakes that fall in winter' },
        { direction: 'down', row: 0, col: 1, answer: 'TREE', clue: 'Tall plant with leaves and branches' },
        { direction: 'down', row: 0, col: 2, answer: 'OPEN', clue: 'Not closed' },
        { direction: 'down', row: 0, col: 3, answer: 'RAIN', clue: 'Water falling from clouds' },
        { direction: 'down', row: 0, col: 4, answer: 'MOON', clue: 'You see it glowing at night in the sky' },
      ],
    },
    {
      id: 'medium-town',
      theme: 'Around Town',
      words: [
        { direction: 'across', row: 0, col: 0, answer: 'CHAIR', clue: 'A seat with a back and legs' },
        { direction: 'down', row: 0, col: 0, answer: 'COAT', clue: 'You wear this outside when it is cold' },
        { direction: 'down', row: 0, col: 1, answer: 'HOME', clue: 'The place where you live' },
        { direction: 'down', row: 0, col: 2, answer: 'AUNT', clue: "Your parent's sister" },
        { direction: 'down', row: 0, col: 3, answer: 'IDEA', clue: 'A thought or plan' },
        { direction: 'down', row: 0, col: 4, answer: 'ROAD', clue: 'Cars and bikes travel on this' },
      ],
    },
  ],
  hard: [
    {
      id: 'hard-spring',
      theme: 'Seasons',
      words: [
        { direction: 'across', row: 0, col: 0, answer: 'SPRING', clue: 'The season when flowers bloom' },
        { direction: 'down', row: 0, col: 0, answer: 'SUGAR', clue: 'Sweet white or brown granules used in baking' },
        { direction: 'down', row: 0, col: 1, answer: 'PEACH', clue: 'A fuzzy orange-pink fruit' },
        { direction: 'down', row: 0, col: 2, answer: 'RIVER', clue: 'A large flowing body of water' },
        { direction: 'down', row: 0, col: 3, answer: 'IMAGE', clue: 'A picture or photo' },
        { direction: 'down', row: 0, col: 4, answer: 'NIGHT', clue: 'When the sky is dark and stars come out' },
        { direction: 'down', row: 0, col: 5, answer: 'GRAPE', clue: 'A small round fruit that grows in bunches' },
      ],
    },
    {
      id: 'hard-ocean',
      theme: 'Ocean Life',
      words: [
        { direction: 'across', row: 0, col: 0, answer: 'DOLPHIN', clue: 'A playful, friendly sea animal known for its clicks' },
        { direction: 'down', row: 0, col: 0, answer: 'DIVER', clue: 'A person who explores underwater' },
        { direction: 'down', row: 0, col: 1, answer: 'OTTER', clue: 'A playful animal that swims in rivers and the sea' },
        { direction: 'down', row: 0, col: 2, answer: 'LEMON', clue: 'A sour yellow fruit' },
        { direction: 'down', row: 0, col: 3, answer: 'PEARL', clue: 'A small white gem found in an oyster' },
        { direction: 'down', row: 0, col: 4, answer: 'HORSE', clue: 'An animal you can ride' },
        { direction: 'down', row: 0, col: 5, answer: 'IGLOO', clue: 'A house made of snow and ice' },
        { direction: 'down', row: 0, col: 6, answer: 'NORTH', clue: 'The direction opposite of south' },
      ],
    },
  ],
};

/* ---------------- Grid engine (pure) ---------------- */

function buildGrid(words) {
  let maxRow = 0;
  let maxCol = 0;
  for (const word of words) {
    const endRow = word.direction === 'down' ? word.row + word.answer.length - 1 : word.row;
    const endCol = word.direction === 'across' ? word.col + word.answer.length - 1 : word.col;
    maxRow = Math.max(maxRow, endRow);
    maxCol = Math.max(maxCol, endCol);
  }
  const rows = maxRow + 1;
  const cols = maxCol + 1;
  const cells = Array.from({ length: rows }, () => Array.from({ length: cols }, () => null));
  words.forEach((word, wordIndex) => {
    for (let i = 0; i < word.answer.length; i += 1) {
      const r = word.direction === 'down' ? word.row + i : word.row;
      const c = word.direction === 'across' ? word.col + i : word.col;
      if (!cells[r][c]) {
        cells[r][c] = {
          answer: word.answer[i], number: null, across: null, down: null,
        };
      }
      cells[r][c][word.direction] = wordIndex;
    }
  });
  return { rows, cols, cells };
}

/** Assigns standard crossword numbering: scan row-major, number each word start cell once. */
function numberGrid(grid) {
  let next = 1;
  for (let r = 0; r < grid.rows; r += 1) {
    for (let c = 0; c < grid.cols; c += 1) {
      const cell = grid.cells[r][c];
      if (!cell) continue;
      const startsAcross = cell.across !== null && (c === 0 || !grid.cells[r][c - 1]);
      const startsDown = cell.down !== null && (r === 0 || !grid.cells[r - 1][c]);
      if (startsAcross || startsDown) {
        cell.number = next;
        next += 1;
      }
    }
  }
}

function wordCells(words, wordIndex) {
  const word = words[wordIndex];
  const cells = [];
  for (let i = 0; i < word.answer.length; i += 1) {
    cells.push({
      row: word.direction === 'down' ? word.row + i : word.row,
      col: word.direction === 'across' ? word.col + i : word.col,
    });
  }
  return cells;
}

function firstCell(grid) {
  for (let r = 0; r < grid.rows; r += 1) {
    for (let c = 0; c < grid.cols; c += 1) {
      if (grid.cells[r][c]) return { row: r, col: c };
    }
  }
  return null;
}

/* ---------------- Wellness-adaptive recommendation ---------------- */

const FALLBACK_PLAN = {
  tier: 'unknown',
  primary: 'medium',
  weights: { easy: 0.34, medium: 0.33, hard: 0.33 },
  goal: 2,
  index: null,
};

const PLAN_COPY = {
  unknown: () => "Kin doesn't have enough check-ins yet to tailor puzzles — here's a balanced mix to start. "
    + 'Recommendations sharpen after a few more chats with Kin.',
  focus: (plan) => `Recent check-ins (recall index ${plan.index}) suggest extra practice helps most right now — `
    + 'Kin recommends Hard puzzles today, with plenty of Medium mixed in.',
  balanced: (plan) => `Recall index ${plan.index} looks steady — Kin recommends a Medium puzzle today, `
    + 'with a little variety from Easy and Hard.',
  light: (plan) => `Recall index ${plan.index} looks strong — Kin recommends Easy puzzles today just for fun, `
    + 'with occasional Medium to keep things interesting.',
};

function weightedPick(weights) {
  const entries = Object.entries(weights);
  const total = entries.reduce((sum, [, weight]) => sum + weight, 0);
  let roll = Math.random() * total;
  for (const [difficulty, weight] of entries) {
    if (roll < weight) return difficulty;
    roll -= weight;
  }
  return entries[entries.length - 1][0];
}

/* ---------------- Today's progress (persisted like the chat history) ---------------- */

function gamesStorageKey() {
  return `kinbridge:games:${todayKey()}`;
}

function loadTodayProgress() {
  try {
    const raw = localStorage.getItem(gamesStorageKey());
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function recordCompletion(puzzleId, difficulty) {
  const entries = loadTodayProgress();
  entries.push({ id: puzzleId, difficulty, completedAt: new Date().toISOString() });
  try {
    localStorage.setItem(gamesStorageKey(), JSON.stringify(entries));
  } catch {
    // Storage may be full or blocked; progress just won't persist across reloads.
  }
}

/* ---------------- State ---------------- */

let currentPlan = FALLBACK_PLAN;
const lastPlayedId = { easy: null, medium: null, hard: null };
let session = null;

/* ---------------- Entry point (called by app.js when the Games view opens) ---------------- */

async function loadGames() {
  showPicker();
  try {
    const data = await fetchJson('/api/screening');
    currentPlan = data.gamePlan ?? FALLBACK_PLAN;
  } catch (error) {
    console.error('Failed to load wellness data for games:', error);
    currentPlan = FALLBACK_PLAN;
  }
  renderBanner();
  renderDifficultyPicker();
  renderProgressTile();
}

function renderBanner() {
  const banner = document.getElementById('games-banner');
  const copy = PLAN_COPY[currentPlan.tier] ?? PLAN_COPY.balanced;
  banner.textContent = copy(currentPlan);
  banner.hidden = false;
}

function renderDifficultyPicker() {
  for (const card of document.querySelectorAll('.difficulty-card')) {
    const isPrimary = card.dataset.difficulty === currentPlan.primary;
    card.classList.toggle('recommended', isPrimary);
    const badge = card.querySelector('.difficulty-badge');
    if (badge) badge.hidden = !isPrimary;
  }
}

function renderProgressTile() {
  document.getElementById('stat-goal').textContent = String(currentPlan.goal);
  document.getElementById('stat-completed').textContent = String(loadTodayProgress().length);
}

/* ---------------- View toggling ---------------- */

function showPicker() {
  document.getElementById('difficulty-section').hidden = false;
  document.getElementById('puzzle-board').hidden = true;
}

function showBoard() {
  document.getElementById('difficulty-section').hidden = true;
  document.getElementById('puzzle-board').hidden = false;
}

/* ---------------- Puzzle selection ---------------- */

function capitalize(text) {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function playRandomPuzzle(difficulty) {
  const pool = PUZZLES[difficulty];
  let choice = pool[Math.floor(Math.random() * pool.length)];
  if (pool.length > 1 && choice.id === lastPlayedId[difficulty]) {
    choice = pool.find((puzzle) => puzzle.id !== choice.id) ?? choice;
  }
  lastPlayedId[difficulty] = choice.id;
  startPuzzle(choice, difficulty);
}

function startPuzzle(puzzleData, difficulty) {
  const grid = buildGrid(puzzleData.words);
  numberGrid(grid);
  const dims = () => Array.from({ length: grid.rows }, () => Array(grid.cols).fill(null));
  const first = firstCell(grid);

  session = {
    puzzle: puzzleData,
    difficulty,
    grid,
    entries: Array.from({ length: grid.rows }, () => Array(grid.cols).fill('')),
    revealed: dims(),
    results: dims(),
    selected: first,
    direction: first && grid.cells[first.row][first.col].across !== null ? 'across' : 'down',
    solved: false,
    hintsUsed: 0,
  };

  document.getElementById('puzzle-title').textContent = puzzleData.theme;
  const chip = document.getElementById('puzzle-difficulty-chip');
  chip.textContent = capitalize(difficulty);
  chip.className = `chip difficulty-chip-${difficulty}`;
  document.getElementById('puzzle-complete').hidden = true;
  document.getElementById('puzzle-status').hidden = true;

  showBoard();
  render();
}

/* ---------------- Selection helpers ---------------- */

function activeWordIndex() {
  if (!session.selected) return null;
  const cell = session.grid.cells[session.selected.row][session.selected.col];
  return cell ? cell[session.direction] : null;
}

function selectCell(row, col) {
  const cell = session.grid.cells[row][col];
  if (!cell) return;
  const isSame = session.selected && session.selected.row === row && session.selected.col === col;
  if (isSame && cell.across !== null && cell.down !== null) {
    session.direction = session.direction === 'across' ? 'down' : 'across';
  } else if (cell[session.direction] === null) {
    session.direction = cell.across !== null ? 'across' : 'down';
  }
  session.selected = { row, col };
  render();
}

function selectWord(wordIndex) {
  const word = session.puzzle.words[wordIndex];
  session.direction = word.direction;
  session.selected = { row: word.row, col: word.col };
  render();
}

function moveSelection(row, col) {
  const { grid } = session;
  if (row < 0 || col < 0 || row >= grid.rows || col >= grid.cols) return;
  if (!grid.cells[row][col]) return;
  session.selected = { row, col };
  if (grid.cells[row][col][session.direction] === null) {
    session.direction = grid.cells[row][col].across !== null ? 'across' : 'down';
  }
  render();
}

function moveToNextCell(row, col) {
  const wordIndex = session.grid.cells[row][col][session.direction];
  if (wordIndex === null) return;
  const cells = wordCells(session.puzzle.words, wordIndex);
  const index = cells.findIndex((p) => p.row === row && p.col === col);
  const next = cells[index + 1];
  if (next) {
    session.selected = next;
  }
}

function moveToPreviousCell(row, col) {
  const wordIndex = session.grid.cells[row][col][session.direction];
  if (wordIndex === null) return;
  const cells = wordCells(session.puzzle.words, wordIndex);
  const index = cells.findIndex((p) => p.row === row && p.col === col);
  const prev = cells[index - 1];
  if (prev) {
    session.entries[prev.row][prev.col] = '';
    session.results[prev.row][prev.col] = null;
    session.revealed[prev.row][prev.col] = false;
    session.selected = prev;
  }
}

function toggleDirection() {
  const { selected, grid } = session;
  const cell = grid.cells[selected.row][selected.col];
  if (cell.across !== null && cell.down !== null) {
    session.direction = session.direction === 'across' ? 'down' : 'across';
  }
}

/* ---------------- Input handling ---------------- */

function handleCellInput(row, col, event) {
  const letter = event.target.value.replace(/[^a-zA-Z]/g, '').slice(-1).toUpperCase();
  session.entries[row][col] = letter;
  session.results[row][col] = null;
  session.revealed[row][col] = false;
  if (letter) {
    moveToNextCell(row, col);
  }
  render();
  maybeAutoCheck();
}

const ARROW_DELTAS = {
  ArrowRight: [0, 1],
  ArrowLeft: [0, -1],
  ArrowDown: [1, 0],
  ArrowUp: [-1, 0],
};

function handleCellKeydown(row, col, event) {
  const { key } = event;
  if (ARROW_DELTAS[key]) {
    event.preventDefault();
    const [dRow, dCol] = ARROW_DELTAS[key];
    moveSelection(row + dRow, col + dCol);
  } else if (key === 'Backspace' && event.target.value === '') {
    event.preventDefault();
    moveToPreviousCell(row, col);
    render();
  } else if (key === 'Enter') {
    event.preventDefault();
    toggleDirection();
    render();
  }
}

/* ---------------- Hints ---------------- */

function revealLetterHint() {
  const wordIndex = activeWordIndex();
  if (wordIndex === null) return;
  const cells = wordCells(session.puzzle.words, wordIndex);
  const target = cells.find(({ row, col }) => (
    session.entries[row][col].toUpperCase() !== session.grid.cells[row][col].answer
  ));
  if (!target) return;
  const { row, col } = target;
  session.entries[row][col] = session.grid.cells[row][col].answer;
  session.revealed[row][col] = true;
  session.results[row][col] = 'correct';
  session.hintsUsed += 1;
  session.selected = { row, col };
  render();
  maybeAutoCheck();
}

function revealWordHint() {
  const wordIndex = activeWordIndex();
  if (wordIndex === null) return;
  const cells = wordCells(session.puzzle.words, wordIndex);
  const alreadyCorrect = cells.every(({ row, col }) => (
    session.entries[row][col].toUpperCase() === session.grid.cells[row][col].answer
  ));
  if (alreadyCorrect) return;
  for (const { row, col } of cells) {
    session.entries[row][col] = session.grid.cells[row][col].answer;
    session.revealed[row][col] = true;
    session.results[row][col] = 'correct';
  }
  session.hintsUsed += 1;
  render();
  maybeAutoCheck();
}

/* ---------------- Checking & completion ---------------- */

function maybeAutoCheck() {
  const { grid, entries } = session;
  for (let r = 0; r < grid.rows; r += 1) {
    for (let c = 0; c < grid.cols; c += 1) {
      if (grid.cells[r][c] && !entries[r][c]) return;
    }
  }
  checkAnswers();
}

function checkAnswers() {
  const { grid, entries } = session;
  let allCorrect = true;
  let anyFilled = false;
  for (let r = 0; r < grid.rows; r += 1) {
    for (let c = 0; c < grid.cols; c += 1) {
      const cell = grid.cells[r][c];
      if (!cell) continue;
      const entry = entries[r][c];
      if (!entry) { allCorrect = false; continue; }
      anyFilled = true;
      const correct = entry.toUpperCase() === cell.answer;
      session.results[r][c] = correct ? 'correct' : 'incorrect';
      if (!correct) allCorrect = false;
    }
  }
  if (allCorrect && anyFilled) {
    completePuzzle();
  } else {
    render();
    const status = document.getElementById('puzzle-status');
    status.textContent = 'Kin checked your answers — take another look at the highlighted letters. Hints are always there if you want one.';
    status.hidden = false;
  }
}

function completePuzzle() {
  session.solved = true;
  recordCompletion(session.puzzle.id, session.difficulty);
  render();
  renderProgressTile();
  renderBanner();
  const complete = document.getElementById('puzzle-complete');
  complete.textContent = session.hintsUsed > 0
    ? `🎉 Wonderful, you solved it! You used ${session.hintsUsed} hint${session.hintsUsed === 1 ? '' : 's'} along the way — that's exactly what they're for.`
    : '🎉 Wonderful, you solved it — no hints needed!';
  complete.hidden = false;
  document.getElementById('puzzle-status').hidden = true;
}

/* ---------------- Rendering ---------------- */

function render() {
  renderGrid();
  renderClues();
  focusSelectedCell();
}

function renderGrid() {
  const { grid, selected } = session;
  const container = document.getElementById('crossword-grid');
  container.style.setProperty('--cw-cols', grid.cols);
  container.replaceChildren();

  const activeWord = activeWordIndex();
  const activeCells = activeWord !== null ? wordCells(session.puzzle.words, activeWord) : [];

  for (let r = 0; r < grid.rows; r += 1) {
    for (let c = 0; c < grid.cols; c += 1) {
      const cell = grid.cells[r][c];
      if (!cell) {
        const block = document.createElement('div');
        block.className = 'cw-cell cw-cell-block';
        container.appendChild(block);
        continue;
      }

      const wrap = document.createElement('div');
      wrap.className = 'cw-cell';
      const isSelected = selected && selected.row === r && selected.col === c;
      if (isSelected) {
        wrap.classList.add('cw-cell-selected');
      } else if (activeCells.some((p) => p.row === r && p.col === c)) {
        wrap.classList.add('cw-cell-active-word');
      }

      if (cell.number) {
        const num = document.createElement('span');
        num.className = 'cw-number';
        num.textContent = String(cell.number);
        wrap.appendChild(num);
      }

      const input = document.createElement('input');
      input.className = 'cw-input';
      input.maxLength = 1;
      input.autocomplete = 'off';
      input.inputMode = 'text';
      input.value = session.entries[r][c];
      input.dataset.row = String(r);
      input.dataset.col = String(c);
      input.setAttribute('aria-label', `Row ${r + 1}, column ${c + 1}`);
      if (session.revealed[r][c]) input.classList.add('cw-revealed');
      if (session.results[r][c] === 'correct') input.classList.add('cw-correct');
      if (session.results[r][c] === 'incorrect') input.classList.add('cw-incorrect');

      input.addEventListener('click', () => selectCell(r, c));
      input.addEventListener('input', (event) => handleCellInput(r, c, event));
      input.addEventListener('keydown', (event) => handleCellKeydown(r, c, event));

      wrap.appendChild(input);
      container.appendChild(wrap);
    }
  }
}

function renderClues() {
  const activeWord = activeWordIndex();
  const acrossList = document.getElementById('clues-across');
  const downList = document.getElementById('clues-down');
  acrossList.replaceChildren();
  downList.replaceChildren();

  const withNumbers = session.puzzle.words.map((word, index) => ({
    word, index, number: session.grid.cells[word.row][word.col].number,
  }));
  withNumbers.sort((a, b) => a.number - b.number);

  for (const entry of withNumbers) {
    const li = document.createElement('li');
    li.className = 'clue-item';
    if (entry.index === activeWord) li.classList.add('active');
    li.textContent = `${entry.number}. ${entry.word.clue}`;
    li.addEventListener('click', () => selectWord(entry.index));
    (entry.word.direction === 'across' ? acrossList : downList).appendChild(li);
  }
}

function focusSelectedCell() {
  if (!session.selected) return;
  const { row, col } = session.selected;
  const target = document.querySelector(`#crossword-grid .cw-input[data-row="${row}"][data-col="${col}"]`);
  target?.focus();
}

/* ---------------- Boot ---------------- */

function setupGamesControls() {
  for (const card of document.querySelectorAll('.difficulty-card')) {
    card.addEventListener('click', () => playRandomPuzzle(card.dataset.difficulty));
  }
  document.getElementById('surprise-me').addEventListener('click', () => {
    playRandomPuzzle(weightedPick(currentPlan.weights));
  });
  document.getElementById('puzzle-back').addEventListener('click', showPicker);
  document.getElementById('hint-letter').addEventListener('click', revealLetterHint);
  document.getElementById('hint-word').addEventListener('click', revealWordHint);
  document.getElementById('check-answers').addEventListener('click', checkAnswers);
  document.getElementById('new-puzzle').addEventListener('click', () => playRandomPuzzle(session.difficulty));
}

setupGamesControls();
