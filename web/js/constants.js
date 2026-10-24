// Shared constants for LMA Muay Thai & KickBoxing

// Shared cloud database (Supabase) so the web app and the Android app see
// the same data. The anon key is meant to be public (same trust model as
// the Registry's own client-side password gate) but it does mean anyone
// who extracts it can read/write the tables directly, bypassing the
// Registry password — see supabase/schema.sql for the policies in place.
const SUPABASE_URL = 'https://oaxeeljelnpobzxzfsrh.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9heGVlbGplbG5wb2J6eHpmc3JoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI3MDY2NzIsImV4cCI6MjEwODI4MjY3Mn0.SWq5rPQNuPmWWhDkHNKwNLCRgvZcYwP3cumNRVl_CLs';

// Matches the .badge.<grade> colors in style.css and Android's
// Constants.gradeColorRes, kept here too so JS code (chart rendering) can
// use the same hex values without parsing CSS.
const GRADE_COLORS = {
  white: '#9a9a9a', red: '#e30613', yellow: '#e6b800', orange: '#e8791a',
  green: '#2e9e4b', blue: '#1565c0', purple: '#7b3fa0', brown: '#6b4226', black: '#111113',
};

const GRADES = [
  { key: 'white', label: 'White Belt' },
  { key: 'red', label: 'Red Belt' },
  { key: 'yellow', label: 'Yellow Belt' },
  { key: 'orange', label: 'Orange Belt' },
  { key: 'green', label: 'Green Belt' },
  { key: 'blue', label: 'Blue Belt' },
  { key: 'purple', label: 'Purple Belt' },
  { key: 'brown', label: 'Brown Belt' },
  { key: 'black', label: 'Black Belt' },
];

const CLASSES = [
  { key: 'girls', label: 'Girls Only' },
  { key: 'kids', label: 'Kids' },
  { key: 'teens_adults', label: 'Teens & Adults' },
];

const PAYMENT_TYPES = [
  { key: 'payg', label: 'Pay as you go' },
  { key: 'monthly', label: 'Monthly membership' },
  { key: 'transfer', label: 'Transfer' },
  { key: 'free', label: 'Free' },
];

const ROLES = [
  { key: 'student', label: 'Student' },
  { key: 'coach', label: 'Coach' },
  { key: 'assistant', label: 'Assistant (Volunteer)' },
];

// Fixed weekly schedule. `key` must stay stable: it is the unit used for
// revenue entries and week-over-week class comparisons.
const SCHEDULE = [
  { key: 'mon-18-teens', day: 'Monday', dayIndex: 1, time: '18:00 - 19:00', classKey: 'teens_adults', label: 'Teens & Adults' },
  { key: 'wed-18-girls', day: 'Wednesday', dayIndex: 3, time: '18:00 - 19:00', classKey: 'girls', label: 'Girls Only' },
  { key: 'wed-1915-kids', day: 'Wednesday', dayIndex: 3, time: '19:15 - 20:15', classKey: 'kids', label: 'Kids' },
  { key: 'thu-18-teens', day: 'Thursday', dayIndex: 4, time: '18:00 - 19:00', classKey: 'teens_adults', label: 'Teens & Adults' },
  { key: 'fri-18-sparring', day: 'Friday', dayIndex: 5, time: '18:00 - 19:00', classKey: 'teens_adults', label: 'Teens & Adults Sparring' },
  { key: 'sat-09-girls', day: 'Saturday', dayIndex: 6, time: '09:00 - 10:00', classKey: 'girls', label: 'Girls Only' },
  { key: 'sat-1215-kids', day: 'Saturday', dayIndex: 6, time: '12:15 - 13:15', classKey: 'kids', label: 'Kids' },
  { key: 'sat-1315-teens', day: 'Saturday', dayIndex: 6, time: '13:15 - 14:15', classKey: 'teens_adults', label: 'Teens & Adults' },
];

