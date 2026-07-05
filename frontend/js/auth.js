// auth.js – Shared frontend auth helper (used by all pages)

/** Backend stores "consumer"; legacy UI may use "client". */
function normalizeRole(role) {
  if (!role) return '';
  const r = String(role).toLowerCase().trim();
  return r === 'client' ? 'consumer' : r;
}

function isConsumer(role) {
  return normalizeRole(role) === 'consumer';
}

function getDashboardUrl(role) {
  const map = { admin: 'admin.html', staff: 'staff.html', consumer: 'client.html' };
  return map[normalizeRole(role)] || 'index.html';
}

/** Sidebar uses "client" key for consumer portal links. */
function getSidebarRole(role) {
  const r = normalizeRole(role || localStorage.getItem('userRole'));
  return r === 'consumer' ? 'client' : r;
}

function logout() {
  const doLogout = () => {
    localStorage.clear();
    sessionStorage.setItem('logoutMessage', 'You have been logged out.');
    history.replaceState(null, '', 'index.html');
    window.location.replace('index.html');
  };
  if (window.UI && UI.confirm) {
    UI.confirm('Are you sure you want to logout?', 'Logout', 'Logout', false).then(ok => {
      if (ok) doLogout();
    });
  } else if (confirm('Are you sure you want to logout?')) {
    doLogout();
  }
}

function requireAuth(allowedRoles) {
  const token = localStorage.getItem('token');
  const role = normalizeRole(localStorage.getItem('userRole'));
  if (!token) {
    sessionStorage.setItem('logoutMessage', 'Please log in to continue.');
    window.location.replace('index.html');
    return false;
  }
  if (allowedRoles && allowedRoles.length) {
    const allowed = allowedRoles.map(normalizeRole);
    if (!allowed.includes(role)) {
      if (window.UI) UI.toast('error', 'Access Denied', 'You do not have permission to view this page.');
      window.location.replace('index.html');
      return false;
    }
  }
  initPageGuard();
  return true;
}

/** Prevent back-button from reopening dashboard after logout. */
function initPageGuard() {
  if (window._pageGuardInit) return;
  window._pageGuardInit = true;
  history.replaceState({ guarded: true }, '', window.location.href);
  window.addEventListener('popstate', () => {
    if (!localStorage.getItem('token')) {
      window.location.replace('index.html');
    }
  });
}

function getStoredUser() {
  return {
    token:    localStorage.getItem('token'),
    username: localStorage.getItem('username'),
    role:     normalizeRole(localStorage.getItem('userRole')),
    userId:   localStorage.getItem('userId'),
    name:     localStorage.getItem('name') || localStorage.getItem('username'),
  };
}