// Registry (admin back-office) logic
//
// Wrapped in an IIFE so unlock()/tryUnlock() and the rest of this file's
// functions aren't attached to `window` — calling e.g. `unlock()` from the
// browser console on the live site no longer bypasses the password gate.
(function () {

function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

const GATE_KEY = 'lma_registry_unlocked';

const AGE_BANDS = [
  { label: 'Under 12', test: a => a < 12 },
  { label: '12-17', test: a => a >= 12 && a <= 17 },
  { label: '18-25', test: a => a >= 18 && a <= 25 },
  { label: '26-35', test: a => a >= 26 && a <= 35 },
  { label: '36-45', test: a => a >= 36 && a <= 45 },
  { label: '46+', test: a => a >= 46 },
];

const SENIORITY_BANDS = [
  { label: '< 6 months', test: m => m < 6 },
  { label: '6-12 months', test: m => m >= 6 && m < 12 },
  { label: '1-2 years', test: m => m >= 12 && m < 24 },
  { label: '2-5 years', test: m => m >= 24 && m < 60 },
  { label: '5+ years', test: m => m >= 60 },
];

const CHART_COLORS = ['#e30613', '#1565c0', '#2e9e4b', '#e6b800', '#7b3fa0', '#e8791a', '#6b4226', '#111113', '#9a9a9a'];

let charts = {};

function ageFromDob(dob) {
  if (!dob) return null;
  const birth = new Date(dob);
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const m = today.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--;
  return age;
}

function seniorityInMonths(member) {
  return (member.trainingYears || 0) * 12 + (member.trainingMonths || 0);
}

function bandFor(bands, value) {
  const b = bands.find(b => b.test(value));
  return b ? b.label : 'Unknown';
}

function monthKey(dateStr) {
  return dateStr ? dateStr.slice(0, 7) : ''; // YYYY-MM
}

// Local-timezone "today" as yyyy-MM-dd. Deliberately NOT `new
// Date().toISOString()`, which is always UTC and would misattribute
// entries/months made in the first few hours of the local day for any
// club east of UTC.
function todayIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

// ISO week key: YYYY-Www
function weekKey(dateStr) {
  const d = new Date(dateStr + 'T00:00:00');
  const target = new Date(d.valueOf());
  const dayNr = (d.getDay() + 6) % 7;
  target.setDate(target.getDate() - dayNr + 3);
  const firstThursday = new Date(target.getFullYear(), 0, 4);
  const diff = target - firstThursday;
  const week = 1 + Math.round(diff / (7 * 24 * 3600 * 1000));
  return `${target.getFullYear()}-W${String(week).padStart(2, '0')}`;
}

function destroyChart(key) {
  if (charts[key]) { charts[key].destroy(); delete charts[key]; }
}

function renderBarChart(canvasId, labels, data, label) {
  destroyChart(canvasId);
  const ctx = document.getElementById(canvasId).getContext('2d');
  charts[canvasId] = new Chart(ctx, {
    type: 'bar',
    data: { labels, datasets: [{ label, data, backgroundColor: CHART_COLORS }] },
    options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } },
  });
}

// Grade chart: colored squares instead of "White Belt" / "Red Belt" text —
// the bar (and the square under it) already carries the belt color.
function renderGradeChart(canvasId, members) {
  destroyChart(canvasId);
  const gradeCounts = {};
  GRADES.forEach(g => gradeCounts[g.key] = 0);
  members.forEach(m => { gradeCounts[m.grade] = (gradeCounts[m.grade] || 0) + 1; });
  const keys = GRADES.map(g => g.key);
  const data = keys.map(k => gradeCounts[k] || 0);
  const colors = keys.map(k => GRADE_COLORS[k]);

  const ctx = document.getElementById(canvasId).getContext('2d');
  charts[canvasId] = new Chart(ctx, {
    type: 'bar',
    data: { labels: keys.map(() => ''), datasets: [{ data, backgroundColor: colors }] },
    options: {
      responsive: true, maintainAspectRatio: false,
      layout: { padding: { bottom: 22 } },
      plugins: { legend: { display: false } },
      scales: { x: { ticks: { display: false } }, y: { beginAtZero: true } },
    },
    plugins: [{
      id: 'gradeColorSquares',
      afterDraw(chart) {
        const meta = chart.getDatasetMeta(0);
        const bottom = chart.scales.x.bottom;
        meta.data.forEach((bar, i) => {
          chart.ctx.fillStyle = colors[i];
          chart.ctx.fillRect(bar.x - 7, bottom + 8, 14, 14);
        });
      },
    }],
  });
}

