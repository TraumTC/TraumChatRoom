
let isGuest = false;
let guestUsername = '';
let currentUsername = '';
let currentName = '';
let currentPrivateChat = null;
let currentPrivateChatName = '';
let privateChats = {};
let reconnectAttempts = 0;
const maxReconnectAttempts = 10;
const pageLoadTime = Date.now();
const SILENT_PERIOD = 10000;
let isPageVisible = true;
let connectionState = 'disconnected';
let heartbeatInterval = null;
let manualDisconnect = false;
let stompClient = null;
let atPopupVisible = false;
let atPopupTarget = null;
let atSelectedIndex = 0;
let onlineUsersCache = [];
let atMultiSelectMode = false;
let atSelectedUsers = [];
let atOriginalCursorPos = 0;
let atOriginalAtPos = 0;
function formatTime(timeStr) {
    if (!timeStr) return '';
    const date = new Date(timeStr);
    if (isNaN(date.getTime())) return timeStr;
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

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
        initWebSocket();
        connectWebSocket();
    });

function initWebSocket() {
    const transports = ['websocket'];
    const sock = new SockJS('/ws', null, {
        withCredentials: true,
        transports: transports
    });
    stompClient = Stomp.over(sock);
    stompClient.heartbeat.outgoing = 10000;
    stompClient.heartbeat.incoming = 10000;
}

function startHeartbeat() {
    stopHeartbeat();
    heartbeatInterval = setInterval(() => {
        if (connectionState === 'connected' && isPageVisible && stompClient && stompClient.connected) {
            try {
                stompClient.send('/app/heartbeat', {}, '');
            } catch (e) {
                handleConnectionLost();
            }
        }
    }, 8000);
}

function stopHeartbeat() {
    if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
        heartbeatInterval = null;
    }
}

function handleVisibilityChange() {
    if (document.hidden) {
        isPageVisible = false;
        stopHeartbeat();
    } else {
        isPageVisible = true;

        setTimeout(() => {
            if (connectionState !== 'connected') {
                reconnectAttempts = 0;
                manualDisconnect = false;
                connectWebSocket();
            } else {
                startHeartbeat();
                syncConnectionState();
            }
        }, 1000);
    }
}

function syncConnectionState() {
    if (connectionState === 'connected' && stompClient && stompClient.connected) {
        try {
            stompClient.send('/app/sync-state', {}, JSON.stringify({
                username: currentUsername,
                timestamp: Date.now()
            }));
        } catch (e) {
            handleConnectionLost();
        }
    }
}

function handleConnectionLost() {
    connectionState = 'disconnected';
    stopHeartbeat();

    if (!manualDisconnect) {
        setTimeout(() => {
            if (connectionState !== 'connected' && !manualDisconnect) {
                reconnectAttempts = 0;
                connectWebSocket();
            }
        }, 2000);
    }
}

document.addEventListener('visibilitychange', handleVisibilityChange);

window.addEventListener('beforeunload', function() {
    manualDisconnect = true;
    stopHeartbeat();
    if (stompClient && stompClient.connected) {
        try {
            stompClient.disconnect();
        } catch (e) {
        }
    }
});

window.addEventListener('focus', function() {
    if (connectionState !== 'connected' && !manualDisconnect) {
        reconnectAttempts = 0;
        manualDisconnect = false;
        connectWebSocket();
    }
});

window.addEventListener('online', function() {
    if (connectionState !== 'connected' && !manualDisconnect) {
        reconnectAttempts = 0;
        connectWebSocket();
    }
});

window.addEventListener('offline', function() {
    handleConnectionLost();
});

function connectWebSocket() {
    if (!stompClient) {
        initWebSocket();
    }

    if (connectionState === 'connecting') {
        return;
    }

    connectionState = 'connecting';
    manualDisconnect = false;

    stompClient.connect({}, function () {
        reconnectAttempts = 0;
        connectionState = 'connected';

        startHeartbeat();

        stompClient.subscribe('/topic/messages', function (res) {
            if (!currentPrivateChat) {
                const msg = JSON.parse(res.body);
                if (msg.messageType === 'file' || msg.messageType === 'image') {
                    appendFileMessage(msg);
                } else {
                    appendMessage(msg.sendTime, msg.sender, msg.message);
                }
            }
        });

        stompClient.subscribe('/user/queue/private-messages', function (res) {
            const msg = JSON.parse(res.body);
            handlePrivateMessage(msg);
        });

        stompClient.subscribe('/topic/private-notifications', function (res) {
            const notification = JSON.parse(res.body);
            handlePrivateNotification(notification);
        });

        stompClient.subscribe('/topic/onlineUsers', function (res) {
            const data = JSON.parse(res.body);
            if (Array.isArray(data)) {
                updateOnlineUsersList(data);
            } else if (data && data.onlineUsers) {
                updateOnlineUsersList(data.onlineUsers);
            } else {
                updateOnlineUsersList([]);
            }
        });

        loadPublicHistory();

        fetch('/api/online-users').then(r => r.json()).then(data => {
            if (data && data.onlineUsers) {
                updateOnlineUsersList(data.onlineUsers);
            } else if (Array.isArray(data)) {
                updateOnlineUsersList(data);
            } else {
                updateOnlineUsersList([]);
            }
        });
    }, function(error) {
        connectionState = 'disconnected';
        stopHeartbeat();

        if (!manualDisconnect && reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++;
            const delay = Math.min(3000 * reconnectAttempts, 30000);
            setTimeout(connectWebSocket, delay);
        } else if (!manualDisconnect) {
            alert('WebSocket连接失败，请刷新页面重试');
        }
    });
}

