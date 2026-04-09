let isGuest = false;
let guestUsername = '';
let currentUsername = '';
let currentName = '';
const originalTitle = document.title;
let titleFlashTimer = null;
let isFlashing = false;

fetch('/api/current-user')
    .then(res => res.json())
    .then(user => {
        if (user) {
            document.getElementById('user').innerText = user.name;
            if (user.role === 'ROLE_GUEST' || user.name.startsWith('游客_')) {
                isGuest = true;
                guestUsername = user.username;
                document.getElementById('guestBadge').classList.remove('hidden');
                document.getElementById('loginBtn').classList.remove('hidden');
                document.getElementById('logoutForm').classList.add('hidden');
                document.getElementById('adminBtn').classList.add('hidden');

                document.getElementById('userLink').removeAttribute('href');
                document.getElementById('userLink').classList.remove('hover:text-blue-600', 'cursor-pointer');
                document.getElementById('userLink').classList.add('cursor-default');
                document.getElementById('userLink').onclick = function(e) {
                    e.preventDefault();
                };
            } else {
                document.getElementById('guestBadge').classList.add('hidden');
                document.getElementById('loginBtn').classList.add('hidden');
                document.getElementById('logoutForm').classList.remove('hidden');

                if (user.role === 'ROLE_ADMIN') {
                    document.getElementById('adminBtn').classList.remove('hidden');
                } else {
                    document.getElementById('adminBtn').classList.add('hidden');
                }
            }
        }
    });

fetch('/api/current-user-info')
    .then(res => res.json())
    .then(info => {
        currentUsername = info.username || '';
        currentName = info.name || '';
        isGuest = info.isGuest || false;

        document.getElementById('fileUploadIcon').textContent = '🖼️';
        document.getElementById('fileUploadBtn').title = '发送图片（仅支持图片）';
        document.getElementById('fileInput').accept = 'image/*';

        restorePrivateChats();
        loadMentionableUsers();
        initWebSocket();
        connectWebSocket();
    });

function send() {
    const content = document.getElementById('content').value;
    if (content.trim()) {
        if (connectionState !== 'connected' || !stompClient || !stompClient.connected) {
            showSendError('连接已断开，请刷新页面重新发送');
            return;
        }

        hideAtUserPopup();

        const sendContent = content.trim();
        const textarea = document.getElementById('content');

        if (currentPrivateChat) {
            stompClient.send('/app/private.message', {}, JSON.stringify({
                receiver: currentPrivateChat,
                content: sendContent
            }));

            if (!privateChats[currentPrivateChat]) {
                privateChats[currentPrivateChat] = { name: currentPrivateChat, messages: [], unread: 0 };
            }

            const tempMsg = {
                sendTime: new Date().toISOString(),
                sender: currentName || '我',
                receiver: currentPrivateChat,
                message: sendContent
            };
            privateChats[currentPrivateChat].messages.push(tempMsg);
            appendMessage(tempMsg.sendTime, tempMsg.sender, tempMsg.message);
            savePrivateChatsToStorage();
        } else {
            stompClient.send('/app/space', {}, sendContent);
        }

        textarea.value = '';
        textarea.style.height = 'auto';
    }
}
function showSendError(message) {
    const msgArea = document.getElementById('msgArea');
    const errorDiv = document.createElement('div');
    errorDiv.className = 'text-center text-red-500 py-2 text-sm bg-red-50 rounded-md my-2';
    errorDiv.innerHTML = `⚠️ ${message} <button onclick="location.reload()" class="ml-2 px-3 py-1 bg-red-500 text-white rounded hover:bg-red-600 text-xs">刷新页面</button>`;
    msgArea.appendChild(errorDiv);
    msgArea.scrollTop = msgArea.scrollHeight;
}

document.addEventListener('click', function(event) {
    const popup = document.getElementById('onlineUsersPopup');
    const btn = document.getElementById('onlineCountBtn');
    const contextMenu = document.getElementById('contextMenu');
    const atPopup = document.getElementById('atUserPopup');
    const textarea = document.getElementById('content');

    if (!popup.contains(event.target) && !btn.contains(event.target)) {
        popup.classList.add('hidden');
    }
    if (!contextMenu.contains(event.target)) {
        contextMenu.style.display = 'none';
    }
    // 在多选模式下，点击外部不隐藏@列表
    if (atPopupVisible && !atPopup.contains(event.target) && typeof atMultiSelectMode !== 'undefined' && !atMultiSelectMode) {
        hideAtUserPopup(true);
    }
});


// 开始闪烁
function startTitleFlash() {
    if (isFlashing) return; // 防止重复启动
    isFlashing = true;
    let showMsg = true;

    titleFlashTimer = setInterval(() => {
        document.title = showMsg ? '【新消息】TraumSpace' : originalTitle;
        showMsg = !showMsg;
    }, 1000); // 每 1 秒切换一次
}

// 停止闪烁
function stopTitleFlash() {
    if (!isFlashing) return;
    isFlashing = false;
    clearInterval(titleFlashTimer);
    document.title = originalTitle;
}

// 监听页面激活事件：当用户切回标签页或点击窗口时，立即停止闪烁
window.addEventListener('focus', stopTitleFlash);
document.addEventListener('visibilitychange', () => {
    if (!document.hidden) {
        stopTitleFlash();
    }
});

// 监听窗口大小变化，用于适配移动端浏览器地址栏显示/隐藏和重新计算@列表位置
let lastHeight = window.innerHeight;
window.addEventListener('resize', function() {
    // 调整主容器高度
    const mainElement = document.querySelector('main');
    if (mainElement) {
        mainElement.style.height = `calc(100vh - 56px)`;
    }
    
    // 如果@列表可见，重新计算其位置
    if (typeof atPopupVisible !== 'undefined' && atPopupVisible) {
        const textarea = document.getElementById('content');
        if (textarea) {
            const cursorPos = textarea.selectionStart;
            const textBeforeCursor = textarea.value.substring(0, cursorPos);
            const lastAtPos = textBeforeCursor.lastIndexOf('@');
            if (lastAtPos !== -1) {
                const searchText = textBeforeCursor.substring(lastAtPos + 1);
                if (typeof showAtUserPopup === 'function') {
                    showAtUserPopup(searchText, cursorPos, lastAtPos);
                }
            }
        }
    }
    
    // 检测移动端浏览器地址栏显示/隐藏
    const currentHeight = window.innerHeight;
    // 如果高度变化超过20px，可能是地址栏显示/隐藏
    if (Math.abs(currentHeight - lastHeight) > 20) {
        lastHeight = currentHeight;
    }
});