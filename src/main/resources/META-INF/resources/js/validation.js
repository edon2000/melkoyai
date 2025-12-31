// Enhanced Form Validation and Error Handling
class FormValidator {
    constructor() {
        this.rules = {};
        this.messages = {};
        this.init();
    }
    
    init() {
        // Add validation to all forms
        document.addEventListener('DOMContentLoaded', () => {
            this.setupFormValidation();
            this.setupLoadingStates();
            this.setupErrorHandling();
        });
    }
    
    setupFormValidation() {
        const forms = document.querySelectorAll('form');
        forms.forEach(form => {
            form.addEventListener('submit', (e) => {
                if (!this.validateForm(form)) {
                    e.preventDefault();
                    return false;
                }
                this.showLoading(form);
            });
            
            // Real-time validation
            const inputs = form.querySelectorAll('input, select, textarea');
            inputs.forEach(input => {
                input.addEventListener('blur', () => this.validateField(input));
                input.addEventListener('input', () => this.clearFieldError(input));
            });
        });
    }
    
    validateForm(form) {
        let isValid = true;
        const inputs = form.querySelectorAll('input[required], select[required], textarea[required]');
        
        inputs.forEach(input => {
            if (!this.validateField(input)) {
                isValid = false;
            }
        });
        
        return isValid;
    }
    
    validateField(field) {
        const value = field.value.trim();
        const type = field.type;
        const name = field.name;
        
        // Clear previous errors
        this.clearFieldError(field);
        
        // Required validation
        if (field.hasAttribute('required') && !value) {
            this.showFieldError(field, 'This field is required');
            return false;
        }
        
        // Type-specific validation
        switch (type) {
            case 'email':
                if (value && !this.isValidEmail(value)) {
                    this.showFieldError(field, 'Please enter a valid email address');
                    return false;
                }
                break;
                
            case 'tel':
                if (value && !this.isValidPhone(value)) {
                    this.showFieldError(field, 'Please enter a valid phone number');
                    return false;
                }
                break;
                
            case 'password':
                if (value && !this.isValidPassword(value)) {
                    this.showFieldError(field, 'Password must be at least 8 characters with letters and numbers');
                    return false;
                }
                break;
                
            case 'number':
                if (value && isNaN(value)) {
                    this.showFieldError(field, 'Please enter a valid number');
                    return false;
                }
                break;
        }
        
        // Custom validation rules
        if (name === 'amount' && value) {
            const amount = parseFloat(value);
            if (amount <= 0) {
                this.showFieldError(field, 'Amount must be greater than 0');
                return false;
            }
        }
        
        this.showFieldSuccess(field);
        return true;
    }
    