function renderPieChart(canvasId, labels, data) {
  destroyChart(canvasId);
  const ctx = document.getElementById(canvasId).getContext('2d');
  charts[canvasId] = new Chart(ctx, {
    type: 'doughnut',
    data: { labels, datasets: [{ data, backgroundColor: CHART_COLORS }] },
    options: { responsive: true, maintainAspectRatio: false },
  });
}

function renderLineChart(canvasId, labels, datasets) {
  destroyChart(canvasId);
  const ctx = document.getElementById(canvasId).getContext('2d');
  charts[canvasId] = new Chart(ctx, {
    type: 'line',
    data: { labels, datasets },
    options: { responsive: true, maintainAspectRatio: false, tension: 0.3 },
  });
}

function countBy(items, keyFn) {
  const map = {};
  items.forEach(it => {
    const k = keyFn(it);
    map[k] = (map[k] || 0) + 1;
  });
  return map;
}

// ---------- DASHBOARD ----------

let lastDashboardMembers = [];
let lastDashboardRevenue = [];

async function renderDashboard() {
  const [members, revenue] = await Promise.all([getMembers(), getRevenueEntries()]);
  lastDashboardMembers = members;
  lastDashboardRevenue = revenue;

  const thisMonth = monthKey(todayIso());
  const thisWeek = weekKey(todayIso());
  const monthRevenue = revenue.filter(r => monthKey(r.date) === thisMonth).reduce((s, r) => s + Number(r.amount || 0), 0);
  const weekRevenue = revenue.filter(r => weekKey(r.date) === thisWeek).reduce((s, r) => s + Number(r.amount || 0), 0);
  const totalRevenue = revenue.reduce((s, r) => s + Number(r.amount || 0), 0);

  const monthsWithRevenue = new Set(revenue.map(r => monthKey(r.date)).filter(Boolean));
  const weeksWithRevenue = new Set(revenue.map(r => weekKey(r.date)).filter(Boolean));
  const avgMonthlyRevenue = monthsWithRevenue.size ? totalRevenue / monthsWithRevenue.size : 0;
  const avgWeeklyRevenue = weeksWithRevenue.size ? totalRevenue / weeksWithRevenue.size : 0;

  const tiles = [
    { num: members.length, label: 'Total Students', action: 'students' },
    { num: monthRevenue.toFixed(0), label: 'Revenue This Month', action: 'revenueMonth' },
    { num: weekRevenue.toFixed(0), label: 'Revenue This Week', action: 'revenueWeek' },
    { num: totalRevenue.toFixed(0), label: 'Total Revenue', action: 'revenueTotal' },
    { num: revenue.length, label: 'Sessions Logged', action: 'sessions' },
    { num: avgMonthlyRevenue.toFixed(0), label: 'Avg Monthly Revenue' },
    { num: avgWeeklyRevenue.toFixed(0), label: 'Avg Weekly Revenue' },
  ];
  document.getElementById('statTiles').innerHTML = tiles.map(t =>
    `<div class="stat-tile${t.action ? ' tappable' : ''}" ${t.action ? `data-action="${t.action}"` : ''}>
      <div class="num">${t.num}</div><div class="label">${t.label}</div>
    </div>`
  ).join('');
  document.querySelectorAll('#statTiles .stat-tile[data-action]').forEach(tile => {
    tile.addEventListener('click', () => showStatDetail(tile.dataset.action));
  });
  document.getElementById('statDetail').style.display = 'none';

  // Monthly revenue evolution (chronological)
  const monthMap = {};
  revenue.forEach(r => {
    const k = monthKey(r.date);
    if (!k) return;
    monthMap[k] = (monthMap[k] || 0) + Number(r.amount || 0);
  });
  const months = Object.keys(monthMap).sort();
  renderLineChart('chartMonthlyRevenue', months, [{
    label: 'Revenue', data: months.map(m => monthMap[m]),
    borderColor: '#e30613', backgroundColor: 'rgba(227,6,19,0.12)', fill: true,
  }]);

  // Weekly revenue evolution (chronological)
  const weekMap = {};
  revenue.forEach(r => {
    const k = weekKey(r.date);
    if (!k) return;
    weekMap[k] = (weekMap[k] || 0) + Number(r.amount || 0);
  });
  const weeks = Object.keys(weekMap).sort();
  renderLineChart('chartWeeklyRevenue', weeks, [{
    label: 'Revenue', data: weeks.map(w => weekMap[w]),
    borderColor: '#2e9e4b', backgroundColor: 'rgba(46,158,75,0.12)', fill: true,
  }]);

  renderBreakdownCharts(members);
}