function handlePrivateNotification(notification) {
    const timeSinceLoad = Date.now() - pageLoadTime;
    const isSilentPeriod = timeSinceLoad < SILENT_PERIOD;

    const notificationName = notification.sender;
    const tab = document.getElementById(`tab-${notificationName}`);

    if (notification.type === 'user_offline') {
        if (tab) {
            tab.style.opacity = '0.5';
            tab.classList.add('user-offline');
        }
        if (!isSilentPeriod && privateChats[notificationName]) {
            if (currentPrivateChat === notificationName) {
                appendSystemMessage(`${notificationName} 已下线`);
            } else {
                privateChats[notificationName].unread++;
                updateTabUnread(notificationName);
            }
            const tabContent = tab?.querySelector('span');
            if (tabContent && privateChats[notificationName].unread === 0) {
                tabContent.textContent = `🔒 ${notificationName} (离线)`;
            }
        }
    } else if (notification.type === 'user_online') {
        if (tab) {
            tab.style.opacity = '1';
            tab.classList.remove('user-offline');
            const tabContent = tab.querySelector('span');
            if (tabContent) {
                const unreadCount = privateChats[notificationName]?.unread || 0;
                if (unreadCount > 0) {
                    tabContent.textContent = `🔒 ${notificationName} (${unreadCount})`;
                } else {
                    tabContent.textContent = `🔒 ${notificationName}`;
                }
            }
        }
        if (!isSilentPeriod && currentPrivateChat === notificationName) {
            appendSystemMessage(`${notificationName} 已上线`);
        }
    }
}

function appendSystemMessage(message) {
    const time = formatTime(new Date().toISOString());
    const msgDiv = document.createElement('div');
    msgDiv.className = 'system-message';
    msgDiv.textContent = `[${time}] ${message}`;
    document.getElementById('msgArea').appendChild(msgDiv);
    document.getElementById('msgArea').scrollTop = document.getElementById('msgArea').scrollHeight;
}

function handlePrivateMessage(msg) {
    const sender = msg.sender;
    if (sender === currentName) return;

    if (!privateChats[sender]) {
        privateChats[sender] = { name: sender, messages: [], unread: 0 };
        createPrivateChatTab(sender);
    }

    privateChats[sender].messages.push(msg);

    if (currentPrivateChat === sender) {
        if (msg.messageType === 'file' || msg.messageType === 'image') {
            appendFileMessage(msg);
        } else {
            appendMessage(msg.sendTime, msg.sender, msg.message);
        }
    } else {
        privateChats[sender].unread++;
        updateTabUnread(sender);
        const tab = document.getElementById(`tab-${sender}`);
        if (tab) tab.style.opacity = '1';
    }
    savePrivateChatsToStorage();
}

function loadPublicHistory() {
    fetch('/history').then(r => r.json()).then(list => {
        if (!list || list.length === 0) {
            const msgArea = document.getElementById('msgArea');
            msgArea.innerHTML = '';
            const emptyDiv = document.createElement('div');
            emptyDiv.className = 'text-center text-gray-400 py-8';
            emptyDiv.textContent = '暂无历史记录';
            msgArea.appendChild(emptyDiv);
            return;
        }
        clearMessages();
        list.forEach(item => {
            if (item.messageType === 'file' || item.messageType === 'image' || (item.filePath && item.fileName)) {
                appendFileMessage(item);
            } else {
                appendMessage(item.sendTime, item.sender, item.message);
            }
        });
    });
}

function loadPrivateHistory(name) {
    if (!name || !privateChats[name]) return;
    fetch(`/api/private-history/${name}`)
        .then(r => r.json())
        .then(list => {
            if (privateChats[name]) {
                privateChats[name].messages = list || [];
            }
            if (currentPrivateChat === name) {
                displayPrivateChatMessages(name);
            }
        })
        .catch(err => {
        });
}

