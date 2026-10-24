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

async function renderDashboard() {
  const [members, revenue] = await Promise.all([getMembers(), getRevenueEntries()]);

  const thisMonth = monthKey(todayIso());
  const monthRevenue = revenue.filter(r => monthKey(r.date) === thisMonth).reduce((s, r) => s + Number(r.amount || 0), 0);
  const totalRevenue = revenue.reduce((s, r) => s + Number(r.amount || 0), 0);

  document.getElementById('statTiles').innerHTML = [
    { num: members.length, label: 'Total Students' },
    { num: `${monthRevenue.toFixed(0)}`, label: 'Revenue This Month' },
    { num: `${totalRevenue.toFixed(0)}`, label: 'Total Revenue' },
    { num: revenue.length, label: 'Sessions Logged' },
  ].map(t => `<div class="stat-tile"><div class="num">${t.num}</div><div class="label">${t.label}</div></div>`).join('');

  // Monthly revenue evolution (last 12 months, chronological)
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

  // Age distribution
  const ageCounts = {};
  AGE_BANDS.forEach(b => ageCounts[b.label] = 0);
  members.forEach(m => {
    const age = ageFromDob(m.dob);
    if (age === null) return;
    ageCounts[bandFor(AGE_BANDS, age)] = (ageCounts[bandFor(AGE_BANDS, age)] || 0) + 1;
  });
  renderBarChart('chartAge', Object.keys(ageCounts), Object.values(ageCounts), 'Students');

  // Seniority distribution
  const senCounts = {};
  SENIORITY_BANDS.forEach(b => senCounts[b.label] = 0);
  members.forEach(m => {
    const months_ = seniorityInMonths(m);
    senCounts[bandFor(SENIORITY_BANDS, months_)] = (senCounts[bandFor(SENIORITY_BANDS, months_)] || 0) + 1;
  });
  renderBarChart('chartSeniority', Object.keys(senCounts), Object.values(senCounts), 'Students');

  // Payment type distribution
  const payCounts = countBy(members, m => paymentLabel(m.paymentType));
  renderPieChart('chartPayment', Object.keys(payCounts), Object.values(payCounts));

  // Grade distribution
  const gradeCounts = {};
  GRADES.forEach(g => gradeCounts[g.label] = 0);
  members.forEach(m => { gradeCounts[gradeLabel(m.grade)] = (gradeCounts[gradeLabel(m.grade)] || 0) + 1; });
  renderBarChart('chartGrade', Object.keys(gradeCounts), Object.values(gradeCounts), 'Students');
}

// ---------- STUDENTS ----------

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
      <td>${classLabel(m.class)}</td>
      <td>${paymentLabel(m.paymentType)}</td>
      <td>${seniorityText(m)}</td>
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
      <div><strong>Class:</strong> ${classLabel(m.class)}</div>
      <div><strong>Payment type:</strong> ${paymentLabel(m.paymentType)}</div>
      <div><strong>Training experience:</strong> ${seniorityText(m)}</div>
      <div><strong>Member since:</strong> ${m.createdAt ? new Date(m.createdAt).toLocaleDateString() : '-'}</div>
    </div>
    <div class="actions"><button class="ghost" id="closeDetailBtn">Close</button></div>
  `;
  document.getElementById('closeDetailBtn').addEventListener('click', () => { panel.style.display = 'none'; });
}

// ---------- REVENUE ENTRY ----------

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
      <td><button class="ghost delRevBtn" data-id="${e.id}">Delete</button></td>
    </tr>
  `).join('') || `<tr><td colspan="6" class="hint">No entries yet.</td></tr>`;

  tbody.querySelectorAll('.delRevBtn').forEach(btn => {
    btn.addEventListener('click', async () => {
      try {
        await deleteRevenueEntry(btn.dataset.id);
        await Promise.all([renderRevenueTable(), renderDashboard(), renderTrends()]);
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

async function initRegistry() {
  initTabs();
  renderStudentsFilters();
  renderRevenueClassOptions();
  renderScheduleTable();

  document.getElementById('revDate').value = todayIso();

  try {
    await Promise.all([renderDashboard(), renderStudentsTable(), renderRevenueTable(), renderTrends()]);
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

  document.getElementById('revenueForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const submitBtn = e.target.querySelector('button[type=submit]');
    submitBtn.disabled = true;
    try {
      await addRevenueEntry({
        date: document.getElementById('revDate').value,
        classKey: document.getElementById('revClass').value,
        amount: parseFloat(document.getElementById('revAmount').value) || 0,
        studentCount: parseInt(document.getElementById('revCount').value, 10) || 0,
        note: document.getElementById('revNote').value.trim(),
      });
      document.getElementById('revenueForm').reset();
      document.getElementById('revDate').value = todayIso();
      await Promise.all([renderRevenueTable(), renderDashboard(), renderTrends()]);
    } catch (err) {
      alert(err.message);
    } finally {
      submitBtn.disabled = false;
    }
  });
}

document.addEventListener('DOMContentLoaded', initGate);

})();