// Age/Seniority/Payment/Grade charts, optionally sliced to one class via the
// "Breakdown for" filter. Coaches don't have a class/payment-type/seniority,
// so they're excluded from those two charts (but still counted in Age/Grade).
function renderBreakdownCharts(members) {
  const classFilter = document.getElementById('dashboardClassFilter').value;
  const inClass = classFilter ? members.filter(m => m.class === classFilter) : members;
  const nonCoach = inClass.filter(m => m.role !== 'coach');

  const ageCounts = {};
  AGE_BANDS.forEach(b => ageCounts[b.label] = 0);
  inClass.forEach(m => {
    const age = ageFromDob(m.dob);
    if (age === null) return;
    ageCounts[bandFor(AGE_BANDS, age)] = (ageCounts[bandFor(AGE_BANDS, age)] || 0) + 1;
  });
  renderBarChart('chartAge', Object.keys(ageCounts), Object.values(ageCounts), 'Students');

  const senCounts = {};
  SENIORITY_BANDS.forEach(b => senCounts[b.label] = 0);
  nonCoach.forEach(m => {
    const months_ = seniorityInMonths(m);
    senCounts[bandFor(SENIORITY_BANDS, months_)] = (senCounts[bandFor(SENIORITY_BANDS, months_)] || 0) + 1;
  });
  renderBarChart('chartSeniority', Object.keys(senCounts), Object.values(senCounts), 'Students');

  const payCounts = countBy(nonCoach, m => paymentLabel(m.paymentType));
  renderPieChart('chartPayment', Object.keys(payCounts), Object.values(payCounts));

  renderGradeChart('chartGrade', inClass);
}

function renderEntriesTable(entries) {
  if (!entries.length) return `<p class="hint">No entries.</p>`;
  const total = entries.reduce((s, e) => s + Number(e.amount || 0), 0);
  return `
    <p class="hint">${entries.length} session(s) · total ${total.toFixed(2)}</p>
    <div style="overflow-x:auto"><table>
      <thead><tr><th>Date</th><th>Session</th><th>Amount</th></tr></thead>
      <tbody>${entries.map(e => `<tr><td>${e.date}</td><td>${scheduleLabel(e.classKey)}</td><td>${Number(e.amount).toFixed(2)}</td></tr>`).join('')}</tbody>
    </table></div>
  `;
}