function restorePrivateChats() {
    const savedChats = localStorage.getItem('privateChats');
    if (savedChats) {
        try {
            const chatList = JSON.parse(savedChats);
            if (Array.isArray(chatList)) {
                chatList.forEach(data => {
                    if (data && data.name && data.name !== currentName) {
                        if (isGuest) {
                            return;
                        }

                        const isTargetGuest = data.name.startsWith('游客_');
                        if (isTargetGuest) {
                            return;
                        }

                        if (!privateChats[data.name]) {
                            privateChats[data.name] = { name: data.name, messages: [], unread: 0 };
                            createPrivateChatTab(data.name);
                        }
                    }
                });

                if (!isGuest) {
                    const lastActiveChat = localStorage.getItem('lastActivePrivateChat');
                    if (lastActiveChat && privateChats[lastActiveChat]) {
                        const isLastChatGuest = lastActiveChat.startsWith('游客_');
                        if (!isLastChatGuest) {
                            switchToPrivateChat(lastActiveChat);
                        }
                    }

                    fetch('/api/online-users')
                        .then(r => r.json())
                        .then(data => {
                            const onlineUsers = data.onlineUsers || [];
                            Object.keys(privateChats).forEach(name => {
                                const isOnline = onlineUsers.includes(name);
                                const tab = document.getElementById(`tab-${name}`);
                                if (tab) {
                                    if (isOnline) {
                                        tab.style.opacity = '1';
                                        tab.classList.remove('user-offline');
                                        const tabContent = tab.querySelector('span');
                                        if (tabContent && privateChats[name].unread === 0) {
                                            tabContent.textContent = `🔒 ${name}`;
                                        }
                                    } else {
                                        tab.style.opacity = '0.5';
                                        tab.classList.add('user-offline');
                                        const tabContent = tab.querySelector('span');
                                        if (tabContent && privateChats[name].unread === 0) {
                                            tabContent.textContent = `🔒 ${name} (离线)`;
                                        }
                                    }
                                }
                            });
                        });
                } else {
                    localStorage.removeItem('privateChats');
                    localStorage.removeItem('lastActivePrivateChat');
                }
            }
        } catch (e) {
        }
    }
}

function savePrivateChatsToStorage() {
    const chatList = Object.values(privateChats).map(chat => ({ name: chat.name }));
    localStorage.setItem('privateChats', JSON.stringify(chatList));
    if (currentPrivateChat) {
        localStorage.setItem('lastActivePrivateChat', currentPrivateChat);
    } else {
        localStorage.removeItem('lastActivePrivateChat');
    }
}

function toggleOnlineUsers() {
    const popup = document.getElementById('onlineUsersPopup');
    popup.classList.toggle('hidden');
}

document.addEventListener('click', function(event) {
    const popup = document.getElementById('onlineUsersPopup');
    const btn = document.getElementById('onlineCountBtn');
    const contextMenu = document.getElementById('contextMenu');
    if (!popup.contains(event.target) && !btn.contains(event.target)) {
        popup.classList.add('hidden');
    }
    if (!contextMenu.contains(event.target)) {
        contextMenu.style.display = 'none';
    }
});

function updateOnlineUsersList(users) {
    const onlineUsersList = document.getElementById('onlineUsersList');
    const onlineCountBtn = document.getElementById('onlineCountBtn');
    const count = users && users.length ? users.length : 0;
    onlineCountBtn.textContent = count + '人在线';
    onlineUsersList.innerHTML = '';

    onlineUsersCache = users || [];

    if (!users || users.length === 0) {
        const emptyDiv = document.createElement('div');
        emptyDiv.className = 'text-center text-gray-400 py-6 text-sm';
        emptyDiv.textContent = '暂无在线用户';
        onlineUsersList.appendChild(emptyDiv);
        return;
    }

    users.forEach(name => {
        const isTargetGuest = name.startsWith('游客_');
        const userDiv = document.createElement('div');
        userDiv.className = 'py-2 px-3 border-b border-gray-100 last:border-0 flex items-center hover:bg-gray-50 transition-colors cursor-pointer';
        userDiv.dataset.name = name;

        const dotDiv = document.createElement('div');
        dotDiv.className = 'w-2 h-2 bg-green-500 rounded-full mr-2 flex-shrink-0';
        userDiv.appendChild(dotDiv);

        const nameSpan = document.createElement('span');
        nameSpan.className = 'text-gray-700 text-sm truncate flex-1';
        nameSpan.textContent = name;
        userDiv.appendChild(nameSpan);

        const guestBadge = document.createElement('span');
        if (isTargetGuest) {
            guestBadge.className = 'text-xs text-yellow-600 mr-2';
            guestBadge.textContent = '游客';
            userDiv.appendChild(guestBadge);
        }

        const chatIcon = document.createElement('span');
        if (!isGuest && !isTargetGuest) {
            chatIcon.className = 'text-blue-500 text-sm ml-2';
            chatIcon.innerHTML = '💬';
            userDiv.appendChild(chatIcon);
        }

        userDiv.addEventListener('click', function(e) {
            e.preventDefault();
            if (isGuest) {
                alert('私聊功能仅对登录用户开放，请先登录！');
                return;
            }
            if (isTargetGuest) {
                alert('无法与游客用户进行私聊');
                return;
            }
            if (name === currentName) {
                return;
            }
            openPrivateChat(name);
        });

        onlineUsersList.appendChild(userDiv);
    });
}

