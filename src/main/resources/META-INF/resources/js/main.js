// Navigation Active State Management
function setActiveNavItem() {
    const currentPath = window.location.pathname;
    const navItems = document.querySelectorAll('.nav-item');
    
    navItems.forEach(item => {
        item.classList.remove('active');
        const link = item.querySelector('a');
        if (link && link.getAttribute('href') === currentPath) {
            item.classList.add('active');
        }
    });
}

// Navigation Click Effects
function addNavClickEffects() {
    const navItems = document.querySelectorAll('.nav-item');
    
    navItems.forEach(item => {
        item.addEventListener('click', function(e) {
            navItems.forEach(nav => nav.classList.remove('active'));
            this.classList.add('active');
            
            const ripple = document.createElement('div');
            ripple.style.cssText = `
                position: absolute;
                border-radius: 50%;
                background: rgba(79, 195, 247, 0.6);
                transform: scale(0);
                animation: ripple 0.6s linear;
                pointer-events: none;
            `;
            
            const rect = this.getBoundingClientRect();
            const size = Math.max(rect.width, rect.height);
            ripple.style.width = ripple.style.height = size + 'px';
            ripple.style.left = (e.clientX - rect.left - size / 2) + 'px';
            ripple.style.top = (e.clientY - rect.top - size / 2) + 'px';
            
            this.appendChild(ripple);
            setTimeout(() => ripple.remove(), 600);
        });
    });
}

// Slider functionality
let currentSlide = 0;
let sliderInterval;

function initSlider() {
    const sliderTrack = document.getElementById('sliderTrack');
    const slides = document.querySelectorAll('.slide');
    const totalSlides = slides.length;
    
    if (!sliderTrack || totalSlides === 0) return;
    
    // Start first slide immediately (0 seconds)
    sliderTrack.style.transform = `translateX(0%)`;
    
            function nextSlide() {
                // Pause all videos first
                const allVideos = document.querySelectorAll('.slider-video');
                allVideos.forEach(video => {
                    try {
                        video.pause();
                    } catch (e) {
                        // Ignore pause errors
                    }
                });
                
                currentSlide = (currentSlide + 1) % totalSlides;
                const translateX = -(currentSlide * (100 / totalSlides));
                sliderTrack.style.transform = `translateX(${translateX}%)`;
                
                // Play current video after transition
                setTimeout(() => {
                    const currentVideo = slides[currentSlide].querySelector('.slider-video');
                    if (currentVideo) {
                        currentVideo.currentTime = 0; // Reset to start
                        currentVideo.play().catch(e => {
                            // Ignore autoplay errors
                        });
                    }
                }, 500);
            }
    
    // First slide shows immediately, then 1.5s intervals
    sliderInterval = setInterval(nextSlide, 1500);
}

// History scrolling functionality
let historyCurrentIndex = 0;
let historyInterval;

function initHistoryScroll() {
    const historyContainer = document.getElementById('scrollingHistory');
    const historyItems = document.querySelectorAll('.history-item');
    const totalItems = historyItems.length;
    
    if (!historyContainer || totalItems === 0) return;
    
    function scrollHistory() {
        historyCurrentIndex = (historyCurrentIndex + 1) % totalItems;
        const translateY = -(historyCurrentIndex * 45); // 45px per item
        historyContainer.style.transform = `translateY(${translateY}px)`;
    }
    
    // Slow scroll - 10 times slower (12 seconds instead of 1.2s)
    historyInterval = setInterval(scrollHistory, 12000);
}

// Initialize everything when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    setActiveNavItem();
    addNavClickEffects();
    initSlider();
    initHistoryScroll();
    
    // Load and initialize icon system
    if (typeof replaceGenericIcons === 'function') {
        replaceGenericIcons();
    } else {
        // Fallback: Load icons.js if not already loaded
        const script = document.createElement('script');
        script.src = '/js/icons.js';
        script.onload = function() {
            if (typeof replaceGenericIcons === 'function') {
                replaceGenericIcons();
            }
        };
        document.head.appendChild(script);
    }
});

// Clean up intervals when page unloads
window.addEventListener('beforeunload', function() {
    if (sliderInterval) clearInterval(sliderInterval);
    if (historyInterval) clearInterval(historyInterval);
});

// Copy to clipboard function
function copyToClipboard(text) {
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(() => {
            showCopySuccess();
        }).catch(err => {
            fallbackCopyTextToClipboard(text);
        });
    } else {
        fallbackCopyTextToClipboard(text);
    }
}

function fallbackCopyTextToClipboard(text) {
    const textArea = document.createElement("textarea");
    textArea.value = text;
    textArea.style.top = "0";
    textArea.style.left = "0";
    textArea.style.position = "fixed";
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    try {
        document.execCommand('copy');
        showCopySuccess();
    } catch (err) {
        console.error('Fallback: Could not copy text: ', err);
    }
    document.body.removeChild(textArea);
}

function showCopySuccess() {
    const button = document.activeElement || document.querySelector('button[onclick*="copyToClipboard"]');
    if (button) {
        const originalText = button.innerHTML;
        button.innerHTML = '<svg class="icon" viewBox="0 0 16 16"><path d="M10.97 4.97a.235.235 0 0 0-.02.022L7.477 9.417 5.384 7.323a.75.75 0 0 0-1.06 1.061L6.97 11.03a.75.75 0 0 0 1.079-.02l3.992-4.99a.75.75 0 0 0-1.071-1.05z"/></svg> Copied!';
        button.style.backgroundColor = '#4caf50';
        setTimeout(() => {
            button.innerHTML = originalText;
            button.style.backgroundColor = '#4fc3f7';
        }, 2000);
    }
}

// Add CSS for ripple animation
const style = document.createElement('style');
style.textContent = `
    @keyframes ripple {
        to {
            transform: scale(4);
            opacity: 0;
        }
    }
`;
document.head.appendChild(style);