function showStatDetail(action) {
  const members = lastDashboardMembers;
  const revenue = lastDashboardRevenue;
  let title = '';
  let body = '';

  if (action === 'students') {
    const byRole = countBy(members, m => roleLabel(m.role));
    const byClass = countBy(members.filter(m => m.role !== 'coach'), m => classLabel(m.class));
    title = 'Total Students';
    body = `
      <p class="hint">By status</p>
      <div class="pill-row">${Object.entries(byRole).map(([k, v]) => `<span class="badge" style="background:#eceef1;color:#444">${k}: ${v}</span>`).join('')}</div>
      <p class="hint" style="margin-top:14px">By class (students &amp; assistants)</p>
      <div class="pill-row">${Object.entries(byClass).map(([k, v]) => `<span class="badge" style="background:#eceef1;color:#444">${k}: ${v}</span>`).join('')}</div>
    `;
  } else if (action === 'revenueMonth' || action === 'revenueWeek') {
    const key = action === 'revenueMonth' ? monthKey(todayIso()) : weekKey(todayIso());
    const keyFn = action === 'revenueMonth' ? (r => monthKey(r.date)) : (r => weekKey(r.date));
    const entries = revenue.filter(r => keyFn(r) === key).sort((a, b) => (a.date < b.date ? 1 : -1));
    title = action === 'revenueMonth' ? 'Revenue This Month' : 'Revenue This Week';
    body = renderEntriesTable(entries);
  } else if (action === 'revenueTotal') {
    const byMonth = {};
    revenue.forEach(r => {
      const k = monthKey(r.date);
      if (!k) return;
      byMonth[k] = (byMonth[k] || 0) + Number(r.amount || 0);
    });
    const monthsSorted = Object.keys(byMonth).sort().reverse();
    const total = revenue.reduce((s, r) => s + Number(r.amount || 0), 0);
    title = 'Total Revenue';
    body = `
      <p class="hint">${revenue.length} session(s) · total ${total.toFixed(2)}</p>
      <table><thead><tr><th>Month</th><th>Revenue</th></tr></thead><tbody>
        ${monthsSorted.map(m => `<tr><td>${m}</td><td>${byMonth[m].toFixed(2)}</td></tr>`).join('')}
      </tbody></table>
    `;
  } else if (action === 'sessions') {
    const entries = [...revenue].sort((a, b) => (a.date < b.date ? 1 : -1));
    title = 'Sessions Logged';
    body = renderEntriesTable(entries);
  }

  const panel = document.getElementById('statDetail');
  panel.innerHTML = `<h2>${title}</h2>${body}<div class="actions"><button class="ghost" id="closeStatDetailBtn">Close</button></div>`;
  panel.style.display = '';
  document.getElementById('closeStatDetailBtn').addEventListener('click', () => { panel.style.display = 'none'; });
}

// ---------- STUDENTS ----------

function renderDashboardClassFilter() {
  document.getElementById('dashboardClassFilter').innerHTML = '<option value="">All classes</option>' +
    CLASSES.map(c => `<option value="${c.key}">${c.label}</option>`).join('');
}

function renderStudentsFilters() {
  document.getElementById('filterRole').innerHTML = '<option value="">All statuses</option>' +
    ROLES.map(r => `<option value="${r.key}">${r.label}</option>`).join('');
  document.getElementById('filterClass').innerHTML = '<option value="">All classes</option>' +
    CLASSES.map(c => `<option value="${c.key}">${c.label}</option>`).join('');
  document.getElementById('filterPayment').innerHTML = '<option value="">All payment types</option>' +
    PAYMENT_TYPES.map(p => `<option value="${p.key}">${p.label}</option>`).join('');
  document.getElementById('filterGrade').innerHTML = '<option value="">All grades</option>' +
    GRADES.map(g => `<option value="${g.key}">${g.label}</option>`).join('');
}

function seniorityText(m) {
  return `${m.trainingYears || 0}y ${m.trainingMonths || 0}m`;
}