function openPrivateChat(name, isRestore = false) {
    if (!privateChats[name]) {
        privateChats[name] = { name: name, messages: [], unread: 0 };
        createPrivateChatTab(name);
    }
    switchToPrivateChat(name);
    if (!isRestore) loadPrivateHistory(name);
    savePrivateChatsToStorage();
}

function createPrivateChatTab(name) {
    const chatTabs = document.getElementById('chatTabs');
    const tab = document.createElement('div');
    tab.id = `tab-${name}`;
    tab.className = 'private-chat-tab px-4 py-2 cursor-pointer border-r text-sm font-medium text-gray-700 flex items-center';
    tab.onclick = () => switchToPrivateChat(name);

    const tabContent = document.createElement('span');
    tabContent.textContent = `🔒 ${name}`;
    tab.appendChild(tabContent);

    const closeBtn = document.createElement('button');
    closeBtn.className = 'ml-2 text-gray-400 hover:text-gray-600';
    closeBtn.innerHTML = '×';
    closeBtn.onclick = (e) => {
        e.stopPropagation();
        closePrivateChatTab(name);
    };
    tab.appendChild(closeBtn);

    chatTabs.appendChild(tab);
    chatTabs.classList.remove('hidden');
}

function switchToPublicChat() {
    currentPrivateChat = null;
    currentPrivateChatName = '';
    document.getElementById('privateChatIndicator').classList.add('hidden');
    document.getElementById('content').placeholder = '输入消息...';
    document.querySelectorAll('.private-chat-tab').forEach(tab => tab.classList.remove('active'));
    document.getElementById('publicChatTab').classList.add('active');
    localStorage.removeItem('lastActivePrivateChat');

    document.getElementById('fileUploadIcon').textContent = '🖼️';
    document.getElementById('fileUploadBtn').title = '发送图片（仅支持图片）';
    document.getElementById('fileInput').accept = 'image/*';

    loadPublicHistory();
}

function switchToPrivateChat(name) {
    currentPrivateChat = name;
    currentPrivateChatName = name;
    document.getElementById('privateChatIndicator').classList.remove('hidden');
    document.getElementById('privateChatWith').textContent = name;
    document.getElementById('content').placeholder = '输入消息...';
    document.querySelectorAll('.private-chat-tab').forEach(tab => tab.classList.remove('active'));
    const targetTab = document.getElementById(`tab-${name}`);
    if (targetTab) {
        targetTab.classList.add('active');
        targetTab.style.opacity = '1';
    }

    document.getElementById('fileUploadIcon').textContent = '📎';
    document.getElementById('fileUploadBtn').title = '发送文件（支持所有文件类型）';
    document.getElementById('fileInput').accept = '';

    if (!privateChats[name] || privateChats[name].messages.length === 0) {
        loadPrivateHistory(name);
    } else {
        displayPrivateChatMessages(name);
    }
    if (privateChats[name]) {
        privateChats[name].unread = 0;
        updateTabUnread(name);
    }
    savePrivateChatsToStorage();
}

function displayPrivateChatMessages(name) {
    clearMessages();
    const messages = privateChats[name]?.messages || [];
    messages.forEach(msg => {
        if (msg.messageType === 'file' || msg.messageType === 'image' || (msg.filePath && msg.fileName)) {
            appendFileMessage(msg);
        } else {
            appendMessage(msg.sendTime, msg.sender, msg.message);
        }
    });
}

function closePrivateChatTab(name) {
    const tab = document.getElementById(`tab-${name}`);
    if (tab) tab.remove();
    delete privateChats[name];
    const remainingTabs = Object.keys(privateChats);
    if (remainingTabs.length === 0) {
        document.getElementById('chatTabs').classList.add('hidden');
    }
    if (currentPrivateChat === name) {
        switchToPublicChat();
    }
    savePrivateChatsToStorage();
}

function closePrivateChat() {
    if (currentPrivateChat) closePrivateChatTab(currentPrivateChat);
}

function updateTabUnread(name) {
    const tab = document.getElementById(`tab-${name}`);
    if (tab && privateChats[name].unread > 0) {
        const unreadCount = privateChats[name].unread;
        let tabContent = tab.querySelector('span');
        if (tabContent) {
            tabContent.textContent = `🔒 ${name} (${unreadCount})`;
        }
    }
}

function clearMessages() {
    const msgArea = document.getElementById('msgArea');
    msgArea.innerHTML = '';
}

function appendMessage(timeStr, sender, message) {
    const time = formatTime(timeStr);
    const isMyMessage = sender === currentName || sender === '我';

    const msgContainer = document.createElement('div');
    msgContainer.className = `message-container ${isMyMessage ? 'message-right' : 'message-left'}`;

    const bubbleDiv = document.createElement('div');
    bubbleDiv.className = 'message-bubble';

    if (!isMyMessage) {
        const senderDiv = document.createElement('div');
        senderDiv.className = 'message-sender';
        senderDiv.textContent = sender;
        bubbleDiv.appendChild(senderDiv);
    }

    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    appendMentionedMessage(contentDiv, message);
    bubbleDiv.appendChild(contentDiv);

    const timeDiv = document.createElement('div');
    timeDiv.className = 'message-time-above';
    timeDiv.textContent = time;

    msgContainer.appendChild(bubbleDiv);
    msgContainer.appendChild(timeDiv);

    document.getElementById('msgArea').appendChild(msgContainer);
    document.getElementById('msgArea').scrollTop = document.getElementById('msgArea').scrollHeight;
}

