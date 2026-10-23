// Shared cloud persistence layer (Supabase REST / PostgREST).
// Every function here is async — call sites must `await` them.

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 9);
}

async function supabaseRequest(path, options = {}) {
  let res;
  try {
    res = await fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
      ...options,
      headers: {
        apikey: SUPABASE_ANON_KEY,
        Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
        'Content-Type': 'application/json',
        ...(options.headers || {}),
      },
    });
  } catch (e) {
    throw new Error('Network error — check your internet connection.');
  }
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`Database error (${res.status}): ${text}`);
  }
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

// ---------- MEMBERS ----------

function memberToRow(m) {
  return {
    id: m.id,
    role: m.role || 'student',
    first_name: m.firstName || '',
    last_name: m.lastName || '',
    dob: m.dob || '',
    gender: m.gender || '',
    phone_prefix: m.phonePrefix || '+971',
    phone_number: m.phoneNumber || '',
    whatsapp: m.whatsapp || '',
    emergency_name: m.emergencyName || '',
    emergency_phone: m.emergencyPhone || '',
    class_category: m.class || '',
    payment_type: m.paymentType || 'payg',
    training_years: m.trainingYears || 0,
    training_months: m.trainingMonths || 0,
    selfie_base64: m.selfie || null,
    grade: m.grade || 'white',
    created_at: m.createdAt,
    updated_at: m.updatedAt,
  };
}

function rowToMember(r) {
  return {
    id: r.id,
    role: r.role,
    firstName: r.first_name,
    lastName: r.last_name,
    dob: r.dob,
    gender: r.gender,
    phonePrefix: r.phone_prefix,
    phoneNumber: r.phone_number,
    whatsapp: r.whatsapp,
    emergencyName: r.emergency_name,
    emergencyPhone: r.emergency_phone,
    class: r.class_category,
    paymentType: r.payment_type,
    trainingYears: r.training_years,
    trainingMonths: r.training_months,
    selfie: r.selfie_base64,
    grade: r.grade,
    createdAt: r.created_at,
    updatedAt: r.updated_at,
  };
}

async function getMembers() {
  const rows = await supabaseRequest('members?select=*&order=created_at.asc');
  return (rows || []).map(rowToMember);
}

// Strips spacing/dashes and a leading zero so "0501234567", "050-123-4567"
// and "501234567" are recognized as the same number.
function normalizePhone(number) {
  return (number || '').replace(/[\s-]/g, '').replace(/^0+/, '');
}

async function getMemberByPhone(prefix, number) {
  const target = normalizePhone(number);
  const members = await getMembers();
  return members.find(m => m.phonePrefix === prefix && normalizePhone(m.phoneNumber) === target);
}

async function getMemberById(id) {
  const rows = await supabaseRequest(`members?id=eq.${encodeURIComponent(id)}&select=*`);
  return rows && rows[0] ? rowToMember(rows[0]) : null;
}

async function upsertMember(member) {
  const now = Date.now();
  const isNew = !member.id;
  const toSave = { ...member, id: member.id || uid() };
  toSave.updatedAt = now;
  if (isNew) {
    toSave.createdAt = now;
    if (!toSave.grade) toSave.grade = 'white';
  } else {
    // Preserve whatever is already stored for fields this payload doesn't
    // explicitly set (e.g. grade, which the member-facing form omits on
    // purpose — it's Registry-only).
    const existing = await getMemberById(toSave.id);
    if (existing) {
      for (const key of Object.keys(existing)) {
        if (toSave[key] === undefined) toSave[key] = existing[key];
      }
      toSave.createdAt = existing.createdAt;
    } else if (!toSave.grade) {
      toSave.grade = 'white';
    }
  }
  const rows = await supabaseRequest('members', {
    method: 'POST',
    headers: { Prefer: 'resolution=merge-duplicates,return=representation' },
    body: JSON.stringify([memberToRow(toSave)]),
  });
  return rows && rows[0] ? rowToMember(rows[0]) : toSave;
}

async function deleteMember(id) {
  await supabaseRequest(`members?id=eq.${encodeURIComponent(id)}`, { method: 'DELETE' });
}

// ---------- REVENUE ----------

function entryToRow(e) {
  return {
    id: e.id,
    date: e.date || '',
    class_key: e.classKey || '',
    amount: e.amount || 0,
    student_count: e.studentCount || 0,
    note: e.note || '',
    created_at: e.createdAt,
  };
}

function rowToEntry(r) {
  return {
    id: r.id,
    date: r.date,
    classKey: r.class_key,
    amount: Number(r.amount) || 0,
    studentCount: r.student_count,
    note: r.note,
    createdAt: r.created_at,
  };
}

async function getRevenueEntries() {
  const rows = await supabaseRequest('revenue_entries?select=*&order=date.asc');
  return (rows || []).map(rowToEntry);
}

async function addRevenueEntry(entry) {
  const toSave = { ...entry, id: uid(), createdAt: Date.now() };
  const rows = await supabaseRequest('revenue_entries', {
    method: 'POST',
    headers: { Prefer: 'return=representation' },
    body: JSON.stringify([entryToRow(toSave)]),
  });
  return rows && rows[0] ? rowToEntry(rows[0]) : toSave;
}

async function deleteRevenueEntry(id) {
  await supabaseRequest(`revenue_entries?id=eq.${encodeURIComponent(id)}`, { method: 'DELETE' });
}
