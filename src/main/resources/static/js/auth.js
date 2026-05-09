const AUTH_TOKEN_KEY = 'traum_auth_token';

function getToken() {
    return localStorage.getItem(AUTH_TOKEN_KEY);
}

function setToken(token) {
    localStorage.setItem(AUTH_TOKEN_KEY, token);
}

function removeToken() {
    localStorage.removeItem(AUTH_TOKEN_KEY);
}

function isLoggedIn() {
    return !!getToken();
}

function logout() {
    fetch('/api/logout', {
        method: 'POST',
        headers: {
            'Authorization': 'Bearer ' + (getToken() || '')
        }
    }).finally(() => {
        removeToken();
        window.location.href = '/login';
    });
}

// 拦截 fetch，自动添加 Authorization 头
const originalFetch = window.fetch;
window.fetch = function(url, options) {
    options = options || {};
    options.headers = options.headers || {};
    if (typeof options.headers === 'object' && !(options.headers instanceof Headers)) {
        const token = getToken();
        if (token && !options.headers['Authorization'] && !options.headers['authorization']) {
            options.headers['Authorization'] = 'Bearer ' + token;
        }
    } else if (options.headers instanceof Headers) {
        const token = getToken();
        if (token && !options.headers.has('Authorization')) {
            options.headers.set('Authorization', 'Bearer ' + token);
        }
    }
    return originalFetch(url, options);
};

// 拦截 XMLHttpRequest，自动添加 Authorization 头
const originalXhrOpen = XMLHttpRequest.prototype.open;
const originalXhrSend = XMLHttpRequest.prototype.send;

XMLHttpRequest.prototype.open = function(method, url, async, user, password) {
    this._requestUrl = url;
    this._requestMethod = method;
    return originalXhrOpen.call(this, method, url, async, user, password);
};

XMLHttpRequest.prototype.send = function(body) {
    const token = getToken();
    if (token && this._requestUrl && !this._requestUrl.startsWith('http')) {
        this.setRequestHeader('Authorization', 'Bearer ' + token);
    }
    return originalXhrSend.call(this, body);
};

// 全局登出按钮事件绑定
document.addEventListener('DOMContentLoaded', function() {
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', function(e) {
            e.preventDefault();
            logout();
        });
    }
});