function appendMentionedMessage(container, message) {
    if (!message) return;

    const mentionRegex = /@([^\s@]+)/g;
    let lastIndex = 0;
    let match;

    while ((match = mentionRegex.exec(message)) !== null) {
        const beforeText = message.substring(lastIndex, match.index);
        const mentionedName = match[1];

        if (beforeText) {
            container.appendChild(document.createTextNode(beforeText));
        }

        const mentionSpan = document.createElement('span');
        mentionSpan.className = 'at-mention';
        mentionSpan.textContent = `@${mentionedName}`;
        mentionSpan.style.cursor = 'pointer';
        mentionSpan.addEventListener('click', () => quickPrivateChat(mentionedName));
        container.appendChild(mentionSpan);

        lastIndex = mentionRegex.lastIndex;
    }

    const remainingText = message.substring(lastIndex);
    if (remainingText) {
        container.appendChild(document.createTextNode(remainingText));
    }
}

function appendFileMessage(msg) {
    const time = formatTime(msg.sendTime);
    const isMyMessage = msg.sender === currentName || msg.sender === '我';

    const msgContainer = document.createElement('div');
    msgContainer.className = `message-container ${isMyMessage ? 'message-right' : 'message-left'}`;

    const bubbleDiv = document.createElement('div');
    bubbleDiv.className = 'message-bubble';

    if (!isMyMessage) {
        const senderDiv = document.createElement('div');
        senderDiv.className = 'message-sender';
        senderDiv.textContent = msg.sender;
        bubbleDiv.appendChild(senderDiv);
    }

    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';

    if (msg.messageType === 'image') {
        const img = document.createElement('img');
        img.src = msg.filePath;
        img.className = 'image-message';
        img.alt = msg.fileName;
        img.onclick = (e) => {
            e.stopPropagation();
            openImagePreview(msg.filePath, msg.fileName);
        };
        contentDiv.appendChild(img);
    }  else {
        const fileDiv = document.createElement('div');
        fileDiv.className = 'file-message';
        fileDiv.onclick = () => confirmDownloadFile(msg);

        const iconSpan = document.createElement('span');
        iconSpan.className = 'file-icon';
        iconSpan.textContent = getFileIcon(msg.fileName);

        const infoDiv = document.createElement('div');
        infoDiv.className = 'file-info';

        const nameSpan = document.createElement('div');
        nameSpan.className = 'file-name';
        nameSpan.textContent = msg.fileName;

        const sizeSpan = document.createElement('div');
        sizeSpan.className = 'file-size';
        sizeSpan.textContent = formatFileSize(msg.fileSize);

        infoDiv.appendChild(nameSpan);
        infoDiv.appendChild(sizeSpan);

        fileDiv.appendChild(iconSpan);
        fileDiv.appendChild(infoDiv);

        contentDiv.appendChild(fileDiv);
    }

    bubbleDiv.appendChild(contentDiv);

    const timeDiv = document.createElement('div');
    timeDiv.className = 'message-time-above';
    timeDiv.textContent = time;

    msgContainer.appendChild(bubbleDiv);
    msgContainer.appendChild(timeDiv);

    document.getElementById('msgArea').appendChild(msgContainer);
    document.getElementById('msgArea').scrollTop = document.getElementById('msgArea').scrollHeight;
}

function confirmDownloadFile(msg) {
    const fileName = msg.fileName || '未知文件';
    const fileSize = formatFileSize(msg.fileSize);
    const isWeChatBrowser = /micromessenger/i.test(navigator.userAgent);

    let message = `是否下载文件？\n\n文件名：${fileName}\n大小：${fileSize}`;
    if (isWeChatBrowser) {
        message += `\n\n提示：微信内无法直接下载，将跳转到浏览器完成下载`;
    }

    if (confirm(message)) {
        downloadFile(msg.filePath, fileName);
    }
}

function downloadFile(fileUrl, fileName) {
    const isWeChatBrowser = /micromessenger/i.test(navigator.userAgent);

    if (isWeChatBrowser) {
        window.location.href = fileUrl;
        return;
    }

    const xhr = new XMLHttpRequest();
    xhr.open('GET', fileUrl);
    xhr.responseType = 'blob';

    xhr.onload = function() {
        if (xhr.status === 200) {
            try {
                const blob = xhr.response;
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = fileName;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);
            } catch (e) {
                console.error('文件下载失败:', e);
                alert('文件下载失败，请重试');
            }
        } else {
            if (xhr.status === 0) {
                alert('网络已断开，请检查网络连接后重试');
            } else {
                alert(`文件下载失败 (HTTP ${xhr.status})，请重试`);
            }
        }
    };

    xhr.onerror = function() {
        alert('文件下载失败，请检查网络连接后重试');
    };

    xhr.ontimeout = function() {
        alert('下载超时，请检查网络后重试');
    };

    xhr.timeout = 30000;
    xhr.send();
}

