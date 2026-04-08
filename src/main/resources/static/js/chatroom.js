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
        loadMentionableUsers().then(() => {
            initWebSocket();
            connectWebSocket();
        });
    });

function send() {
    const content = document.getElementById('content').value;
    if (content.trim()) {
        if (connectionState !== 'connected' || !stompClient || !stompClient.connected) {
            alert('连接已断开，请刷新页面尝试重新连接....');
            return;
        }

        hideAtUserPopup();

        if (currentPrivateChat) {
            stompClient.send('/app/private.message', {}, JSON.stringify({
                receiver: currentPrivateChat,
                content: content
            }));

            if (!privateChats[currentPrivateChat]) {
                privateChats[currentPrivateChat] = { name: currentPrivateChat, messages: [], unread: 0 };
            }

            const tempMsg = {
                sendTime: new Date().toISOString(),
                sender: currentName || '我',
                receiver: currentPrivateChat,
                message: content
            };
            privateChats[currentPrivateChat].messages.push(tempMsg);
            appendMessage(tempMsg.sendTime, tempMsg.sender, tempMsg.message);
            savePrivateChatsToStorage();
        } else {
            stompClient.send('/app/ChatRoom', {}, content);
        }

        const textarea = document.getElementById('content');
        textarea.value = '';
        textarea.style.height = 'auto';
    }
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
    if (atPopupVisible && !atPopup.contains(event.target)) {
        hideAtUserPopup(true);
    }
});


// 开始闪烁
function startTitleFlash() {
    if (isFlashing) return; // 防止重复启动
    isFlashing = true;
    let showMsg = true;

    titleFlashTimer = setInterval(() => {
        document.title = showMsg ? '【新消息】TraumChatRoom' : originalTitle;
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