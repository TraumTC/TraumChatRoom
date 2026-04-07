let currentPrivateChat = null;
let currentPrivateChatName = '';
let privateChats = {};
const pageLoadTime = Date.now();
const SILENT_PERIOD = 10000;

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

    if (currentPrivateChat !== sender || document.hidden) {
        startTitleFlash();
    }

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