function getFileIcon(fileName) {
    if (!fileName) return '📄';
    const ext = fileName.split('.').pop().toLowerCase();
    const iconMap = {
        'pdf': '📄',
        'doc': '📝', 'docx': '📝',
        'xls': '📊', 'xlsx': '📊',
        'ppt': '📑', 'pptx': '📑',
        'txt': '📃',
        'zip': '📦', 'rar': '📦', '7z': '📦',
        'jpg': '🖼️', 'jpeg': '🖼️', 'png': '🖼️', 'gif': '🖼️', 'bmp': '🖼️', 'webp': '🖼️',
        'mp4': '🎥', 'avi': '🎥', 'mov': '🎥', 'mkv': '🎥',
        'mp3': '🎧', 'wav': '🎧', 'flac': '🎧', 'aac': '🎧',
        'exe': '⚙️', 'msi': '⚙️',
        'apk': '📱',
        'html': '🌐', 'css': '🎨', 'js': '⚡',
        'py': '🐍', 'java': '☕',
        'sql': '🗃️',
        'xml': '📋', 'json': '📋', 'yaml': '📋', 'yml': '📋'
    };
    return iconMap[ext] || '📄';
}

function formatFileSize(bytes) {
    if (!bytes) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

function handleFileSelect(event) {
    const files = event.target.files;
    if (!files || files.length === 0) return;

    Array.from(files).forEach(file => {
        if (!currentPrivateChat && !isImageFile(file)) {
            alert('群聊仅支持发送图片文件');
            return;
        }
        uploadFile(file);
    });

    event.target.value = '';
}

function uploadFile(file) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', isImageFile(file) ? 'image' : 'file');

    if (currentPrivateChat) {
        formData.append('receiver', currentPrivateChat);
    }

    const xhr = new XMLHttpRequest();

    xhr.upload.onprogress = function(e) {
        if (e.lengthComputable) {
            const percent = (e.loaded / e.total) * 100;
            console.log('上传进度:', percent.toFixed(2) + '%');
        }
    };

    xhr.onload = function() {
        if (xhr.status === 200) {
            try {
                const response = JSON.parse(xhr.responseText);
                if (response.success) {
                    const msg = response.message;

                    if (currentPrivateChat) {
                        if (!privateChats[currentPrivateChat]) {
                            privateChats[currentPrivateChat] = { name: currentPrivateChat, messages: [], unread: 0 };
                        }
                        privateChats[currentPrivateChat].messages.push(msg);
                        appendFileMessage(msg);
                        savePrivateChatsToStorage();
                    }
                } else {
                    alert('文件上传失败: ' + response.message);
                }
            } catch (e) {
                console.error('解析响应失败:', xhr.responseText);
                alert('文件上传失败，服务器返回了错误响应');
            }
        } else {
            alert('文件上传失败，HTTP状态码: ' + xhr.status);
        }
    };

    xhr.onerror = function() {
        alert('文件上传失败，请检查网络连接');
    };

    xhr.open('POST', '/api/file/upload');
    xhr.send(formData);
}

function isImageFile(file) {
    const imageTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/webp'];
    return imageTypes.includes(file.type);
}

function openImagePreview(imageUrl, fileName) {
    const overlay = document.getElementById('imagePreviewOverlay');
    const previewImg = document.getElementById('imagePreviewImg');
    const previewInfo = document.getElementById('imagePreviewInfo');

    previewImg.src = imageUrl;
    previewInfo.textContent = fileName || '图片预览';
    overlay.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeImagePreview(event) {
    if (event) {
        event.stopPropagation();
    }
    const overlay = document.getElementById('imagePreviewOverlay');
    overlay.classList.remove('active');
    document.body.style.overflow = '';

    setTimeout(() => {
        document.getElementById('imagePreviewImg').src = '';
    }, 200);
}

document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape') {
        const overlay = document.getElementById('imagePreviewOverlay');
        if (overlay.classList.contains('active')) {
            closeImagePreview();
        }
    }
});

function autoResize(textarea) {
    textarea.style.height = 'auto';
    textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px';

    checkAtTrigger(textarea);
}

function checkAtTrigger(textarea) {
    const value = textarea.value;
    const cursorPos = textarea.selectionStart;

    const textBeforeCursor = value.substring(0, cursorPos);
    const lastAtPos = textBeforeCursor.lastIndexOf('@');

    if (lastAtPos !== -1) {
        const textAfterAt = textBeforeCursor.substring(lastAtPos + 1);

        if (textAfterAt.includes(' ') || textAfterAt.includes('\n')) {
            hideAtUserPopup();
            return;
        }

        showAtUserPopup(textAfterAt, cursorPos, lastAtPos);
    } else {
        hideAtUserPopup();
    }
}

