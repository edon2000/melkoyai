// Navigation Interactive Effects
document.addEventListener('DOMContentLoaded', function() {
    const navItems = document.querySelectorAll('.nav-item');
    const currentPath = window.location.pathname;
    
    // Set active navigation item based on current page
    navItems.forEach(item => {
        const link = item.querySelector('a');
        if (link && link.getAttribute('href') === currentPath) {
            item.classList.add('active');
        }
        
        // Add click effects
        item.addEventListener('click', function(e) {
            // Remove active from all items
            navItems.forEach(nav => nav.classList.remove('active'));
            
            // Add active to clicked item
            this.classList.add('active');
            
            // Add bounce animation
            this.classList.add('clicked');
            setTimeout(() => {
                this.classList.remove('clicked');
            }, 600);
        });
        
        // Touch feedback for mobile
        item.addEventListener('touchstart', function() {
            this.style.transform = 'scale(0.95)';
        });
        
        item.addEventListener('touchend', function() {
            this.style.transform = '';
        });
    });
});