async function renderStudentsTable() {
  const search = document.getElementById('studentSearch').value.trim().toLowerCase();
  const fRole = document.getElementById('filterRole').value;
  const fClass = document.getElementById('filterClass').value;
  const fPayment = document.getElementById('filterPayment').value;
  const fGrade = document.getElementById('filterGrade').value;

  let members = await getMembers();
  if (search) {
    members = members.filter(m =>
      `${m.firstName} ${m.lastName}`.toLowerCase().includes(search) ||
      `${m.phonePrefix}${m.phoneNumber}`.includes(search)
    );
  }
  if (fRole) members = members.filter(m => (m.role || 'student') === fRole);
  if (fClass) members = members.filter(m => m.class === fClass);
  if (fPayment) members = members.filter(m => m.paymentType === fPayment);
  if (fGrade) members = members.filter(m => m.grade === fGrade);

  const tbody = document.querySelector('#studentsTable tbody');
  tbody.innerHTML = members.map(m => `
    <tr data-id="${m.id}">
      <td>${escapeHtml(m.firstName)} ${escapeHtml(m.lastName)}</td>
      <td>${m.phonePrefix} ${escapeHtml(m.phoneNumber)}</td>
      <td>${roleLabel(m.role)}</td>
      <td>${ageFromDob(m.dob) ?? '-'}</td>
      <td>${m.role === 'coach' ? '-' : classLabel(m.class)}</td>
      <td>${m.role === 'coach' ? '-' : paymentLabel(m.paymentType)}</td>
      <td>${m.role === 'coach' ? '-' : seniorityText(m)}</td>
      <td>
        <select class="gradeSelect badge ${m.grade || 'white'}" data-id="${m.id}">
          ${GRADES.map(g => `<option value="${g.key}" ${g.key === m.grade ? 'selected' : ''}>${g.label}</option>`).join('')}
        </select>
      </td>
      <td>
        <button class="ghost viewBtn" data-id="${m.id}">View</button>
        <button class="ghost deleteStudentBtn" data-id="${m.id}" style="color:var(--red)">Delete</button>
      </td>
    </tr>
  `).join('') || `<tr><td colspan="9" class="hint">No students found.</td></tr>`;

  tbody.querySelectorAll('.gradeSelect').forEach(sel => {
    sel.addEventListener('change', async () => {
      // Recolor immediately so the box reflects the new belt right away,
      // instead of waiting for the save + re-render round trip.
      GRADES.forEach(g => sel.classList.remove(g.key));
      sel.classList.add(sel.value);
      try {
        const member = await getMemberById(sel.dataset.id);
        if (!member) return;
        member.grade = sel.value;
        await upsertMember(member);
        await Promise.all([renderDashboard(), renderStudentsTable()]);
      } catch (err) {
        alert(err.message);
      }
    });
  });

  tbody.querySelectorAll('.viewBtn').forEach(btn => {
    btn.addEventListener('click', () => renderStudentDetail(btn.dataset.id));
  });

  tbody.querySelectorAll('.deleteStudentBtn').forEach(btn => {
    btn.addEventListener('click', async () => {
      try {
        const member = await getMemberById(btn.dataset.id);
        if (!member) return;
        const name = `${member.firstName} ${member.lastName}`;
        if (!confirm(`Delete the profile for ${name}? This cannot be undone.`)) return;
        await deleteMember(member.id);
        document.getElementById('studentDetail').style.display = 'none';
        await Promise.all([renderStudentsTable(), renderDashboard()]);
      } catch (err) {
        alert(err.message);
      }
    });
  });
}

async function renderStudentDetail(id) {
  const m = await getMemberById(id);
  const panel = document.getElementById('studentDetail');
  if (!m) { panel.style.display = 'none'; return; }
  panel.style.display = '';
  panel.innerHTML = `
    <div class="profile-header">
      <img src="${m.selfie || PLACEHOLDER_AVATAR}" alt="" style="width:64px;height:64px;border-radius:50%;object-fit:cover;border:1px solid var(--line)" />
      <div>
        <div class="name">${escapeHtml(m.firstName)} ${escapeHtml(m.lastName)}</div>
        <div class="meta">${m.phonePrefix} ${escapeHtml(m.phoneNumber)} · ${m.gender === 'female' ? 'Female' : 'Male'} · Born ${m.dob ? m.dob.split('-').reverse().join('/') : '-'}</div>
      </div>
      <div class="spacer"></div>
      <span class="badge ${m.grade || 'white'}">${gradeLabel(m.grade)}</span>
    </div>
    <div class="grid" style="margin-top:14px">
      <div><strong>Status:</strong> ${roleLabel(m.role)}</div>
      <div><strong>WhatsApp:</strong> ${escapeHtml(m.whatsapp) || '-'}</div>
      <div><strong>Emergency contact:</strong> ${escapeHtml(m.emergencyName) || '-'} (${escapeHtml(m.emergencyPhone) || '-'})</div>
      ${m.role === 'coach' ? '' : `
      <div><strong>Class:</strong> ${classLabel(m.class)}</div>
      <div><strong>Payment type:</strong> ${paymentLabel(m.paymentType)}</div>
      <div><strong>Training experience:</strong> ${seniorityText(m)}</div>`}
      <div><strong>Member since:</strong> ${m.createdAt ? new Date(m.createdAt).toLocaleDateString() : '-'}</div>
    </div>
    <div class="actions"><button class="ghost" id="closeDetailBtn">Close</button></div>
  `;
  document.getElementById('closeDetailBtn').addEventListener('click', () => { panel.style.display = 'none'; });
}

