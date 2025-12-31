let currentLang = localStorage.getItem('selectedLang') || 'en';
const languages = ['en', 'am', 'zh', 'es'];
const langLabels = { en: 'English', am: 'አማርኛ', zh: '中文', es: 'Español' };

function toggleLanguageDropdown() {
    const dropdown = document.getElementById('langDropdown');
    dropdown.style.display = dropdown.style.display === 'block' ? 'none' : 'block';
}

function selectLanguage(lang) {
    setLanguage(lang);
    document.getElementById('langDropdown').style.display = 'none';
}

function switchLanguage() {
    toggleLanguageDropdown();
}

function setLanguage(lang) {
    // Hide all language elements
    languages.forEach(l => {
        document.querySelectorAll('[id$="-' + l + '"]').forEach(el => el.style.display = 'none');
    });
    
    // Show selected language elements
    document.querySelectorAll('[id$="-' + lang + '"]').forEach(el => el.style.display = 'inline');
    
    currentLang = lang;
    localStorage.setItem('selectedLang', lang);
    const toggle = document.getElementById('langToggle');
    if (toggle) toggle.textContent = langLabels[currentLang];
}

document.addEventListener('DOMContentLoaded', function() {
    setLanguage(currentLang);
    
    // Create dropdown if it doesn't exist
    if (!document.getElementById('langDropdown')) {
        const dropdown = document.createElement('div');
        dropdown.id = 'langDropdown';
        dropdown.style.cssText = 'position:fixed;top:60px;right:20px;background:#fff;border:1px solid #ccc;border-radius:8px;box-shadow:0 4px 8px rgba(0,0,0,0.2);display:none;z-index:1001;min-width:120px';
        
        languages.forEach(lang => {
            const item = document.createElement('div');
            item.textContent = langLabels[lang];
            item.style.cssText = 'padding:10px 15px;cursor:pointer;color:#333;border-bottom:1px solid #eee';
            item.onmouseover = () => item.style.backgroundColor = '#f5f5f5';
            item.onmouseout = () => item.style.backgroundColor = 'transparent';
            item.onclick = () => selectLanguage(lang);
            dropdown.appendChild(item);
        });
        
        document.body.appendChild(dropdown);
    }
    
    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        const dropdown = document.getElementById('langDropdown');
        const toggle = document.getElementById('langToggle');
        if (dropdown && !dropdown.contains(e.target) && e.target !== toggle) {
            dropdown.style.display = 'none';
        }
    });
});