function showAtUserPopup(searchText, cursorPos, atPos) {
    const popup = document.getElementById('atUserPopup');
    const listDiv = document.getElementById('atUserList');
    const searchInput = document.getElementById('atSearchInput');
    const multiSelectBtn = document.getElementById('atMultiSelectBtn');
    const multiSelectActions = document.getElementById('atMultiSelectActions');

    const textarea = document.getElementById('content');
    const textareaRect = textarea.getBoundingClientRect();

    const textBeforeAt = textarea.value.substring(0, atPos);
    const lines = textBeforeAt.split('\n');
    const currentLine = lines[lines.length - 1];

    const lineHeight = 20;
    const topOffset = Math.min(lines.length, 5) * lineHeight;

    const popupHeight = 250;
    const popupPadding = 10;

    const isMobile = window.innerWidth <= 640;
    const availableSpaceAbove = textareaRect.top;
    const availableSpaceBelow = window.innerHeight - textareaRect.bottom;

    let showAbove = true;
    let topPos, leftPos;

    if (isMobile) {
        if (availableSpaceAbove < popupHeight + popupPadding) {
            if (availableSpaceBelow >= popupHeight + popupPadding) {
                showAbove = false;
            } else {
                showAbove = availableSpaceAbove >= availableSpaceBelow;
            }
        }

        if (showAbove) {
            topPos = Math.max(popupPadding, textareaRect.top - popupHeight - popupPadding);
        } else {
            topPos = textareaRect.bottom + popupPadding;
        }

        leftPos = Math.max(popupPadding, textareaRect.left);
        leftPos = Math.min(leftPos, window.innerWidth - popup.offsetWidth - popupPadding);

        popup.style.left = leftPos + 'px';
        popup.style.top = topPos + 'px';
        popup.style.maxHeight = (showAbove ? availableSpaceAbove - popupPadding : availableSpaceBelow - popupPadding) + 'px';
        popup.style.width = 'calc(100% - 20px)';
    } else {
        popup.style.left = textareaRect.left + 'px';
        popup.style.top = (textareaRect.top - topOffset - popupHeight) + 'px';
    }

    if (!atMultiSelectMode) {
        atSelectedUsers = [];
    }
    atOriginalCursorPos = cursorPos;
    atOriginalAtPos = atPos;

    if (atMultiSelectMode) {
        multiSelectBtn.textContent = '取消多选';
        multiSelectActions.classList.remove('hidden');
        searchInput.value = searchText;
        searchInput.parentElement.classList.remove('hidden');
    } else {
        multiSelectBtn.textContent = '多选';
        multiSelectActions.classList.add('hidden');
        searchInput.value = '';
        searchInput.parentElement.classList.add('hidden');
    }

    const filteredUsers = onlineUsersCache.filter(name =>
        name !== currentName && (searchText === '' || name.toLowerCase().includes(searchText.toLowerCase()))
    ).slice(0, 10);

    if (filteredUsers.length === 0) {
        hideAtUserPopup();
        return;
    }

    atSelectedIndex = 0;

    if (atMultiSelectMode) {
        listDiv.innerHTML = filteredUsers.map((name, index) => {
            const isGuest = name.startsWith('游客_');
            const badgeClass = isGuest ? 'bg-yellow-100 text-yellow-800' : 'bg-blue-100 text-blue-800';
            const badgeText = isGuest ? '游客' : '用户';
            const isSelected = atSelectedUsers.includes(name);

            return `                <div class="at-user-item ${isSelected ? 'selected' : ''}" data-index="${index}" data-name="${name}" onclick="toggleAtUserSelection('${name}')">
                    <div class="select-check"></div>
                    <span class="user-name">${name}</span>
                    <span class="user-badge ${badgeClass}">${badgeText}</span>
                </div>
            `;
        }).join('');
    } else {
        listDiv.innerHTML = filteredUsers.map((name, index) => {
            const isGuest = name.startsWith('游客_');
            const badgeClass = isGuest ? 'bg-yellow-100 text-yellow-800' : 'bg-blue-100 text-blue-800';
            const badgeText = isGuest ? '游客' : '用户';

            return `                <div class="at-user-item ${index === 0 ? 'active' : ''}" data-index="${index}" data-name="${name}" onclick="selectAtUserByClick('${name}')">
                    <span class="user-name">${name}</span>
                    <span class="user-badge ${badgeClass}">${badgeText}</span>
                </div>
            `;
        }).join('');
    }

    popup.classList.remove('hidden');
    atPopupVisible = true;
    updateAtSelectedCount();
}

function hideAtUserPopup() {
    const popup = document.getElementById('atUserPopup');
    popup.classList.add('hidden');
    atPopupVisible = false;
    atPopupTarget = null;
    if (!atMultiSelectMode) {
        atSelectedUsers = [];
    }
}
function toggleAtMultiSelect() {
    atMultiSelectMode = !atMultiSelectMode;
    if (!atMultiSelectMode) {
        atSelectedUsers = [];
        const searchInput = document.getElementById('atSearchInput');
        searchInput.value = '';
        filterAtUsers('');
    } else {
        filterAtUsers(document.getElementById('atSearchInput').value);
    }
}

