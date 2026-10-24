// Member-facing profile page logic
//
// A phone number is treated as a "contact" that can have several linked
// profiles (e.g. one parent registering more than one child), so lookup
// shows a list rather than a single profile.

let currentMember = null; // member being edited (null when creating)
let selfieData = null;
let contactPrefix = '';   // the contact phone currently being viewed/added to
let contactNumber = '';

function showAlert(msg, type) {
  const box = document.getElementById('alertBox');
  box.innerHTML = `<div class="alert ${type}">${msg}</div>`;
  setTimeout(() => { box.innerHTML = ''; }, 4000);
}

function fillPrefixSelect(select) {
  select.innerHTML = DIAL_CODES.map(d => `<option value="${d.code}">${d.code} ${d.country}</option>`).join('');
}

function showScreen(name) {
  document.getElementById('lookupCard').style.display = name === 'lookup' ? '' : 'none';
  document.getElementById('profilesListCard').style.display = name === 'list' ? '' : 'none';
  document.getElementById('profileForm').style.display = name === 'form' ? '' : 'none';
}

async function renderProfilesList(prefix, number) {
  contactPrefix = prefix;
  contactNumber = number;
  const members = await getMembersByContact(prefix, number);

  const list = document.getElementById('profilesList');
  document.getElementById('noProfilesHint').style.display = members.length ? 'none' : '';
  list.innerHTML = members.map(m => `
    <div class="profile-header" style="margin-bottom:10px;">
      <img src="${m.selfie || PLACEHOLDER_AVATAR}" alt="" />
      <div>
        <div class="name">${escapeHtml(m.firstName)} ${escapeHtml(m.lastName)}</div>
        <div class="meta">Born ${formatDob(m.dob)}</div>
      </div>
      <div class="spacer"></div>
      <span class="badge ${m.grade || 'white'}">${gradeLabel(m.grade)}</span>
    </div>
    <div class="pill-row" style="margin-bottom:10px;">
      ${[
        roleLabel(m.role),
        ...(m.role === 'coach' ? [] : [
          classLabel(m.class),
          paymentLabel(m.paymentType),
          `${m.trainingYears || 0}y ${m.trainingMonths || 0}m training`,
        ]),
        m.gender === 'female' ? 'Female' : 'Male',
      ].map(t => `<span class="badge" style="background:#eceef1;color:#444">${t}</span>`).join('')}
    </div>
    <div class="actions" style="margin-bottom:20px; padding-bottom:20px; border-bottom:1px solid var(--line);">
      <button class="secondary editProfileBtn" data-id="${m.id}">Edit</button>
      <button class="danger deleteProfileBtn" data-id="${m.id}">Delete</button>
    </div>
  `).join('');

  list.querySelectorAll('.editProfileBtn').forEach(btn => {
    btn.addEventListener('click', () => {
      const member = members.find(m => m.id === btn.dataset.id);
      if (!member) return;
      currentMember = member;
      fillFormFromMember(member);
      showScreen('form');
    });
  });

  list.querySelectorAll('.deleteProfileBtn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const member = members.find(m => m.id === btn.dataset.id);
      if (!member) return;
      const name = `${member.firstName} ${member.lastName}`;
      if (!confirm(`Delete the profile for ${name}? This cannot be undone.`)) return;
      try {
        await deleteMember(member.id);
        showAlert('Profile deleted.', 'success');
        await renderProfilesList(contactPrefix, contactNumber);
      } catch (err) {
        showAlert(err.message, 'error');
      }
    });
  });

  showScreen('list');
}

// Coaches don't have a class, payment type or training-seniority — those
// fields only make sense for students/assistants attending a class.
function updateTrainingVisibility() {
  const role = document.querySelector('input[name=role]:checked');
  const isCoach = role && role.value === 'coach';
  document.getElementById('trainingSection').style.display = isCoach ? 'none' : '';
  document.querySelectorAll('input[name=classKey]').forEach(el => { el.required = !isCoach; });
}

function resetForm() {
  document.getElementById('profileForm').reset();
  selfieData = null;
  document.getElementById('selfiePreview').src = PLACEHOLDER_AVATAR;
  document.getElementById('formGradeBadge').textContent = 'White Belt';
  document.getElementById('formGradeBadge').className = 'badge white';
  const defaultRole = document.querySelector('input[name=role][value=student]');
  if (defaultRole) defaultRole.checked = true;
  updateTrainingVisibility();
}

function fillFormFromMember(member) {
  resetForm();
  document.getElementById('formTitle').textContent = 'Edit profile';
  const r = document.querySelector(`input[name=role][value=${member.role || 'student'}]`);
  if (r) r.checked = true;
  updateTrainingVisibility();
  document.getElementById('firstName').value = member.firstName || '';
  document.getElementById('lastName').value = member.lastName || '';
  document.getElementById('dob').value = member.dob || '';
  if (member.gender) {
    const r = document.querySelector(`input[name=gender][value=${member.gender}]`);
    if (r) r.checked = true;
  }
  document.getElementById('formPrefix').value = member.phonePrefix || '+44';
  document.getElementById('formNumber').value = member.phoneNumber || '';
  document.getElementById('whatsapp').value = member.whatsapp || '';
  document.getElementById('emergencyName').value = member.emergencyName || '';
  document.getElementById('emergencyPhone').value = member.emergencyPhone || '';
  document.getElementById('paymentType').value = member.paymentType || 'payg';
  document.getElementById('trainingYears').value = member.trainingYears || 0;
  document.getElementById('trainingMonths').value = member.trainingMonths || 0;
  if (member.class) {
    const c = document.querySelector(`input[name=classKey][value=${member.class}]`);
    if (c) c.checked = true;
  }
  selfieData = member.selfie || null;
  document.getElementById('selfiePreview').src = selfieData || PLACEHOLDER_AVATAR;
  const badge = document.getElementById('formGradeBadge');
  badge.textContent = gradeLabel(member.grade);
  badge.className = `badge ${member.grade || 'white'}`;
}