// ---------- REVENUE ENTRY ----------

// The entry currently loaded into the form, if the chosen date & class
// already has one logged — editing it (e.g. ticking a late arrival) then
// corrects that same row instead of creating a duplicate. null means the
// form is building a brand new entry.
let currentRevenueEntry = null;

function renderRevenueClassOptions() {
  document.getElementById('revClass').innerHTML = SCHEDULE.map(s =>
    `<option value="${s.key}">${s.day} ${s.time} — ${s.label}</option>`
  ).join('');
  document.getElementById('trendClass').innerHTML = SCHEDULE.map(s =>
    `<option value="${s.key}">${s.day} ${s.time} — ${s.label}</option>`
  ).join('');
}

function scheduleLabel(key) {
  const s = SCHEDULE.find(s => s.key === key);
  return s ? `${s.day} ${s.time} — ${s.label}` : key;
}

function classCategoryForScheduleKey(key) {
  const s = SCHEDULE.find(s => s.key === key);
  return s ? s.classKey : '';
}

function updateAttendanceCount() {
  const count = document.querySelectorAll('#attendanceList input[type=checkbox]:checked').length;
  document.getElementById('revCount').value = count;
}

// Students eligible for the selected class (coaches don't attend as
// students, so they're excluded), with whoever is already marked present
// pre-ticked.
async function renderAttendanceList(classCategory, presentIds) {
  const list = document.getElementById('attendanceList');
  const members = (await getMembers())
    .filter(m => m.role !== 'coach' && m.class === classCategory)
    .sort((a, b) => `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`));
  list.innerHTML = members.map(m => `
    <label>
      <input type="checkbox" value="${m.id}" data-name="${escapeHtml(`${m.firstName} ${m.lastName}`.toLowerCase())}" ${presentIds.includes(m.id) ? 'checked' : ''} />
      ${escapeHtml(m.firstName)} ${escapeHtml(m.lastName)}
    </label>
  `).join('') || `<p class="hint">No students in this class yet.</p>`;
  list.querySelectorAll('input[type=checkbox]').forEach(cb => cb.addEventListener('change', updateAttendanceCount));
  updateAttendanceCount();
}

// Looks up whether the chosen date & class already has a logged entry and
// loads it into the form for editing (amount, note, who was ticked present)
// instead of starting blank — this is what makes the log editable at any
// time, e.g. to tick a student who arrived late.
async function loadRevenueEntryForDateClass() {
  const date = document.getElementById('revDate').value;
  const classKey = document.getElementById('revClass').value;
  if (!date || !classKey) return;
  const classCategory = classCategoryForScheduleKey(classKey);
  let existing = null;
  try {
    existing = await getRevenueEntryByDateAndClass(date, classKey);
  } catch (err) {
    alert(err.message);
  }
  currentRevenueEntry = existing;
  document.getElementById('revAmount').value = existing ? existing.amount : '';
  document.getElementById('revNote').value = existing ? existing.note : '';
  await renderAttendanceList(classCategory, existing ? existing.attendeeIds : []);
  document.getElementById('revenueSubmitBtn').textContent = existing ? 'Update entry' : 'Save entry';
  document.getElementById('revenueFormHint').textContent = existing
    ? 'Editing the entry already logged for this date & class — tick a late arrival or fix the amount any time.'
    : "After each class, tick who showed up and enter how much money you collected. Do this once per class, right after it ends, so weekly trends stay accurate.";
}

