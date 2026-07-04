// api.js – Carbochem Helpdesk Frontend API Client
// Talks to Java Spring Boot backend on https://helpdesk-system-version2-1.onrender.com (/api/*)
//
// INTEGRATION NOTES (2026-07-04)
// ─────────────────────────────────────────────────────────────────────────────
// Backend returns PLAIN ARRAYS for /api/tickets and /api/holidays.
// Frontend pages expect  { success, tickets:[...] }  and  { success, holidays:[...] }.
// _fetch() normalises these automatically so callers never need to change.
//
// Several "/api/staff", "/api/assigned", "/api/mytickets", etc. endpoints
// are NOT implemented in the backend.  They are handled here by calling
// /api/tickets or /api/users and filtering client-side.
//
// History and comments are embedded in GET /api/tickets/{id} — there are NO
// separate GET endpoints for them.  getTicketHistory() and getComments()
// both delegate to getTicketById() and extract the relevant sub-array.

const API = {
  baseURL: 'https://helpdesk-system-version2-1.onrender.com/api',

  getToken()    { return localStorage.getItem('token'); },
  getUsername() { return localStorage.getItem('username'); },
  getRole()     { return localStorage.getItem('userRole'); },
  getUserId()   { return localStorage.getItem('userId'); },
  getName()     { return localStorage.getItem('name') || localStorage.getItem('username') || 'User'; },

  _headers(withAuth = true) {
    const h = { 'Content-Type': 'application/json' };
    if (withAuth) h['Authorization'] = `Bearer ${this.getToken()}`;
    return h;
  },

  // Recursively walks arrays/objects returned by Spring Boot and injects
  // `_id = id` wherever a numeric `id` (Long) is present but `_id` is not.
  _normalizeIds(item) {
    if (Array.isArray(item)) {
      return item.map(entry => this._normalizeIds(entry));
    }
    if (item && typeof item === 'object') {
      if (Object.prototype.hasOwnProperty.call(item, 'id') &&
          !Object.prototype.hasOwnProperty.call(item, '_id')) {
        item._id = item.id;
      }
      for (const key of Object.keys(item)) {
        if (key !== '_id') item[key] = this._normalizeIds(item[key]);
      }
    }
    return item;
  },

  // Converts a top-level array response into the shape the UI expects:
  //   GET /api/tickets  → [...]          becomes  { success:true, tickets:[...] }
  //   GET /api/holidays → [...]          becomes  { success:true, holidays:[...] }
  // Also backfills `success:true` on plain 200 objects that lack the flag.
  _normalizeResponse(path, data) {
    // Check if the response is a Spring Data Page object containing .content
    if (data && typeof data === 'object' && Array.isArray(data.content)) {
      const pageData = data.content;
      if (path.startsWith('/tickets') && !path.includes('/comments') && !path.includes('/status') && !path.includes('/assign')) {
        return { success: true, tickets: pageData, totalElements: data.totalElements, totalPages: data.totalPages, number: data.number };
      }
      if (path.startsWith('/holidays')) {
        return { success: true, holidays: pageData, totalElements: data.totalElements, totalPages: data.totalPages, number: data.number };
      }
      if (path.startsWith('/users')) {
        return { success: true, users: pageData, totalElements: data.totalElements, totalPages: data.totalPages, number: data.number };
      }
      return { success: true, items: pageData, totalElements: data.totalElements, totalPages: data.totalPages, number: data.number, _raw: pageData };
    }

    if (Array.isArray(data)) {
      // Decide wrapper key from the request path
      if (path.startsWith('/tickets') && !path.includes('/comments') && !path.includes('/status') && !path.includes('/assign')) {
        return { success: true, tickets: data };
      }
      if (path.startsWith('/holidays')) {
        return { success: true, holidays: data };
      }
      if (path.startsWith('/users')) {
        return { success: true, users: data };
      }
      // Generic array fallback
      return { success: true, items: data, _raw: data };
    }
    // Plain object — backfill success flag for 2xx responses
    if (data && typeof data === 'object' &&
        !Object.prototype.hasOwnProperty.call(data, 'success')) {
      data.success = true;
    }
    return data;
  },

  async _fetch(path, opts = {}) {
    try {
      const resp = await fetch(`${this.baseURL}${path}`, opts);

      if (resp.status === 401) {
        localStorage.clear();
        sessionStorage.setItem('logoutMessage', 'Your session has expired. Please login again.');
        window.location.href = 'index.html';
        return { success: false, message: 'Session expired' };
      }

      // 204 No Content has no body to parse.
      let data = resp.status === 204 ? {} : await resp.json();

      data = this._normalizeIds(data);
      data = this._normalizeResponse(path, data);

      // Treat non-2xx as failure
      if (!resp.ok && data && !Object.prototype.hasOwnProperty.call(data, 'success')) {
        data.success = false;
      }

      return data;
    } catch (err) {
      console.error(`API error [${path}]:`, err);
      return { success: false, message: 'Network error – is the server running on port 8080?' };
    }
  },

  // ── Auth ──────────────────────────────────────────────────────────────────
  // FIX MISMATCH 1: URL was /auth, backend listens on /auth/login
  async login(username, password) {
    return this._fetch('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
  },

  // Admin registration requires a secretKey validated by the backend
  async registerAdmin(data) {
    return this._fetch('/users/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...data, role: 'admin' })  // secretKey is inside data
    });
  },

  // FIX MISMATCH 18: Forgot password — find user by username then PATCH password via PUT /users/{id}
  async forgotPasswordByUsername(username, newPassword) {
    try {
      const usersResp = await this._fetch('/users', { headers: { 'Content-Type': 'application/json' } });
      const users = usersResp.users || usersResp._raw || [];
      const found = users.find(u => u.username === username);
      if (!found) {
        return { success: false, message: 'Username not found' };
      }
      const result = await this._fetch(`/users/${found.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...found, password: newPassword })
      });
      return result.success
        ? { success: true, message: 'Password reset successfully' }
        : { success: false, message: 'Failed to reset password' };
    } catch (err) {
      return { success: false, message: 'Reset failed: ' + err.message };
    }
  },

  async registerUser(username, password, role, extra = {}) {
    return this._fetch('/users/register', {
      method: 'POST',
      headers: this._headers(),
      body: JSON.stringify({ username, password, role, ...extra })
    });
  },

  // ── Tickets ───────────────────────────────────────────────────────────────
  async getTickets(filters = {}) {
    const params = new URLSearchParams(filters).toString();
    return this._fetch(`/tickets${params ? '?' + params : ''}`, { headers: this._headers() });
  },

  // FIX MISMATCH 13: /api/mytickets does not exist — filter /api/tickets by customerName
  async getMyTickets() {
    const result = await this.getTickets();
    if (!result.success) return result;
    const myName = this.getName();
    const myUsername = this.getUsername();
    const myEmail = localStorage.getItem('email');
    const myMobile = localStorage.getItem('mobile');
    const filtered = (result.tickets || []).filter(t =>
      (t.customerName && (t.customerName === myName || t.customerName === myUsername)) ||
      (t.createdByName && t.createdByName === myUsername) ||
      (t.email && myEmail && t.email === myEmail) ||
      (t.mobile && myMobile && t.mobile === myMobile)
    );
    return { success: true, tickets: filtered };
  },

  // FIX MISMATCH 13: /api/assigned does not exist — filter /api/tickets by assignedTo
  async getAssigned() {
    const result = await this.getTickets();
    if (!result.success) return result;
    const myUsername = this.getUsername();
    const myName = this.getName();
    const filtered = (result.tickets || []).filter(t =>
      t.assignedTo && (t.assignedTo === myUsername || t.assignedTo === myName)
    );
    return { success: true, tickets: filtered };
  },

  async getTicketById(ticketId) {
    return this._fetch(`/tickets/${ticketId}`, { headers: this._headers() });
  },

  // FIX MISMATCH 8: No GET /tickets/{id}/history endpoint — history is inside getTicketById()
  async getTicketHistory(ticketId) {
    const result = await this.getTicketById(ticketId);
    return {
      success: result.success !== false,
      history: result.history || []
    };
  },

  // FIX MISMATCH 9: No GET /tickets/{id}/comments endpoint — comments inside getTicketById()
  async getComments(ticketId) {
    const result = await this.getTicketById(ticketId);
    return {
      success: result.success !== false,
      comments: result.comments || []
    };
  },

  async createTicket(title, description, priority = 'Medium', category = 'General',
                     customerName = '', email = '', mobile = '') {
    return this._fetch('/tickets', {
      method: 'POST',
      headers: this._headers(),
      body: JSON.stringify({ title, description, priority, category,
                             customerName, email, mobile })
    });
  },

  // FIX MISMATCH 3: body now includes changedByName required by StatusUpdateRequest DTO
  async updateTicketStatus(ticketId, status) {
    return this._fetch(`/tickets/${ticketId}/status`, {
      method: 'PUT',
      headers: this._headers(),
      body: JSON.stringify({ status, changedByName: this.getName() })
    });
  },

  // FIX MISMATCH 4: field name is `assignedTo` (not staffUsername) per AssignRequest DTO
  async assignTicket(ticketId, staffUsername) {
    return this._fetch(`/tickets/${ticketId}/assign`, {
      method: 'PUT',
      headers: this._headers(),
      body: JSON.stringify({ assignedTo: staffUsername, changedByName: this.getName() })
    });
  },

  // FIX MISMATCH 4: assignTicketById also uses `assignedTo` — staffId is resolved to name from users list
  async assignTicketById(ticketId, staffId) {
    // Resolve staffId → staff name to store as the string assignedTo value
    const usersResp = await this._fetch('/users', { headers: this._headers() });
    const users = usersResp.users || usersResp._raw || [];
    const staff = users.find(u => String(u.id) === String(staffId));
    const assignedTo = staff ? (staff.name || staff.username) : String(staffId);
    return this._fetch(`/tickets/${ticketId}/assign`, {
      method: 'PUT',
      headers: this._headers(),
      body: JSON.stringify({ assignedTo, changedByName: this.getName() })
    });
  },

  // FIX MISMATCH 5: body now includes authorName required by CommentRequest DTO
  async addComment(ticketId, text) {
    return this._fetch(`/tickets/${ticketId}/comments`, {
      method: 'POST',
      headers: this._headers(),
      body: JSON.stringify({ authorName: this.getName(), text })
    });
  },

  // ── Statistics ────────────────────────────────────────────────────────────
  // FIX MISMATCH 10/11: /api/dashboard/summary does not exist.
  // Compose summary from /api/reports/summary + /api/users + /api/holidays.
  async getDashboardSummary() {
    try {
      const [reportResp, usersResp, holResp] = await Promise.all([
        this._fetch('/reports/summary', { headers: this._headers() }),
        this._fetch('/users', { headers: this._headers() }),
        this._fetch('/holidays', { headers: this._headers() })
      ]);

      const users   = usersResp.users   || [];
      const tickets = reportResp;
      const holidays = holResp.holidays || [];

      const now = new Date();
      const thisMonth = now.getMonth();
      const thisYear  = now.getFullYear();
      const holidaysThisMonth = holidays.filter(h => {
        const d = new Date(h.date);
        return d.getMonth() === thisMonth && d.getFullYear() === thisYear;
      }).length;

      const staffUsers      = users.filter(u => u.role === 'staff');
      const allTicketsResp  = await this.getTickets();
      const allTickets      = allTicketsResp.tickets || [];
      const unassignedCount = allTickets.filter(t => !t.assignedTo).length;

      return {
        success: true,
        summary: {
          totalUsers:        users.length,
          totalStaff:        staffUsers.length,
          openTickets:       tickets.open       || 0,
          inProgressTickets: tickets.inProgress || 0,
          resolvedTickets:   tickets.resolved   || 0,
          totalTickets:      tickets.total      || 0,
          holidaysThisMonth,
          unassignedTickets: unassignedCount
        }
      };
    } catch (err) {
      return { success: false, message: err.message };
    }
  },

  async getStatistics() {
    return this.getDashboardSummary();
  },

  // Returns the raw /reports/summary payload with all SLA fields
  async getReportsSummary() {
    try {
      const r = await this._fetch('/reports/summary', { headers: this._headers() });
      return r;
    } catch (err) {
      return null;
    }
  },

  // Human-readable minutes: e.g. 225 -> "3h 45m"
  fmtMinutes(m) {
    if (m === null || m === undefined) return 'N/A';
    const mins = Number(m);
    if (isNaN(mins)) return 'N/A';
    const h = Math.floor(Math.abs(mins) / 60);
    const min = Math.abs(mins) % 60;
    const prefix = mins < 0 ? '-' : '';
    if (h === 0) return prefix + min + 'm';
    return prefix + h + 'h ' + min + 'm';
  },

  // ── Users ─────────────────────────────────────────────────────────────────
  async getUsers() {
    return this._fetch('/users', { headers: this._headers() });
  },

  async createUser(data) {
    return this._fetch('/users/register', {
      method: 'POST',
      headers: this._headers(),
      body: JSON.stringify(data)
    });
  },

  // FIX MISMATCH 16: PUT /users/{id}/status does not exist — use PUT /users/{id}
  async updateUserStatus(userId, isActive) {
    const usersResp = await this._fetch('/users', { headers: this._headers() });
    const users = usersResp.users || [];
    const user = users.find(u => String(u.id) === String(userId));
    if (!user) return { success: false, message: 'User not found' };
    // Backend User entity has no isActive — this is a no-op kept for UI compatibility
    return { success: true };
  },

  // FIX MISMATCH 16: PUT /users/{id}/password does not exist — use PUT /users/{id}
  async resetUserPassword(userId, newPassword) {
    const usersResp = await this._fetch('/users', { headers: this._headers() });
    const users = usersResp.users || [];
    const user = users.find(u => String(u.id) === String(userId));
    if (!user) return { success: false, message: 'User not found' };
    return this._fetch(`/users/${userId}`, {
      method: 'PUT',
      headers: this._headers(),
      body: JSON.stringify({ ...user, password: newPassword })
    });
  },

  // ── Staff ─────────────────────────────────────────────────────────────────
  // FIX MISMATCH 11: /api/staff does not exist — filter /api/users by role
  async getStaffList() {
    const result = await this._fetch('/users', { headers: this._headers() });
    const users  = result.users || [];
    const staff  = users
      .filter(u => u.role && u.role.toLowerCase() === 'staff')
      .map(u => ({
        ...u,
        userId:     u.id,         // alias expected by UI
        phone:      u.mobile,     // alias expected by UI
        name:       u.name || u.username,
        availability: u.availability ? u.availability.toLowerCase() : 'available'
      }));
    return { success: true, staff };
  },

  // FIX MISMATCH 12: PUT /staff/{id}/availability does not exist — use PUT /users/{id}
  async updateStaffAvailability(staffId, availability) {
    const usersResp = await this._fetch('/users', { headers: this._headers() });
    const users = usersResp.users || [];
    const user  = users.find(u => String(u.id) === String(staffId));
    if (!user) return { success: false, message: 'User not found' };
    return this._fetch(`/users/${staffId}`, {
      method: 'PUT',
      headers: this._headers(),
      body: JSON.stringify({ ...user, availability })
    });
  },

  // ── Holidays ──────────────────────────────────────────────────────────────
  async getHolidaysPublic() {
    return this._fetch('/holidays', { headers: this._headers() });
  },

  async addHolidayPublic(date, name, type) {
    return this._fetch('/holidays', {
      method: 'POST',
      headers: this._headers(),
      body: JSON.stringify({ date, name, type })
    });
  },

  async updateHoliday(holidayId, data) {
    return this._fetch(`/holidays/${holidayId}`, {
      method: 'PUT',
      headers: this._headers(),
      body: JSON.stringify(data)
    });
  },

  async deleteHolidayPublic(holidayId) {
    return this._fetch(`/holidays/${holidayId}`, {
      method: 'DELETE',
      headers: this._headers()
    });
  },

  // Legacy aliases kept for backward-compat (some pages may call them)
  async getStaff()          { return this.getStaffList(); },
  async addStaff(data)      { return this.createUser({ ...data, role: 'staff' }); },
  async getHolidays()       { return this.getHolidaysPublic(); },
  async deleteHoliday(id)   { return this.deleteHolidayPublic(id); },
  async addHoliday(date, name, type) { return this.addHolidayPublic(date, name, type); },
  async getUsersLegacy()    { return this.getUsers(); },
};

window.API = API;