function startNewProfile(prefix, number) {
  resetForm();
  currentMember = null;
  document.getElementById('formTitle').textContent = 'Create your profile';
  document.getElementById('formPrefix').value = prefix;
  document.getElementById('formNumber').value = number;
  showScreen('form');
}

function init() {
  fillPrefixSelect(document.getElementById('lookupPrefix'));
  fillPrefixSelect(document.getElementById('formPrefix'));
  document.getElementById('formPrefix').value = '+44';
  document.getElementById('lookupPrefix').value = '+44';

  document.getElementById('paymentType').innerHTML =
    PAYMENT_TYPES.map(p => `<option value="${p.key}">${p.label}</option>`).join('');

  document.getElementById('classGroup').innerHTML = CLASSES.map(c =>
    `<label><input type="radio" name="classKey" value="${c.key}" required /> ${c.label}</label>`
  ).join('');

  document.getElementById('roleGroup').innerHTML = ROLES.map(r =>
    `<label><input type="radio" name="role" value="${r.key}" ${r.key === 'student' ? 'checked' : ''} /> ${r.label}</label>`
  ).join('');
  document.querySelectorAll('input[name=role]').forEach(el => {
    el.addEventListener('change', updateTrainingVisibility);
  });
  updateTrainingVisibility();

  document.getElementById('selfiePreview').src = PLACEHOLDER_AVATAR;

  document.getElementById('lookupBtn').addEventListener('click', async () => {
    const prefix = document.getElementById('lookupPrefix').value;
    const number = document.getElementById('lookupNumber').value.trim();
    if (!number) { showAlert('Please enter a phone number.', 'error'); return; }
    const btn = document.getElementById('lookupBtn');
    btn.disabled = true;
    try {
      const members = await getMembersByContact(prefix, number);
      if (members.length > 0) {
        await renderProfilesList(prefix, number);
      } else {
        contactPrefix = prefix;
        contactNumber = number;
        startNewProfile(prefix, number);
      }
    } catch (err) {
      showAlert(err.message, 'error');
    } finally {
      btn.disabled = false;
    }
  });

  document.getElementById('addAnotherBtn').addEventListener('click', () => {
    startNewProfile(contactPrefix, contactNumber);
  });

  document.getElementById('backHomeBtn').addEventListener('click', () => {
    contactPrefix = '';
    contactNumber = '';
    document.getElementById('lookupNumber').value = '';
    showScreen('lookup');
  });

  document.getElementById('cancelFormBtn').addEventListener('click', async () => {
    if (contactPrefix && contactNumber) {
      try {
        await renderProfilesList(contactPrefix, contactNumber);
      } catch (err) {
        showAlert(err.message, 'error');
      }
    } else {
      showScreen('lookup');
    }
  });

  document.getElementById('selfieInput').addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      selfieData = reader.result;
      document.getElementById('selfiePreview').src = selfieData;
    };
    reader.readAsDataURL(file);
  });

  document.getElementById('profileForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const gender = document.querySelector('input[name=gender]:checked');
    const classKey = document.querySelector('input[name=classKey]:checked');
    const role = document.querySelector('input[name=role]:checked');
    const isCoach = (role ? role.value : 'student') === 'coach';
    const member = {
      id: currentMember ? currentMember.id : undefined,
      role: role ? role.value : 'student',
      firstName: document.getElementById('firstName').value.trim(),
      lastName: document.getElementById('lastName').value.trim(),
      dob: document.getElementById('dob').value,
      gender: gender ? gender.value : '',
      phonePrefix: document.getElementById('formPrefix').value,
      phoneNumber: document.getElementById('formNumber').value.trim(),
      whatsapp: document.getElementById('whatsapp').value.trim(),
      emergencyName: document.getElementById('emergencyName').value.trim(),
      emergencyPhone: document.getElementById('emergencyPhone').value.trim(),
      // Coaches don't have a class, payment type or training-seniority —
      // save these as empty/zero rather than whatever the hidden fields
      // still happen to hold.
      class: isCoach ? '' : (classKey ? classKey.value : ''),
      paymentType: isCoach ? '' : document.getElementById('paymentType').value,
      trainingYears: isCoach ? 0 : parseInt(document.getElementById('trainingYears').value, 10) || 0,
      trainingMonths: isCoach ? 0 : parseInt(document.getElementById('trainingMonths').value, 10) || 0,
      selfie: selfieData,
      // grade is intentionally omitted: it's Registry-only, and upsertMember's
      // merge preserves whatever grade is already stored (or defaults new
      // members to 'white') rather than resending a possibly-stale value
      // captured when this form was opened, which could otherwise clobber a
      // grade change a coach made in the meantime.
    };

    const submitBtn = e.target.querySelector('button[type=submit]');
    submitBtn.disabled = true;
    try {
      const saved = await upsertMember(member);
      showAlert('Profile saved successfully.', 'success');
      await renderProfilesList(saved.phonePrefix, saved.phoneNumber);
    } catch (err) {
      showAlert(err.message, 'error');
    } finally {
      submitBtn.disabled = false;
    }
  });
}

document.addEventListener('DOMContentLoaded', init);
