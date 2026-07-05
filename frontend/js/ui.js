/**
 * ui.js – Shared UI utilities for the Carbochem Helpdesk frontend.
 *
 * Provides:
 *   UI.toast(type, title, message)   – Premium toast notifications
 *   UI.setLoading(btn, bool, text)   – Button loading state management
 *   UI.confirm(message)              – Stylish confirmation dialog
 *   UI.skeleton(container, rows)     – Skeleton loading placeholder
 *   UI.guardSession()                – Redirect to login if not authenticated
 *   UI.blockDuplicate(key, fn)       – Prevent double execution of async actions
 */

window.UI = (() => {
  // ── Toast Notification System ──────────────────────────────────────────────
  let toastContainer = null;

  function ensureToastContainer() {
    if (toastContainer && document.body.contains(toastContainer)) return toastContainer;
    toastContainer = document.createElement('div');
    toastContainer.id = 'ui-toast-container';
    toastContainer.style.cssText = `
      position:fixed; top:20px; right:20px; z-index:99999;
      display:flex; flex-direction:column; gap:10px;
      pointer-events:none; max-width:380px;
    `;
    document.body.appendChild(toastContainer);
    return toastContainer;
  }

  const TOAST_ICONS = {
    success: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>`,
    error:   `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>`,
    warning: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`,
    info:    `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`
  };

  const TOAST_COLORS = {
    success: { bg: '#d1fae5', border: '#34d399', icon: '#059669', text: '#065f46' },
    error:   { bg: '#fee2e2', border: '#f87171', icon: '#dc2626', text: '#7f1d1d' },
    warning: { bg: '#fef3c7', border: '#fbbf24', icon: '#d97706', text: '#78350f' },
    info:    { bg: '#dbeafe', border: '#60a5fa', icon: '#2563eb', text: '#1e3a8a' }
  };

  function toast(type = 'info', title = '', message = '', duration = 4000) {
    const c = ensureToastContainer();
    const colors = TOAST_COLORS[type] || TOAST_COLORS.info;
    const icon   = TOAST_ICONS[type]  || TOAST_ICONS.info;

    const el = document.createElement('div');
    el.style.cssText = `
      display:flex; align-items:flex-start; gap:12px; padding:14px 16px;
      background:${colors.bg}; border:1.5px solid ${colors.border};
      border-radius:12px; box-shadow:0 4px 20px rgba(0,0,0,.12);
      pointer-events:all; cursor:pointer; min-width:280px;
      animation:slideInRight .3s cubic-bezier(.22,1,.36,1) both;
      transition:opacity .3s ease, transform .3s ease;
      font-family:'Inter',system-ui,sans-serif;
    `;

    el.innerHTML = `
      <div style="color:${colors.icon};flex-shrink:0;margin-top:1px">${icon}</div>
      <div style="flex:1;min-width:0">
        ${title ? `<div style="font-weight:600;font-size:.875rem;color:${colors.text};margin-bottom:${message?'2px':'0'}">${title}</div>` : ''}
        ${message ? `<div style="font-size:.8125rem;color:${colors.text};opacity:.85;line-height:1.4">${message}</div>` : ''}
      </div>
      <button style="background:none;border:none;cursor:pointer;color:${colors.icon};opacity:.6;padding:0;margin-top:1px;flex-shrink:0" onclick="this.parentElement.remove()">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
      </button>
    `;

    // Inject keyframe if needed
    if (!document.getElementById('ui-toast-anim')) {
      const s = document.createElement('style');
      s.id = 'ui-toast-anim';
      s.textContent = `@keyframes slideInRight{from{opacity:0;transform:translateX(60px)}to{opacity:1;transform:translateX(0)}}`;
      document.head.appendChild(s);
    }

    c.appendChild(el);
    el.addEventListener('click', () => dismiss(el));

    const timer = setTimeout(() => dismiss(el), duration);
    el._toastTimer = timer;

    return el;
  }

  function dismiss(el) {
    clearTimeout(el._toastTimer);
    el.style.opacity = '0';
    el.style.transform = 'translateX(60px)';
    setTimeout(() => el.remove(), 300);
  }

  // ── Button Loading State ───────────────────────────────────────────────────
  function setLoading(btn, isLoading, loadingText = 'Please wait…') {
    if (!btn) return;
    if (isLoading) {
      btn._originalText   = btn.innerHTML;
      btn._originalTitle  = btn.title || '';
      btn.disabled        = true;
      btn.style.opacity   = '0.75';
      btn.style.cursor    = 'not-allowed';
      btn.innerHTML = `
        <span style="display:inline-flex;align-items:center;gap:8px">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
               style="animation:spin .75s linear infinite">
            <path d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" opacity=".25"/>
            <path d="M21 12a9 9 0 00-9-9" stroke-linecap="round"/>
          </svg>
          <span>${loadingText}</span>
        </span>`;
      if (!document.getElementById('ui-spin-anim')) {
        const s = document.createElement('style');
        s.id = 'ui-spin-anim';
        s.textContent = `@keyframes spin{to{transform:rotate(360deg)}}`;
        document.head.appendChild(s);
      }
    } else {
      btn.disabled      = false;
      btn.style.opacity = '';
      btn.style.cursor  = '';
      if (btn._originalText !== undefined) {
        btn.innerHTML = btn._originalText;
        delete btn._originalText;
      }
    }
  }

  // ── Confirm Dialog ─────────────────────────────────────────────────────────
  function confirm(message, title = 'Confirm Action', confirmText = 'Confirm', isDangerous = false) {
    return new Promise(resolve => {
      // Remove any existing dialog
      document.getElementById('ui-confirm-overlay')?.remove();

      const overlay = document.createElement('div');
      overlay.id = 'ui-confirm-overlay';
      overlay.style.cssText = `
        position:fixed;inset:0;z-index:99998;background:rgba(0,0,0,.5);
        display:flex;align-items:center;justify-content:center;
        font-family:'Inter',system-ui,sans-serif;
        animation:fadeIn .2s ease;
      `;
      if (!document.getElementById('ui-fade-anim')) {
        const s = document.createElement('style');
        s.id = 'ui-fade-anim';
        s.textContent = `@keyframes fadeIn{from{opacity:0}to{opacity:1}}`;
        document.head.appendChild(s);
      }
      overlay.innerHTML = `
        <div style="background:#fff;border-radius:16px;padding:28px 32px;max-width:420px;width:90%;
                    box-shadow:0 20px 60px rgba(0,0,0,.2);animation:slideUp .25s cubic-bezier(.22,1,.36,1)">
          <h3 style="margin:0 0 10px;font-size:1.125rem;font-weight:700;color:#111">${title}</h3>
          <p style="margin:0 0 24px;font-size:.9rem;color:#555;line-height:1.5">${message}</p>
          <div style="display:flex;gap:12px;justify-content:flex-end">
            <button id="ui-confirm-cancel" style="padding:9px 20px;border:1.5px solid #d1d5db;background:#fff;
                    border-radius:8px;cursor:pointer;font-size:.875rem;font-weight:500;color:#374151">Cancel</button>
            <button id="ui-confirm-ok" style="padding:9px 20px;border:none;
                    background:${isDangerous?'#dc2626':'#2563eb'};color:#fff;
                    border-radius:8px;cursor:pointer;font-size:.875rem;font-weight:600">${confirmText}</button>
          </div>
        </div>
      `;
      if (!document.getElementById('ui-slideup-anim')) {
        const s = document.createElement('style');
        s.id = 'ui-slideup-anim';
        s.textContent = `@keyframes slideUp{from{opacity:0;transform:translateY(20px)}to{opacity:1;transform:translateY(0)}}`;
        document.head.appendChild(s);
      }
      document.body.appendChild(overlay);

      const close = (val) => { overlay.remove(); resolve(val); };
      overlay.querySelector('#ui-confirm-cancel').onclick = () => close(false);
      overlay.querySelector('#ui-confirm-ok').onclick     = () => close(true);
      overlay.onclick = (e) => { if (e.target === overlay) close(false); };
    });
  }

  // ── Skeleton Loader ────────────────────────────────────────────────────────
  function skeleton(container, rows = 5, cols = 4) {
    if (!container) return;
    const shimmer = `
      <style>
        @keyframes shimmer{0%{background-position:-800px 0}100%{background-position:800px 0}}
        .ui-skeleton-cell{height:18px;border-radius:6px;
          background:linear-gradient(90deg,#f0f0f0 25%,#e0e0e0 37%,#f0f0f0 63%);
          background-size:800px 100%;animation:shimmer 1.4s infinite;}
      </style>
    `;
    let html = shimmer + '<table style="width:100%;border-collapse:collapse">';
    for (let r = 0; r < rows; r++) {
      html += '<tr>';
      for (let c = 0; c < cols; c++) {
        html += `<td style="padding:14px 12px"><div class="ui-skeleton-cell" style="width:${60+Math.random()*30|0}%"></div></td>`;
      }
      html += '</tr>';
    }
    html += '</table>';
    container.innerHTML = html;
  }

  // ── Session Guard ──────────────────────────────────────────────────────────
  function guardSession() {
    const token = localStorage.getItem('token');
    const role  = localStorage.getItem('userRole');
    if (!token || !role) {
      sessionStorage.setItem('logoutMessage', 'Please log in to continue.');
      window.location.href = resolveIndexPath();
      return false;
    }
    return true;
  }

  function resolveIndexPath() {
    const path = window.location.pathname;
    if (path.includes('/html/')) return 'index.html';
    return '/html/index.html';
  }

  // Prevent back-button access to protected pages after logout
  function preventBackAccess(requiredRole) {
    const token = localStorage.getItem('token');
    const role  = localStorage.getItem('userRole');
    if (!token || !role) {
      history.replaceState(null, '', resolveIndexPath());
      window.location.href = resolveIndexPath();
      return false;
    }
    if (requiredRole && role !== requiredRole) {
      history.replaceState(null, '', resolveIndexPath());
      window.location.href = resolveIndexPath();
      return false;
    }
    // Push a state so back-button stays on current page
    history.pushState({ page: 'protected' }, '', window.location.href);
    window.addEventListener('popstate', () => {
      if (!localStorage.getItem('token')) {
        window.location.href = resolveIndexPath();
      }
    });
    return true;
  }

  // ── Block duplicate async execution ───────────────────────────────────────
  const _runningKeys = new Set();
  async function blockDuplicate(key, fn) {
    if (_runningKeys.has(key)) return null;
    _runningKeys.add(key);
    try {
      return await fn();
    } finally {
      _runningKeys.delete(key);
    }
  }

  // ── Search / Filter helpers ────────────────────────────────────────────────
  /**
   * Filters an array of objects against a search query across all string fields.
   * @param {Array} items - Array of objects to filter
   * @param {string} query - Search string
   * @param {Array<string>} fields - Fields to search; if empty, searches all string fields
   */
  function filterItems(items, query, fields = []) {
    if (!query || !query.trim()) return items;
    const q = query.trim().toLowerCase();
    return items.filter(item => {
      const keys = fields.length ? fields : Object.keys(item);
      return keys.some(k => {
        const v = item[k];
        return v != null && String(v).toLowerCase().includes(q);
      });
    });
  }

  /**
   * Sorts array by a field.
   * @param {Array} items
   * @param {string} field
   * @param {'asc'|'desc'} dir
   */
  function sortItems(items, field, dir = 'asc') {
    return [...items].sort((a, b) => {
      const av = a[field] != null ? a[field] : '';
      const bv = b[field] != null ? b[field] : '';
      const cmp = String(av).localeCompare(String(bv), undefined, { numeric: true, sensitivity: 'base' });
      return dir === 'asc' ? cmp : -cmp;
    });
  }

  /**
   * Creates a simple pagination object.
   * @param {Array} items - Full list
   * @param {number} page - 0-indexed page
   * @param {number} pageSize
   */
  function paginate(items, page, pageSize) {
    const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
    const safePage   = Math.max(0, Math.min(page, totalPages - 1));
    const start      = safePage * pageSize;
    return {
      items: items.slice(start, start + pageSize),
      page:  safePage,
      totalPages,
      totalItems: items.length,
      hasNext: safePage < totalPages - 1,
      hasPrev: safePage > 0
    };
  }

  /**
   * Renders a pagination bar into a container element.
   * @param {HTMLElement} el
   * @param {object} page - result from paginate()
   * @param {function} onNavigate - called with new page index
   */
  function renderPagination(el, page, onNavigate) {
    if (!el) return;
    if (page.totalPages <= 1) { el.innerHTML = ''; return; }
    const btn = (label, pg, disabled) =>
      `<button class="ui-page-btn${disabled?' disabled':''}" data-page="${pg}"
               style="padding:6px 12px;border:1.5px solid ${disabled?'#e5e7eb':'#2563eb'};
               background:${disabled?'#f9fafb':'#2563eb'};color:${disabled?'#9ca3af':'#fff'};
               border-radius:7px;cursor:${disabled?'default':'pointer'};font-size:.8125rem;
               font-weight:500;transition:all .15s">${label}</button>`;
    el.innerHTML = `
      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;justify-content:flex-end;padding-top:8px">
        ${btn('← Prev', page.page - 1, !page.hasPrev)}
        <span style="font-size:.8125rem;color:#6b7280">Page ${page.page+1} of ${page.totalPages} &nbsp;(${page.totalItems} items)</span>
        ${btn('Next →', page.page + 1, !page.hasNext)}
      </div>`;
    el.querySelectorAll('.ui-page-btn:not(.disabled)').forEach(b =>
      b.addEventListener('click', () => onNavigate(+b.dataset.page))
    );
  }

  // ── Status badge helper ────────────────────────────────────────────────────
  function statusBadge(status) {
    const map = {
      'Open':        { bg:'#dbeafe', color:'#1d4ed8', border:'#93c5fd' },
      'Assigned':    { bg:'#ede9fe', color:'#6d28d9', border:'#c4b5fd' },
      'In Progress': { bg:'#fef3c7', color:'#92400e', border:'#fcd34d' },
      'Resolved':    { bg:'#d1fae5', color:'#065f46', border:'#6ee7b7' },
      'Closed':      { bg:'#f3f4f6', color:'#374151', border:'#d1d5db' },
    };
    const c = map[status] || { bg:'#f3f4f6', color:'#374151', border:'#d1d5db' };
    return `<span style="display:inline-flex;align-items:center;padding:3px 10px;border-radius:20px;
      background:${c.bg};color:${c.color};border:1px solid ${c.border};
      font-size:.75rem;font-weight:600;letter-spacing:.02em;white-space:nowrap">${status}</span>`;
  }

  function priorityBadge(priority) {
    const map = {
      'Critical': { bg:'#fee2e2', color:'#991b1b', border:'#fca5a5' },
      'High':     { bg:'#ffedd5', color:'#9a3412', border:'#fdba74' },
      'Medium':   { bg:'#fef9c3', color:'#854d0e', border:'#fde68a' },
      'Low':      { bg:'#dcfce7', color:'#166534', border:'#86efac' },
    };
    const c = map[priority] || { bg:'#f3f4f6', color:'#374151', border:'#d1d5db' };
    return `<span style="display:inline-flex;align-items:center;padding:3px 10px;border-radius:20px;
      background:${c.bg};color:${c.color};border:1px solid ${c.border};
      font-size:.75rem;font-weight:600;letter-spacing:.02em;white-space:nowrap">${priority || 'N/A'}</span>`;
  }

  function availBadge(avail) {
    const a = (avail || '').toLowerCase();
    const map = {
      'available':  { bg:'#d1fae5', color:'#065f46', border:'#6ee7b7', label:'Available' },
      'busy':       { bg:'#fef3c7', color:'#92400e', border:'#fcd34d', label:'Busy' },
      'on_leave':   { bg:'#fee2e2', color:'#991b1b', border:'#fca5a5', label:'On Leave' },
      'on leave':   { bg:'#fee2e2', color:'#991b1b', border:'#fca5a5', label:'On Leave' },
    };
    const c = map[a] || { bg:'#f3f4f6', color:'#374151', border:'#d1d5db', label: avail || 'Unknown' };
    return `<span style="display:inline-flex;align-items:center;padding:3px 10px;border-radius:20px;
      background:${c.bg};color:${c.color};border:1px solid ${c.border};
      font-size:.75rem;font-weight:600;letter-spacing:.02em;white-space:nowrap">${c.label}</span>`;
  }

  function roleBadge(role) {
    const map = {
      'admin':    { bg:'#fce7f3', color:'#9d174d', border:'#f9a8d4' },
      'staff':    { bg:'#dbeafe', color:'#1e40af', border:'#93c5fd' },
      'consumer': { bg:'#d1fae5', color:'#065f46', border:'#6ee7b7' },
    };
    const r = (role||'').toLowerCase();
    const c = map[r] || { bg:'#f3f4f6', color:'#374151', border:'#d1d5db' };
    const label = r.charAt(0).toUpperCase() + r.slice(1);
    return `<span style="display:inline-flex;align-items:center;padding:3px 10px;border-radius:20px;
      background:${c.bg};color:${c.color};border:1px solid ${c.border};
      font-size:.75rem;font-weight:600;letter-spacing:.02em;white-space:nowrap">${label}</span>`;
  }

  // ── Empty state helper ─────────────────────────────────────────────────────
  function emptyState(message = 'No records found', icon = '📋') {
    return `
      <div style="text-align:center;padding:60px 20px;color:#9ca3af">
        <div style="font-size:3rem;margin-bottom:12px">${icon}</div>
        <div style="font-size:.9375rem;font-weight:500;color:#6b7280">${message}</div>
      </div>`;
  }

  // ── Validation helpers ─────────────────────────────────────────────────────
  function validateEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email || '').trim());
  }

  function validatePhone(phone) {
    const digits = String(phone || '').replace(/\D/g, '');
    return digits.length === 10;
  }

  function validatePassword(pw, minLen = 6) {
    return String(pw || '').length >= minLen;
  }

  /** Client-side duplicate check against a user list from GET /users. */
  function checkDuplicateUser(users, { username, email, excludeId } = {}) {
    const un = String(username || '').trim().toLowerCase();
    const em = String(email || '').trim().toLowerCase();
    for (const u of users || []) {
      if (excludeId && String(u.id) === String(excludeId)) continue;
      if (un && u.username && u.username.toLowerCase() === un) {
        return { ok: false, field: 'username', message: 'Username is already taken' };
      }
      if (em && u.email && u.email.toLowerCase() === em) {
        return { ok: false, field: 'email', message: 'Email is already registered' };
      }
    }
    return { ok: true };
  }

  /** Wrap async API calls with toast on failure. */
  async function handleApi(fn, { successMsg, errorMsg, onSuccess } = {}) {
    try {
      const result = await fn();
      if (result && result.success !== false) {
        if (successMsg) toast('success', 'Success', successMsg);
        if (onSuccess) onSuccess(result);
        return result;
      }
      toast('error', 'Error', (result && result.message) || errorMsg || 'Request failed');
      return result;
    } catch (err) {
      console.error(err);
      toast('error', 'Error', errorMsg || 'Network error. Please try again.');
      return { success: false };
    }
  }

  // ── Public API ─────────────────────────────────────────────────────────────
  return {
    toast, setLoading, confirm, skeleton,
    guardSession, preventBackAccess, blockDuplicate,
    filterItems, sortItems, paginate, renderPagination,
    statusBadge, priorityBadge, availBadge, roleBadge,
    emptyState,
    validateEmail, validatePhone, validatePassword, checkDuplicateUser,
    handleApi
  };
})();