// A reasonably broad list of international dial codes.
const DIAL_CODES = [
  { code: '+44', country: 'United Kingdom' },
  { code: '+971', country: 'UAE' }, { code: '+966', country: 'Saudi Arabia' },
  { code: '+974', country: 'Qatar' }, { code: '+973', country: 'Bahrain' },
  { code: '+965', country: 'Kuwait' }, { code: '+968', country: 'Oman' },
  { code: '+20', country: 'Egypt' }, { code: '+962', country: 'Jordan' },
  { code: '+961', country: 'Lebanon' }, { code: '+963', country: 'Syria' },
  { code: '+964', country: 'Iraq' }, { code: '+970', country: 'Palestine' },
  { code: '+90', country: 'Turkey' }, { code: '+98', country: 'Iran' },
  { code: '+92', country: 'Pakistan' }, { code: '+91', country: 'India' },
  { code: '+880', country: 'Bangladesh' }, { code: '+94', country: 'Sri Lanka' },
  { code: '+63', country: 'Philippines' }, { code: '+60', country: 'Malaysia' },
  { code: '+65', country: 'Singapore' }, { code: '+62', country: 'Indonesia' },
  { code: '+66', country: 'Thailand' }, { code: '+84', country: 'Vietnam' },
  { code: '+86', country: 'China' }, { code: '+852', country: 'Hong Kong' },
  { code: '+81', country: 'Japan' }, { code: '+82', country: 'South Korea' },
  { code: '+1', country: 'USA / Canada' },
  { code: '+33', country: 'France' }, { code: '+49', country: 'Germany' },
  { code: '+39', country: 'Italy' }, { code: '+34', country: 'Spain' },
  { code: '+351', country: 'Portugal' }, { code: '+31', country: 'Netherlands' },
  { code: '+32', country: 'Belgium' }, { code: '+41', country: 'Switzerland' },
  { code: '+43', country: 'Austria' }, { code: '+46', country: 'Sweden' },
  { code: '+47', country: 'Norway' }, { code: '+45', country: 'Denmark' },
  { code: '+358', country: 'Finland' }, { code: '+48', country: 'Poland' },
  { code: '+30', country: 'Greece' }, { code: '+7', country: 'Russia' },
  { code: '+380', country: 'Ukraine' }, { code: '+27', country: 'South Africa' },
  { code: '+234', country: 'Nigeria' }, { code: '+254', country: 'Kenya' },
  { code: '+212', country: 'Morocco' }, { code: '+216', country: 'Tunisia' },
  { code: '+213', country: 'Algeria' }, { code: '+61', country: 'Australia' },
  { code: '+64', country: 'New Zealand' }, { code: '+55', country: 'Brazil' },
  { code: '+52', country: 'Mexico' }, { code: '+54', country: 'Argentina' },
];

// SHA-256 of the registry password, so the real password isn't sitting in
// plaintext in this publicly-served file. Still only as strong as a
// client-side check can be (no backend), but closes the trivial
// "view-source and read the password" path.
const REGISTRY_PASSWORD_HASH = 'dcdc240c9e0c71a200eec8cc118ae8dbd640ae3f79b8d41515d96462bc09bcc7';

async function sha256Hex(text) {
  const bytes = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, '0')).join('');
}

const PLACEHOLDER_AVATAR = 'data:image/svg+xml;utf8,' + encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="#f0f0f2"/><circle cx="50" cy="38" r="18" fill="#cfcfd4"/><ellipse cx="50" cy="88" rx="32" ry="24" fill="#cfcfd4"/></svg>'
);

function gradeLabel(key) {
  const g = GRADES.find(g => g.key === key);
  return g ? g.label : 'White Belt';
}

function classLabel(key) {
  const c = CLASSES.find(c => c.key === key);
  return c ? c.label : key;
}

function paymentLabel(key) {
  const p = PAYMENT_TYPES.find(p => p.key === key);
  return p ? p.label : key;
}

function roleLabel(key) {
  const r = ROLES.find(r => r.key === key);
  return r ? r.label : 'Student';
}

const HTML_ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
function escapeHtml(value) {
  if (value == null) return '';
  return String(value).replace(/[&<>"']/g, c => HTML_ESCAPES[c]);
}

function formatDob(iso) {
  if (!iso) return '';
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}
