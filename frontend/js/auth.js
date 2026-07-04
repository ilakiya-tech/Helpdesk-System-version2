// auth.js – Shared frontend auth helper (used by all pages)

function logout() {
  if (confirm('Are you sure you want to logout?')) {
    localStorage.clear();
    window.location.href = 'index.html';
  }
}

function requireAuth(allowedRoles) {
  const token = localStorage.getItem('token');
  const role  = localStorage.getItem('userRole');
  if (!token) { window.location.href = 'index.html'; return false; }
  if (allowedRoles && !allowedRoles.includes(role)) {
    alert('Access denied. Redirecting...');
    window.location.href = 'index.html';
    return false;
  }
  return true;
}

function getStoredUser() {
  return {
    token:    localStorage.getItem('token'),
    username: localStorage.getItem('username'),
    role:     localStorage.getItem('userRole'),
    userId:   localStorage.getItem('userId'),
    name:     localStorage.getItem('name') || localStorage.getItem('username'),
  };
}