async function renderRevenueTable() {
  const entries = [...(await getRevenueEntries())].sort((a, b) => (a.date < b.date ? 1 : -1));
  const tbody = document.querySelector('#revenueTable tbody');
  tbody.innerHTML = entries.map(e => `
    <tr>
      <td>${e.date}</td>
      <td>${scheduleLabel(e.classKey)}</td>
      <td>${e.studentCount ?? '-'}</td>
      <td>${Number(e.amount).toFixed(2)}</td>
      <td>${escapeHtml(e.note)}</td>
      <td>
        <button class="ghost editRevBtn" data-date="${e.date}" data-class="${e.classKey}">Edit</button>
        <button class="ghost delRevBtn" data-id="${e.id}">Delete</button>
      </td>
    </tr>
  `).join('') || `<tr><td colspan="6" class="hint">No entries yet.</td></tr>`;

  tbody.querySelectorAll('.editRevBtn').forEach(btn => {
    btn.addEventListener('click', async () => {
      document.getElementById('revDate').value = btn.dataset.date;
      document.getElementById('revClass').value = btn.dataset.class;
      await loadRevenueEntryForDateClass();
      document.getElementById('revenueForm').scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });

  tbody.querySelectorAll('.delRevBtn').forEach(btn => {
    btn.addEventListener('click', async () => {
      try {
        await deleteRevenueEntry(btn.dataset.id);
        await Promise.all([renderRevenueTable(), renderDashboard(), renderTrends()]);
        // In case the deleted row was the one loaded into the form, drop
        // back to "new entry" mode instead of still offering to "update" it.
        await loadRevenueEntryForDateClass();
      } catch (err) {
        alert(err.message);
      }
    });
  });
}

// ---------- SCHEDULE & TRENDS ----------

function renderScheduleTable() {
  document.getElementById('scheduleBody').innerHTML = SCHEDULE.map(s =>
    `<tr><td>${s.day}</td><td>${s.time}</td><td>${s.label}</td></tr>`
  ).join('');
}

async function renderTrends() {
  const classKey = document.getElementById('trendClass').value;
  const entries = (await getRevenueEntries())
    .filter(e => e.classKey === classKey)
    .sort((a, b) => (a.date < b.date ? -1 : 1));

  const weekMap = {}; // weekKey -> { students, revenue }
  entries.forEach(e => {
    const wk = weekKey(e.date);
    if (!weekMap[wk]) weekMap[wk] = { students: 0, revenue: 0, count: 0 };
    weekMap[wk].students += Number(e.studentCount || 0);
    weekMap[wk].revenue += Number(e.amount || 0);
    weekMap[wk].count += 1;
  });
  const weeks = Object.keys(weekMap).sort();

  renderLineChart('chartClassStudents', weeks, [{
    label: 'Students', data: weeks.map(w => weekMap[w].students),
    borderColor: '#1565c0', backgroundColor: 'rgba(21,101,192,0.12)', fill: true,
  }]);
  renderLineChart('chartClassRevenue', weeks, [{
    label: 'Revenue', data: weeks.map(w => weekMap[w].revenue),
    borderColor: '#2e9e4b', backgroundColor: 'rgba(46,158,75,0.12)', fill: true,
  }]);
}

// ---------- TABS ----------

function switchToTab(tabName) {
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tabName));
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.toggle('active', p.id === `panel-${tabName}`));
}

function initTabs() {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => switchToTab(btn.dataset.tab));
  });
  document.getElementById('goToRevenueBtn').addEventListener('click', () => switchToTab('revenue'));
}

// ---------- GATE ----------

function unlock() {
  sessionStorage.setItem(GATE_KEY, '1');
  document.getElementById('gateScreen').style.display = 'none';
  document.getElementById('registryContent').style.display = '';
  initRegistry();
}

function initGate() {
  if (sessionStorage.getItem(GATE_KEY) === '1') { unlock(); return; }
  document.getElementById('gateBtn').addEventListener('click', () => tryUnlock());
  document.getElementById('gatePassword').addEventListener('keydown', e => {
    if (e.key === 'Enter') tryUnlock();
  });
}

