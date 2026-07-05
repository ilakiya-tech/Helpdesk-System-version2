// sidebar.js – Dynamic top horizontal navigation bar for all dashboards

const Sidebar = {
  adminLinks: [
    { href: 'admin.html', label: 'Dashboard', icon: '📊' },
    { href: 'admin-users.html', label: 'Users', icon: '👥' },
    { href: 'admin-staff.html', label: 'Staff', icon: '🛠️' },
    { href: 'admin.html#tickets', label: 'Tickets', icon: '🎫' },
    { href: 'admin-holidays.html', label: 'Holidays', icon: '📅' },
    { href: 'admin-report.html', label: 'Reports', icon: '📈' },
  ],
  staffLinks: [
    { href: 'staff.html', label: 'Assigned Tickets', icon: '🎫' },
    { href: 'staff.html#availability', label: 'Leave Status', icon: '🏖️' },
  ],
  clientLinks: [
    { href: 'client.html', label: 'My Tickets', icon: '🎫' },
    { href: 'client.html#raise', label: 'Raise Ticket', icon: '➕' },
  ],

  render(role, activePage) {
    const links = role === 'admin' ? this.adminLinks
      : role === 'staff' ? this.staffLinks : this.clientLinks;
    const roleLabel = role === 'admin' ? 'Admin' : role === 'staff' ? 'Staff' : 'Consumer';
    const username = localStorage.getItem('username') || '';

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
        <nav class="sidebar-nav">
          ${links.map(l => {
            return `<a href="${l.href}" class="sidebar-link d-flex align-items-center gap-2">
              <span>${l.icon}</span><span>${l.label}</span>
            </a>`;
          }).join('')}
        </nav>
        <div class="sidebar-footer">
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