function toggleAtUserSelection(name) {
    if (!atMultiSelectMode) return;

    const index = atSelectedUsers.indexOf(name);
    if (index > -1) {
        atSelectedUsers.splice(index, 1);
    } else {
        atSelectedUsers.push(name);
    }

    updateAtSelectedCount();

    const item = document.querySelector(`.at-user-item[data-name="${name}"]`);
    if (item) {
        item.classList.toggle('selected');
    }
}

function updateAtSelectedCount() {
    const countSpan = document.getElementById('atSelectedCount');
    if (countSpan) {
        countSpan.textContent = atSelectedUsers.length;
    }
}

function clearAtSelection() {
    atSelectedUsers = [];
    updateAtSelectedCount();
    document.querySelectorAll('.at-user-item').forEach(item => {
        item.classList.remove('selected');
    });
}

function confirmAtSelection() {
    if (atSelectedUsers.length === 0) {
        alert('请至少选择一位用户');
        return;
    }

    const textarea = document.getElementById('content');
    const textBeforeAt = textarea.value.substring(0, atOriginalAtPos);
    const textAfterCursor = textarea.value.substring(atOriginalCursorPos);

    const mentions = atSelectedUsers.map(name => `@${name}`).join(' ');
    const newValue = textBeforeAt + mentions + textAfterCursor;

    textarea.value = newValue;

    const newCursorPos = atOriginalAtPos + mentions.length;
    textarea.setSelectionRange(newCursorPos, newCursorPos);
    textarea.focus();

    autoResize(textarea);
    hideAtUserPopup();
}

function filterAtUsers(searchText) {
    showAtUserPopup(searchText, atOriginalCursorPos, atOriginalAtPos);
}

function updateAtUserSelection() {
    document.querySelectorAll('.at-user-item').forEach((item, index) => {
        if (index === atSelectedIndex) {
            item.classList.add('active');
            item.scrollIntoView({ block: 'nearest' });
        } else {
            item.classList.remove('active');
        }
    });
}

function selectAtUser() {
    const selectedItem = document.querySelector(`.at-user-item[data-index="${atSelectedIndex}"]`);
    if (selectedItem) {
        const userName = selectedItem.getAttribute('data-name');
        insertAtMention(userName);
    }
}

function selectAtUserByClick(userName) {
    insertAtMention(userName);
}

function insertAtMention(userName) {
    const textarea = document.getElementById('content');
    const value = textarea.value;
    const cursorPos = textarea.selectionStart;

    const textBeforeCursor = value.substring(0, cursorPos);
    const lastAtPos = textBeforeCursor.lastIndexOf('@');

    if (lastAtPos !== -1) {
        const newValue = value.substring(0, lastAtPos) + '@' + userName + ' ' + value.substring(cursorPos);
        textarea.value = newValue;

        const newCursorPos = lastAtPos + userName.length + 2;
        textarea.setSelectionRange(newCursorPos, newCursorPos);
        textarea.focus();

        autoResize(textarea);
    }

    hideAtUserPopup();
}

document.addEventListener('click', function(event) {
    const popup = document.getElementById('atUserPopup');
    const textarea = document.getElementById('content');

    if (atPopupVisible && !popup.contains(event.target) && event.target !== textarea) {
        hideAtUserPopup();
    }

    const onlinePopup = document.getElementById('onlineUsersPopup');
    const btn = document.getElementById('onlineCountBtn');
    const contextMenu = document.getElementById('contextMenu');
    if (!onlinePopup.contains(event.target) && !btn.contains(event.target)) {
        onlinePopup.classList.add('hidden');
    }
    if (!contextMenu.contains(event.target)) {
        contextMenu.style.display = 'none';
    }
});

function handleKeyDown(event) {
    if (atPopupVisible) {
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            atSelectedIndex = Math.min(atSelectedIndex + 1, document.querySelectorAll('.at-user-item').length - 1);
            updateAtUserSelection();
            return;
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            atSelectedIndex = Math.max(atSelectedIndex - 1, 0);
            updateAtUserSelection();
            return;
        } else if (event.key === 'Enter' || event.key === 'Tab') {
            event.preventDefault();
            selectAtUser();
            return;
        } else if (event.key === 'Escape') {
            event.preventDefault();
            hideAtUserPopup();
            return;
        }
    }

    if (event.keyCode === 13) {
        if (event.shiftKey) {
            return;
        } else {
            event.preventDefault();
            send();
        }
    }
}

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

function quickPrivateChat(name) {
    if (isGuest) {
        alert('私聊功能仅对登录用户开放，请先登录！');
        return;
    }
    if (name.startsWith('游客_')) {
        alert('无法与游客用户进行私聊');
        return;
    }
    if (name === currentName) {
        return;
    }
    openPrivateChat(name);
}
