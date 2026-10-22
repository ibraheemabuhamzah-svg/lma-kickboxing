// localStorage persistence layer

const DB = {
  MEMBERS: 'lma_members',
  REVENUE: 'lma_revenue',
};

function uid() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 9);
}

function getMembers() {
  try {
    return JSON.parse(localStorage.getItem(DB.MEMBERS)) || [];
  } catch (e) {
    return [];
  }
}

function saveMembers(members) {
  localStorage.setItem(DB.MEMBERS, JSON.stringify(members));
}

function getMemberByPhone(prefix, number) {
  return getMembers().find(m => m.phonePrefix === prefix && m.phoneNumber === number);
}

function getMemberById(id) {
  return getMembers().find(m => m.id === id);
}

function upsertMember(member) {
  const members = getMembers();
  const idx = members.findIndex(m => m.id === member.id);
  if (idx >= 0) {
    member.updatedAt = new Date().toISOString();
    members[idx] = { ...members[idx], ...member };
  } else {
    member.id = member.id || uid();
    member.createdAt = new Date().toISOString();
    member.updatedAt = member.createdAt;
    if (!member.grade) member.grade = 'white';
    members.push(member);
  }
  saveMembers(members);
  return member;
}

function deleteMember(id) {
  saveMembers(getMembers().filter(m => m.id !== id));
}

function getRevenueEntries() {
  try {
    return JSON.parse(localStorage.getItem(DB.REVENUE)) || [];
  } catch (e) {
    return [];
  }
}

function saveRevenueEntries(entries) {
  localStorage.setItem(DB.REVENUE, JSON.stringify(entries));
}

function addRevenueEntry(entry) {
  const entries = getRevenueEntries();
  entry.id = uid();
  entry.createdAt = new Date().toISOString();
  entries.push(entry);
  saveRevenueEntries(entries);
  return entry;
}

function deleteRevenueEntry(id) {
  saveRevenueEntries(getRevenueEntries().filter(e => e.id !== id));
}
