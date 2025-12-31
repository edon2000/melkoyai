/**
 * Video Validation System
 * Ensures all videos meet minimum duration requirement (8 seconds)
 */

(function() {
    'use strict';
    
    const MIN_VIDEO_DURATION = 8; // Minimum 8 seconds
    
    /**
     * Validate video duration
     * @param {HTMLVideoElement} video - Video element to validate
     * @returns {Promise<boolean>} - True if video meets requirements
     */
    function validateVideoDuration(video) {
        return new Promise((resolve, reject) => {
            if (!video || !video.src) {
                reject(new Error('Invalid video element'));
                return;
            }
            
            // Check if duration is already loaded
            if (video.readyState >= 2) { // HAVE_CURRENT_DATA
                const duration = video.duration;
                if (isNaN(duration) || duration < MIN_VIDEO_DURATION) {
                    console.warn(`Video ${video.src} duration (${duration}s) is less than minimum ${MIN_VIDEO_DURATION}s`);
                    resolve(false);
                } else {
                    resolve(true);
                }
            } else {
                // Wait for metadata to load
                const handleLoadedMetadata = () => {
                    const duration = video.duration;
                    video.removeEventListener('loadedmetadata', handleLoadedMetadata);
                    
                    if (isNaN(duration) || duration < MIN_VIDEO_DURATION) {
                        console.warn(`Video ${video.src} duration (${duration}s) is less than minimum ${MIN_VIDEO_DURATION}s`);
                        resolve(false);
                    } else {
                        resolve(true);
                    }
                };
                
                video.addEventListener('loadedmetadata', handleLoadedMetadata);
                
                // Timeout after 5 seconds
                setTimeout(() => {
                    video.removeEventListener('loadedmetadata', handleLoadedMetadata);
                    console.warn(`Video ${video.src} metadata loading timeout`);
                    resolve(false);
                }, 5000);
            }
        });
    }
    
    /**
     * Validate all videos on the page
     * Validates both slider videos and standalone videos
     */
    function validateAllVideos() {
        const videos = document.querySelectorAll('video');
        const validationPromises = [];
        
        videos.forEach((video, index) => {
            const promise = validateVideoDuration(video).then(isValid => {
                if (!isValid) {
                    // Add visual indicator for invalid videos
                    video.style.opacity = '0.7';
                    video.style.border = '2px solid #ff6b6b';
                    
                    // Create warning badge
                    const warning = document.createElement('div');
                    warning.style.cssText = `
                        position: absolute;
                        top: 5px;
                        right: 5px;
                        background: rgba(255, 107, 107, 0.9);
                        color: white;
                        padding: 4px 8px;
                        border-radius: 4px;
                        font-size: 10px;
                        font-weight: bold;
                        z-index: 1000;
                        pointer-events: none;
                    `;
                    warning.textContent = `Min 8s`;
                    
                    const container = video.parentElement;
                    if (container && container.style.position !== 'absolute' && container.style.position !== 'relative') {
                        container.style.position = 'relative';
                    }
                    container.appendChild(warning);
                }
                return isValid;
            }).catch(error => {
                console.error(`Error validating video ${index}:`, error);
                return false;
            });
            
            validationPromises.push(promise);
        });
        
        return Promise.all(validationPromises);
    }
    
    /**
     * Initialize video validation when DOM is ready
     */
    function initVideoValidation() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => {
                setTimeout(validateAllVideos, 1000); // Wait 1 second for videos to start loading
            });
        } else {
            setTimeout(validateAllVideos, 1000);
        }
    }
    
    // Initialize on load
    initVideoValidation();
    
    // Export for manual validation if needed
    window.validateVideos = validateAllVideos;
    
})();

