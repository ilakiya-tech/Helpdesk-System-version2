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
    success: { bg: '#f0fdf4', border: '#bbf7d0', icon: '#16a34a', text: '#14532d' },
    error:   { bg: '#fef2f2', border: '#fecaca', icon: '#dc2626', text: '#7f1d1d' },
    warning: { bg: '#fffbeb', border: '#fde68a', icon: '#d97706', text: '#78350f' },
    info:    { bg: '#f0f9ff', border: '#bae6fd', icon: '#0284c7', text: '#0c4a6e' }
  };

  function toast(type = 'info', title = '', message = '', duration = 4000) {
    const c = ensureToastContainer();
    const colors = TOAST_COLORS[type] || TOAST_COLORS.info;
    const icon   = TOAST_ICONS[type]  || TOAST_ICONS.info;

    const el = document.createElement('div');
    el.className = 'ui-toast';
    el.style.background = colors.bg;
    el.style.borderColor = colors.border;

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
      document.getElementById('ui-confirm-overlay')?.remove();

      const overlay = document.createElement('div');
      overlay.id = 'ui-confirm-overlay';
      overlay.style.cssText = `
        position:fixed;inset:0;z-index:99998;background:rgba(0,0,0,.5);
        display:flex;align-items:center;justify-content:center;
        font-family:'Inter',system-ui,sans-serif;
        animation:fadeIn .2s ease;
      `;
      overlay.innerHTML = `
        <div style="background:#fff;border-radius:12px;padding:24px 28px;max-width:400px;width:90%;
                    box-shadow:0 15px 45px rgba(0,0,0,.15);animation:slideUp .25s cubic-bezier(.22,1,.36,1)">
          <h3 style="margin:0 0 10px;font-size:1.1rem;font-weight:700;color:var(--primary-color)">${title}</h3>
          <p style="margin:0 0 20px;font-size:.9rem;color:#555;line-height:1.5">${message}</p>
          <div style="display:flex;gap:12px;justify-content:flex-end">
            <button id="ui-confirm-cancel" class="btn btn-outline-primary" style="padding:6px 16px !important;background:none !important;color:var(--primary-color) !important">Cancel</button>
            <button id="ui-confirm-ok" class="btn btn-primary" style="padding:6px 16px !important;background:${isDangerous?'#dc2626':'var(--primary-color)'} !important;border-color:${isDangerous?'#dc2626':'var(--primary-color)'} !important">${confirmText}</button>
          </div>
        </div>
      `;
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
    const isTbody = container.tagName && container.tagName.toLowerCase() === 'tbody';
    let html = '';
    if (!isTbody) html += '<table style="width:100%;border-collapse:collapse">';
    for (let r = 0; r < rows; r++) {
      html += '<tr>';
      for (let c = 0; c < cols; c++) {
        html += `<td style="padding:10px 14px"><div class="skeleton-cell" style="width:${60+Math.random()*30|0}%"></div></td>`;
      }
      html += '</tr>';
    }
    if (!isTbody) html += '</table>';
    container.innerHTML = html;
  }

  // ── Session Guard ──────────────────────────────────────────────────────────
  function guardSession() {
    const token = localStorage.getItem('token');
    const role  = localStorage.getItem('userRole');
    if (!token || !role) {
      sessionStorage.setItem('logoutMessage', 'Please log in to continue.');
      window.location.href = 'index.html';
      return false;
    }
    return true;
  }

  // Prevent back-button access to protected pages after logout
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

  function sortItems(items, field, dir = 'asc') {
    return [...items].sort((a, b) => {
      const av = a[field] != null ? a[field] : '';
      const bv = b[field] != null ? b[field] : '';
      const cmp = String(av).localeCompare(String(bv), undefined, { numeric: true, sensitivity: 'base' });
      return dir === 'asc' ? cmp : -cmp;
    });
  }

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

  function renderPagination(el, page, onNavigate) {
    if (!el) return;
    if (page.totalPages <= 1) { el.innerHTML = ''; return; }
    const btn = (label, pg, disabled) =>
      `<button class="ui-page-btn${disabled?' disabled':''}" data-page="${pg}"
               style="padding:6px 12px;border:1.5px solid ${disabled?'#e5e7eb':'var(--primary-color)'};
               background:${disabled?'#f9fafb':'var(--primary-color)'};color:${disabled?'#9ca3af':'#fff'};
               border-radius:6px;cursor:${disabled?'default':'pointer'};font-size:.8125rem;
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
      'Open':        { bg:'rgba(31, 51, 115, 0.05)', color:'#1F3373', border:'rgba(31, 51, 115, 0.1)' },
      'Assigned':    { bg:'rgba(31, 51, 115, 0.06)', color:'#1F3373', border:'rgba(31, 51, 115, 0.12)' },
      'In Progress': { bg:'rgba(180, 83, 9, 0.06)', color:'#b45309', border:'rgba(180, 83, 9, 0.12)' },
      'Resolved':    { bg:'rgba(22, 101, 52, 0.06)', color:'#166534', border:'rgba(22, 101, 52, 0.12)' },
      'Closed':      { bg:'rgba(100, 116, 139, 0.06)', color:'#64748b', border:'rgba(100, 116, 139, 0.12)' },
    };
    const c = map[status] || { bg:'rgba(100, 116, 139, 0.06)', color:'#64748b', border:'rgba(100, 116, 139, 0.12)' };
    return `<span style="display:inline-flex;align-items:center;padding:2px 8px;border-radius:4px;
      background:${c.bg};color:${c.color};border:1px solid ${c.border};
      font-size:.75rem;font-weight:600;white-space:nowrap">${status}</span>`;
  }

  function priorityBadge(priority) {
    const map = {
      'Critical': { bg:'rgba(220, 38, 38, 0.06)', color:'#dc2626', border:'rgba(220, 38, 38, 0.12)' },
      'High':     { bg:'rgba(31, 51, 115, 0.06)', color:'#1F3373', border:'rgba(31, 51, 115, 0.12)' },
      'Medium':   { bg:'rgba(100, 116, 139, 0.06)', color:'#64748b', border:'rgba(100, 116, 139, 0.12)' },
      'Low':      { bg:'rgba(100, 116, 139, 0.04)', color:'#64748b', border:'rgba(100, 116, 139, 0.08)' },
    };
    const c = map[priority] || { bg:'rgba(100, 116, 139, 0.04)', color:'#64748b', border:'rgba(100, 116, 139, 0.08)' };
    return `<span style="display:inline-flex;align-items:center;padding:2px 8px;border-radius:4px;
      background:${c.bg};color:${c.color};border:1px solid ${c.border};
      font-size:.75rem;font-weight:600;white-space:nowrap">${priority || 'Low'}</span>`;
  }

  function availBadge(avail) {
    const a = (avail || '').toLowerCase();
    const map = {
      'available':  { bg:'rgba(22, 101, 52, 0.05)', color:'#166534', border:'rgba(22, 101, 52, 0.1)', label:'Available' },
      'busy':       { bg:'rgba(180, 83, 9, 0.06)', color:'#b45309', border:'rgba(180, 83, 9, 0.12)', label:'Busy' },
      'on_leave':   { bg:'rgba(220, 38, 38, 0.05)', color:'#dc2626', border:'rgba(220, 38, 38, 0.1)', label:'On Leave' },
      'on leave':   { bg:'rgba(220, 38, 38, 0.05)', color:'#dc2626', border:'rgba(220, 38, 38, 0.1)', label:'On Leave' },
    };
    const c = map[a] || { bg:'rgba(100, 116, 139, 0.06)', color:'#64748b', border:'rgba(100, 116, 139, 0.12)', label: avail || 'Unknown' };
    return `<span style="display:inline-flex;align-items:center;padding:2px 8px;border-radius:4px;
      background:${c.bg};color:${c.color};border:1px solid ${c.border};
      font-size:.75rem;font-weight:600;white-space:nowrap">${c.label}</span>`;
  }

  function roleBadge(role) {
    const map = {
      'admin':    { bg:'rgba(31, 51, 115, 0.06)', color:'#1F3373', border:'rgba(31, 51, 115, 0.12)' },
      'staff':    { bg:'rgba(31, 51, 115, 0.05)', color:'#1F3373', border:'rgba(31, 51, 115, 0.1)' },
      'consumer': { bg:'rgba(100, 116, 139, 0.06)', color:'#64748b', border:'rgba(100, 116, 139, 0.12)' },
    };
    const r = (role||'').toLowerCase();
    const c = map[r] || { bg:'rgba(100, 116, 139, 0.06)', color:'#64748b', border:'rgba(100, 116, 139, 0.12)' };
    const label = r.charAt(0).toUpperCase() + r.slice(1);
    return `<span style="display:inline-flex;align-items:center;padding:2px 8px;border-radius:4px;
      background:${c.bg};color:${c.color};border:1px solid ${c.border};
      font-size:.75rem;font-weight:600;white-space:nowrap">${label}</span>`;
  }

  function emptyState(message = 'No records found', icon = '📋') {
    return `
      <div class="empty-state">
        <span class="empty-state-icon">${icon}</span>
        <h5>No Records Found</h5>
        <p>${message}</p>
      </div>`;
  }

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

  // ── Notification Center ───────────────────────────────────────────────────
  let notificationsDropdown = null;

  function ensureNotificationsDropdown() {
    if (notificationsDropdown && document.body.contains(notificationsDropdown)) {
      return notificationsDropdown;
    }
    
    const styleId = 'ui-notifications-styles';
    if (!document.getElementById(styleId)) {
      const style = document.createElement('style');
      style.id = styleId;
      style.textContent = `
        .ui-noti-dropdown {
          position: absolute;
          top: 60px;
          right: 20px;
          width: 340px;
          max-height: 450px;
          background: #ffffff;
          border: 1px solid #e2e8f0;
          border-radius: 8px;
          box-shadow: 0 10px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.1);
          z-index: 10000;
          display: none;
          flex-direction: column;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        }
        .ui-noti-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 12px 16px;
          border-bottom: 1px solid #f1f5f9;
          font-weight: 600;
          color: #1e293b;
        }
        .ui-noti-body {
          flex: 1;
          overflow-y: auto;
          max-height: 350px;
        }
        .ui-noti-item {
          padding: 12px 16px;
          border-bottom: 1px solid #f8fafc;
          cursor: pointer;
          transition: background 0.15s ease;
          display: flex;
          flex-direction: column;
          gap: 4px;
        }
        .ui-noti-item:hover {
          background: #f8fafc;
        }
        .ui-noti-item.unread {
          background: #f0f4ff;
          border-left: 3px solid #1F3373;
        }
        .ui-noti-item.unread:hover {
          background: #e8eeff;
        }
        .ui-noti-item-title {
          font-size: 0.85rem;
          font-weight: 600;
          color: #1e293b;
        }
        .ui-noti-item-msg {
          font-size: 0.8rem;
          color: #64748b;
          line-height: 1.4;
        }
        .ui-noti-item-time {
          font-size: 0.7rem;
          color: #94a3b8;
          align-self: flex-end;
          margin-top: 2px;
        }
        .ui-noti-btn-link {
          background: none;
          border: none;
          color: #1F3373;
          font-weight: 600;
          font-size: 0.75rem;
          cursor: pointer;
          padding: 0;
        }
        .ui-noti-btn-link:hover {
          text-decoration: underline;
        }
        .ui-noti-empty {
          padding: 24px;
          text-align: center;
          color: #94a3b8;
          font-size: 0.85rem;
        }
      `;
      document.head.appendChild(style);
    }

    notificationsDropdown = document.createElement('div');
    notificationsDropdown.id = 'ui-notifications-dropdown';
    notificationsDropdown.className = 'ui-noti-dropdown';
    document.body.appendChild(notificationsDropdown);

    document.addEventListener('click', (e) => {
      const btn = document.getElementById('nav-notifications-btn');
      if (notificationsDropdown.style.display === 'flex' && 
          !notificationsDropdown.contains(e.target) && 
          (!btn || !btn.contains(e.target))) {
        notificationsDropdown.style.display = 'none';
      }
    });

    return notificationsDropdown;
  }

  function toggleNotificationsDropdown(event) {
    if (event) event.stopPropagation();
    const dropdown = ensureNotificationsDropdown();
    if (dropdown.style.display === 'flex') {
      dropdown.style.display = 'none';
    } else {
      const btn = document.getElementById('nav-notifications-btn');
      if (btn) {
        const rect = btn.getBoundingClientRect();
        dropdown.style.top = `${rect.bottom + window.scrollY + 8}px`;
        dropdown.style.left = 'auto';
        dropdown.style.right = `${window.innerWidth - rect.right - window.scrollX}px`;
      }
      dropdown.style.display = 'flex';
      refreshNotificationsDropdown();
    }
  }

  async function refreshNotificationsDropdown() {
    const dropdown = ensureNotificationsDropdown();
    dropdown.innerHTML = `
      <div class="ui-noti-header">
        <span>Notifications</span>
        <button class="ui-noti-btn-link" id="noti-mark-all-btn" style="display: none;" onclick="UI.markAllNotificationsAsRead()">Mark all as read</button>
      </div>
      <div class="ui-noti-body" id="noti-list-container">
        <div style="padding: 20px; text-align: center;">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="animation:spin .75s linear infinite">
            <path d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" opacity=".25"/>
            <path d="M21 12a9 9 0 00-9-9" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    `;

    try {
      const resp = await API.getNotifications();
      const list = resp.items || resp.content || resp._raw || [];
      const listContainer = document.getElementById('noti-list-container');
      const markAllBtn = document.getElementById('noti-mark-all-btn');

      if (!list.length) {
        listContainer.innerHTML = `<div class="ui-noti-empty">No notifications yet</div>`;
        markAllBtn.style.display = 'none';
        return;
      }

      const hasUnread = list.some(n => !n.isRead || !n.read);
      markAllBtn.style.display = hasUnread ? 'inline-block' : 'none';

      listContainer.innerHTML = list.map(n => {
        const isRead = n.isRead || n.read;
        const date = new Date(n.createdAt);
        const timeStr = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) + ' ' + date.toLocaleDateString([], { month: 'short', day: 'numeric' });
        return `
          <div class="ui-noti-item ${isRead ? '' : 'unread'}" onclick="UI.handleNotificationClick(${n.id}, '${n.notificationType}', ${n.referenceId})">
            <div class="ui-noti-item-title">${escapeHtml(n.title)}</div>
            <div class="ui-noti-item-msg">${escapeHtml(n.message)}</div>
            <div class="ui-noti-item-time">${timeStr}</div>
          </div>
        `;
      }).join('');
    } catch (e) {
      document.getElementById('noti-list-container').innerHTML = `<div class="ui-noti-empty" style="color: #dc2626;">Failed to load notifications</div>`;
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
  }

  async function handleNotificationClick(id, type, referenceId) {
    try {
      await API.markNotificationAsRead(id);
      updateUnreadCount();
      
      const dropdown = ensureNotificationsDropdown();
      dropdown.style.display = 'none';

      if (referenceId) {
        if (type.startsWith('LEAVE_')) {
          const role = localStorage.getItem('userRole');
          if (role === 'admin') {
            window.location.href = 'admin-staff.html';
          } else {
            window.location.href = 'staff.html';
          }
        } else {
          localStorage.setItem('selectedTicketId', referenceId);
          window.location.href = 'ticket-details.html';
        }
      }
    } catch (e) {
      console.error(e);
    }
  }

  async function markAllNotificationsAsRead() {
    try {
      await API.markAllNotificationsAsRead();
      updateUnreadCount();
      refreshNotificationsDropdown();
    } catch (e) {
      console.error(e);
    }
  }

  const syncChannel = new BroadcastChannel('carbochem_sync');

  async function updateUnreadCount() {
    const badge = document.getElementById('nav-notifications-badge');
    if (!badge) return;

    try {
      const resp = await API.getUnreadNotificationsCount();
      const count = resp.unreadCount || 0;
      if (count > 0) {
        badge.textContent = count;
        badge.style.display = 'inline-block';
      } else {
        badge.style.display = 'none';
      }
    } catch (e) {
      console.error('Failed to get notifications unread count:', e);
    }
  }

  function initNotifications() {
    updateUnreadCount();

    // Listen to tab synchronization events
    syncChannel.onmessage = (event) => {
      console.log('Sync channel event:', event.data);
      if (event.data.type === 'NOTIFICATION_MUTATION') {
        updateUnreadCount();
        if (notificationsDropdown && notificationsDropdown.style.display === 'flex') {
          refreshNotificationsDropdown();
        }
      } else {
        if (typeof window.loadTickets === 'function') window.loadTickets();
        if (typeof window.loadLeaveRequests === 'function') window.loadLeaveRequests();
        if (typeof window.loadAll === 'function') window.loadAll();
        if (typeof window.loadUsers === 'function') window.loadUsers();
        if (typeof window.loadHolidays === 'function') window.loadHolidays();
        if (typeof window.loadSummary === 'function') window.loadSummary();
        updateUnreadCount();
      }
    };

    // Background-aware polling every 30 seconds
    setInterval(() => {
      if (!document.hidden) {
        updateUnreadCount();
      }
    }, 30000);

    // Focused-tab periodic sync poller every 15 seconds to update dashboards automatically
    setInterval(() => {
      if (!document.hidden && localStorage.getItem('token')) {
        if (typeof window.loadTickets === 'function') window.loadTickets();
        if (typeof window.loadLeaveRequests === 'function') window.loadLeaveRequests();
        if (typeof window.loadAll === 'function') window.loadAll();
        if (typeof window.loadUsers === 'function') window.loadUsers();
        if (typeof window.loadHolidays === 'function') window.loadHolidays();
        if (typeof window.loadSummary === 'function') window.loadSummary();
      }
    }, 15000);
  }

  // Auto-init notifications
  document.addEventListener('DOMContentLoaded', () => {
    if (localStorage.getItem('token')) {
      setTimeout(initNotifications, 500);
    }
  });

  return {
    toast, setLoading, confirm, skeleton,
    guardSession, blockDuplicate,
    filterItems, sortItems, paginate, renderPagination,
    statusBadge, priorityBadge, availBadge, roleBadge,
    emptyState,
    validateEmail, validatePhone, validatePassword, checkDuplicateUser,
    toggleNotificationsDropdown, handleNotificationClick, markAllNotificationsAsRead, updateUnreadCount, initNotifications
  };
})();
