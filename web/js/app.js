// Member-facing profile page logic

let currentMember = null; // member being viewed/edited
let selfieData = null;

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
  document.getElementById('viewCard').style.display = name === 'view' ? '' : 'none';
  document.getElementById('profileForm').style.display = name === 'form' ? '' : 'none';
}

function renderView(member) {
  currentMember = member;
  document.getElementById('viewSelfie').src = member.selfie || PLACEHOLDER_AVATAR;
  document.getElementById('viewName').textContent = `${member.firstName} ${member.lastName}`;
  document.getElementById('viewMeta').textContent = `${member.phonePrefix} ${member.phoneNumber} · Born ${formatDob(member.dob)}`;
  const badge = document.getElementById('viewGrade');
  badge.textContent = gradeLabel(member.grade);
  badge.className = `badge ${member.grade || 'white'}`;

  const pills = document.getElementById('viewPills');
  pills.innerHTML = [
    roleLabel(member.role),
    classLabel(member.class),
    paymentLabel(member.paymentType),
    member.gender === 'female' ? 'Female' : 'Male',
    `${member.trainingYears || 0}y ${member.trainingMonths || 0}m training`,
  ].map(t => `<span class="badge" style="background:#eceef1;color:#444">${t}</span>`).join('');

  showScreen('view');
}

function resetForm() {
  document.getElementById('profileForm').reset();
  selfieData = null;
  document.getElementById('selfiePreview').src = PLACEHOLDER_AVATAR;
  document.getElementById('formGradeBadge').textContent = 'White Belt';
  document.getElementById('formGradeBadge').className = 'badge white';
  const defaultRole = document.querySelector('input[name=role][value=student]');
  if (defaultRole) defaultRole.checked = true;
}

function fillFormFromMember(member) {
  resetForm();
  document.getElementById('formTitle').textContent = 'Edit your profile';
  const r = document.querySelector(`input[name=role][value=${member.role || 'student'}]`);
  if (r) r.checked = true;
  document.getElementById('firstName').value = member.firstName || '';
  document.getElementById('lastName').value = member.lastName || '';
  document.getElementById('dob').value = member.dob || '';
  if (member.gender) {
    const r = document.querySelector(`input[name=gender][value=${member.gender}]`);
    if (r) r.checked = true;
  }
  document.getElementById('formPrefix').value = member.phonePrefix || '+971';
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

function init() {
  fillPrefixSelect(document.getElementById('lookupPrefix'));
  fillPrefixSelect(document.getElementById('formPrefix'));
  document.getElementById('formPrefix').value = '+971';
  document.getElementById('lookupPrefix').value = '+971';

  document.getElementById('paymentType').innerHTML =
    PAYMENT_TYPES.map(p => `<option value="${p.key}">${p.label}</option>`).join('');

  document.getElementById('classGroup').innerHTML = CLASSES.map(c =>
    `<label><input type="radio" name="classKey" value="${c.key}" required /> ${c.label}</label>`
  ).join('');

  document.getElementById('roleGroup').innerHTML = ROLES.map(r =>
    `<label><input type="radio" name="role" value="${r.key}" ${r.key === 'student' ? 'checked' : ''} /> ${r.label}</label>`
  ).join('');

  document.getElementById('selfiePreview').src = PLACEHOLDER_AVATAR;

  document.getElementById('lookupBtn').addEventListener('click', async () => {
    const prefix = document.getElementById('lookupPrefix').value;
    const number = document.getElementById('lookupNumber').value.trim();
    if (!number) { showAlert('Please enter a phone number.', 'error'); return; }
    const btn = document.getElementById('lookupBtn');
    btn.disabled = true;
    try {
      const member = await getMemberByPhone(prefix, number);
      if (member) {
        renderView(member);
      } else {
        resetForm();
        document.getElementById('formTitle').textContent = 'Create your profile';
        document.getElementById('formPrefix').value = prefix;
        document.getElementById('formNumber').value = number;
        currentMember = null;
        showScreen('form');
      }
    } catch (err) {
      showAlert(err.message, 'error');
    } finally {
      btn.disabled = false;
    }
  });

  document.getElementById('editBtn').addEventListener('click', () => {
    fillFormFromMember(currentMember);
    showScreen('form');
  });

  document.getElementById('deleteProfileBtn').addEventListener('click', async () => {
    if (!currentMember) return;
    const name = `${currentMember.firstName} ${currentMember.lastName}`;
    if (!confirm(`Delete the profile for ${name}? This cannot be undone.`)) return;
    try {
      await deleteMember(currentMember.id);
      currentMember = null;
      showScreen('lookup');
      showAlert('Profile deleted.', 'success');
    } catch (err) {
      showAlert(err.message, 'error');
    }
  });

  document.getElementById('backBtn').addEventListener('click', () => {
    currentMember = null;
    showScreen('lookup');
  });

  document.getElementById('cancelFormBtn').addEventListener('click', () => {
    if (currentMember) { renderView(currentMember); } else { showScreen('lookup'); }
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
      class: classKey ? classKey.value : '',
      paymentType: document.getElementById('paymentType').value,
      trainingYears: parseInt(document.getElementById('trainingYears').value, 10) || 0,
      trainingMonths: parseInt(document.getElementById('trainingMonths').value, 10) || 0,
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
      // prevent duplicate phone numbers across different members
      const existing = await getMemberByPhone(member.phonePrefix, member.phoneNumber);
      if (existing && (!currentMember || existing.id !== currentMember.id)) {
        showAlert('A profile already exists with this phone number.', 'error');
        return;
      }

      const saved = await upsertMember(member);
      showAlert('Profile saved successfully.', 'success');
      renderView(saved);
    } catch (err) {
      showAlert(err.message, 'error');
    } finally {
      submitBtn.disabled = false;
    }
  });
}

document.addEventListener('DOMContentLoaded', init);
