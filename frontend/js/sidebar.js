// sidebar.js – Dynamic top horizontal navigation bar for all dashboards

const Sidebar = {
  adminLinks: [
    { href: 'admin.html', label: 'Dashboard' },
    { href: 'admin-users.html', label: 'Users' },
    { href: 'admin-staff.html', label: 'Staff' },
    { href: 'admin.html#tickets', label: 'Tickets' },
    { href: 'admin-holidays.html', label: 'Holidays' },
    { href: 'admin-report.html', label: 'Reports' },
  ],
  staffLinks: [
    { href: 'staff.html', label: 'Tickets' },
    { href: 'staff.html#holidays', label: 'Holidays' }
  ],
  clientLinks: [
    { href: 'client.html', label: 'Tickets' },
    { href: 'client.html#holidays', label: 'Holidays' }
  ],

  render(role, activePage) {
    const links = role === 'admin' ? this.adminLinks
      : role === 'staff' ? this.staffLinks : this.clientLinks;
    const roleLabel = role === 'admin' ? 'Admin' : role === 'staff' ? 'Staff' : 'Consumer';
    const username = localStorage.getItem('username') || '';

    const navHtml = links.length ? `
      <nav class="sidebar-nav">
        ${links.map(l => {
          return `<a href="${l.href}" class="sidebar-link d-flex align-items-center gap-2">
            <span>${l.label}</span>
          </a>`;
        }).join('')}
      </nav>` : '';

    return `
      <div class="sidebar text-white d-flex flex-row align-items-center justify-content-between" id="app-sidebar">
        <div class="sidebar-header">
          <div class="d-flex align-items-center gap-2">
            <div class="sidebar-logo">CC</div>
            <div>
              <div class="fw-bold text-white" style="font-size:0.95rem; line-height: 1.1">Carbochem</div>
              <small class="opacity-75 text-white" style="font-size:0.75rem">${roleLabel} Portal</small>
            </div>
          </div>
        </div>
        ${navHtml}
        <div class="sidebar-footer d-flex align-items-center gap-3">
          <div class="position-relative" style="cursor: pointer; display: flex; align-items: center;" id="nav-notifications-btn" onclick="UI.toggleNotificationsDropdown(event)">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-white"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg>
            <span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger" id="nav-notifications-badge" style="font-size: 0.6rem; padding: 3px 6px; transform: translate(-30%, -30%) !important; display: none;">0</span>
          </div>
          <small class="opacity-75 text-white d-none d-sm-inline">User: <strong>${username}</strong></small>
          <button class="btn btn-outline-light btn-sm" style="padding: 4px 10px !important; font-size: 0.75rem !important; background: transparent !important; color: #fff !important; border-color: #fff !important;" onclick="logout()">Logout</button>
        </div>
      </div>`;
  },

  inject(role, activePage, targetId = 'sidebar-container') {
    const el = document.getElementById(targetId);
    if (el) {
      el.innerHTML = this.render(role, activePage);
      
      // Update links to have the active class correctly highlighted
      const updateActiveLink = () => {
        const currentFile = window.location.pathname.split('/').pop() || (role === 'admin' ? 'admin.html' : role === 'staff' ? 'staff.html' : 'client.html');
        const currentHash = window.location.hash;
        const currentFull = currentFile + currentHash;
        
        el.querySelectorAll('.sidebar-link').forEach(link => {
          link.classList.remove('active');
          const href = link.getAttribute('href');
          
          if (href === currentFull || 
              (currentHash === '' && href === currentFile) || 
              (currentHash === '#dashboard' && href === currentFile)) {
            link.classList.add('active');
          }
        });
      };

      updateActiveLink();
      window.addEventListener('hashchange', updateActiveLink);
    }
  }
};

window.Sidebar = Sidebar;