    isValidEmail(email) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email);
    }
    
    isValidPhone(phone) {
        const phoneRegex = /^\+?[\d\s\-\(\)]{10,}$/;
        return phoneRegex.test(phone);
    }
    
    isValidPassword(password) {
        return password.length >= 8 && /[A-Za-z]/.test(password) && /\d/.test(password);
    }
    
    showFieldError(field, message) {
        field.classList.add('error');
        field.classList.remove('success');
        
        let errorElement = field.parentNode.querySelector('.error-message');
        if (!errorElement) {
            errorElement = document.createElement('span');
            errorElement.className = 'error-message';
            field.parentNode.appendChild(errorElement);
        }
        errorElement.textContent = message;
    }
    
    showFieldSuccess(field) {
        field.classList.add('success');
        field.classList.remove('error');
        this.clearFieldError(field);
    }
    
    clearFieldError(field) {
        field.classList.remove('error');
        const errorElement = field.parentNode.querySelector('.error-message');
        if (errorElement) {
            errorElement.remove();
        }
    }
    
    setupLoadingStates() {
        document.addEventListener('click', (e) => {
            if (e.target.type === 'submit' || e.target.classList.contains('btn-submit')) {
                this.showButtonLoading(e.target);
            }
        });
    }
    
    showLoading(form) {
        const submitBtn = form.querySelector('button[type="submit"], input[type="submit"]');
        if (submitBtn) {
            this.showButtonLoading(submitBtn);
        }
    }
    
    showButtonLoading(button) {
        button.classList.add('btn-loading');
        button.disabled = true;
        const originalText = button.textContent;
        button.dataset.originalText = originalText;
        button.textContent = 'Loading...';
        
        // Auto-restore after 10 seconds (fallback)
        setTimeout(() => {
            this.hideButtonLoading(button);
        }, 10000);
    }
    
    hideButtonLoading(button) {
        button.classList.remove('btn-loading');
        button.disabled = false;
        if (button.dataset.originalText) {
            button.textContent = button.dataset.originalText;
        }
    }
    
    setupErrorHandling() {
        // Global error handler
        window.addEventListener('error', (e) => {
            console.error('JavaScript Error:', e.error);
            this.showToast('An unexpected error occurred. Please try again.', 'error');
        });
        
        // Unhandled promise rejection handler
        window.addEventListener('unhandledrejection', (e) => {
            console.error('Unhandled Promise Rejection:', e.reason);
            this.showToast('An error occurred while processing your request.', 'error');
        });
    }
    
    showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `${type}-toast`;
        toast.textContent = message;
        
        document.body.appendChild(toast);
        
        // Auto-remove after 5 seconds
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 5000);
        
        // Allow manual close
        toast.addEventListener('click', () => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        });
    }
}

// CSRF Token Management
class CSRFManager {
    constructor() {
        this.token = null;
        this.init();
    }
    
    init() {
        this.token = this.getCSRFToken();
        this.addCSRFToForms();
    }
    
    getCSRFToken() {
        const metaTag = document.querySelector('meta[name="csrf-token"]');
        return metaTag ? metaTag.getAttribute('content') : null;
    }
    
    addCSRFToForms() {
        const forms = document.querySelectorAll('form');
        forms.forEach(form => {
            if (form.method.toLowerCase() === 'post' && this.token) {
                let csrfInput = form.querySelector('input[name="csrf-token"]');
                if (!csrfInput) {
                    csrfInput = document.createElement('input');
                    csrfInput.type = 'hidden';
                    csrfInput.name = 'csrf-token';
                    csrfInput.value = this.token;
                    form.appendChild(csrfInput);
                }
            }
        });
    }
}

// Rate Limiting
class RateLimiter {
    constructor() {
        this.requests = new Map();
        this.limits = {
            login: { max: 5, window: 300000 }, // 5 attempts per 5 minutes
            signup: { max: 3, window: 600000 }, // 3 attempts per 10 minutes
            default: { max: 10, window: 60000 } // 10 requests per minute
        };
    }
    
    canMakeRequest(action = 'default') {
        const now = Date.now();
        const limit = this.limits[action] || this.limits.default;
        
        if (!this.requests.has(action)) {
            this.requests.set(action, []);
        }
        
        const actionRequests = this.requests.get(action);
        
        // Remove old requests outside the window
        const validRequests = actionRequests.filter(time => now - time < limit.window);
        this.requests.set(action, validRequests);
        
        if (validRequests.length >= limit.max) {
            return false;
        }
        
        validRequests.push(now);
        return true;
    }
    
    getRemainingTime(action = 'default') {
        const limit = this.limits[action] || this.limits.default;
        const actionRequests = this.requests.get(action) || [];
        
        if (actionRequests.length === 0) return 0;
        
        const oldestRequest = Math.min(...actionRequests);
        const remainingTime = limit.window - (Date.now() - oldestRequest);
        
        return Math.max(0, remainingTime);
    }
}

// Initialize all components
const formValidator = new FormValidator();
const csrfManager = new CSRFManager();
const rateLimiter = new RateLimiter();

// Export for global use
window.FormValidator = FormValidator;
window.CSRFManager = CSRFManager;
window.RateLimiter = RateLimiter;