async function tryUnlock() {
  const pwd = document.getElementById('gatePassword').value;
  const hash = await sha256Hex(pwd);
  if (hash === REGISTRY_PASSWORD_HASH) {
    unlock();
  } else {
    document.getElementById('gateError').innerHTML = '<div class="alert error">Incorrect password.</div>';
  }
}

// ---------- INIT ----------

// Pre-selects the class/session dropdown to match whatever is scheduled on
// the chosen day (e.g. picking a Wednesday selects Girls Only, the first
// Wednesday session) — the coach can still change it if there's more than
// one session that day.
function autoSelectClassForDate(dateStr) {
  if (!dateStr) return;
  const dayIndex = new Date(`${dateStr}T00:00:00`).getDay();
  const match = SCHEDULE.find(s => s.dayIndex === dayIndex);
  if (match) document.getElementById('revClass').value = match.key;
}

async function initRegistry() {
  initTabs();
  renderStudentsFilters();
  renderDashboardClassFilter();
  renderRevenueClassOptions();
  renderScheduleTable();

  document.getElementById('revDate').value = todayIso();
  autoSelectClassForDate(todayIso());
  document.getElementById('revDate').addEventListener('change', (e) => {
    autoSelectClassForDate(e.target.value);
    loadRevenueEntryForDateClass().catch(err => alert(err.message));
  });
  document.getElementById('revClass').addEventListener('change', () => {
    loadRevenueEntryForDateClass().catch(err => alert(err.message));
  });
  document.getElementById('attendanceSearch').addEventListener('keydown', (e) => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    const q = e.target.value.trim().toLowerCase();
    if (!q) return;
    const checkboxes = Array.from(document.querySelectorAll('#attendanceList input[type=checkbox]'));
    const match = checkboxes.find(cb => !cb.checked && cb.dataset.name.includes(q));
    if (match) {
      match.checked = true;
      updateAttendanceCount();
      match.scrollIntoView({ block: 'nearest' });
    }
    e.target.value = '';
  });

  try {
    await Promise.all([renderDashboard(), renderStudentsTable(), renderRevenueTable(), renderTrends(), loadRevenueEntryForDateClass()]);
  } catch (err) {
    alert(err.message);
  }

  const safe = fn => () => fn().catch(err => alert(err.message));

  ['filterRole', 'filterClass', 'filterPayment', 'filterGrade'].forEach(id => {
    document.getElementById(id).addEventListener('change', safe(renderStudentsTable));
  });
  // Debounced so typing a name doesn't re-fetch and rebuild the whole table
  // on every single keystroke.
  document.getElementById('studentSearch').addEventListener('input', debounce(safe(renderStudentsTable), 200));

  document.getElementById('trendClass').addEventListener('change', safe(renderTrends));

  document.getElementById('dashboardClassFilter').addEventListener('change', () => renderBreakdownCharts(lastDashboardMembers));

  document.getElementById('revenueForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const submitBtn = e.target.querySelector('button[type=submit]');
    submitBtn.disabled = true;
    try {
      const attendeeIds = Array.from(document.querySelectorAll('#attendanceList input[type=checkbox]:checked')).map(cb => cb.value);
      await upsertRevenueEntry({
        id: currentRevenueEntry ? currentRevenueEntry.id : undefined,
        createdAt: currentRevenueEntry ? currentRevenueEntry.createdAt : undefined,
        date: document.getElementById('revDate').value,
        classKey: document.getElementById('revClass').value,
        amount: parseFloat(document.getElementById('revAmount').value) || 0,
        studentCount: attendeeIds.length,
        attendeeIds,
        note: document.getElementById('revNote').value.trim(),
      });
      await Promise.all([renderRevenueTable(), renderDashboard(), renderTrends()]);
      // Reload rather than reset: the form stays on this same date & class
      // in "edit" mode so a correction (e.g. a late arrival) can be made
      // right away without hunting for the entry again.
      await loadRevenueEntryForDateClass();
    } catch (err) {
      alert(err.message);
    } finally {
      submitBtn.disabled = false;
    }
  });
}

document.addEventListener('DOMContentLoaded', initGate);

})();
