let stompClient = null;
let connectionState = 'disconnected';
let reconnectAttempts = 0;
const maxReconnectAttempts = 10;
let manualDisconnect = false;

function initWebSocket() {
    const sock = new SockJS('/ws', null, {
        withCredentials: true,
        transports: ['websocket']
    });
    stompClient = Stomp.over(sock);
    stompClient.debug = null;
}

function connectWebSocket() {
    if (!stompClient) {
        initWebSocket();
    }

    if (connectionState === 'connecting') {
        return;
    }

    connectionState = 'connecting';
    manualDisconnect = false;

    stompClient.connect({}, onConnected, onError);
}

function onConnected(frame) {
    reconnectAttempts = 0;
    connectionState = 'connected';

    stompClient.subscribe('/topic/messages', onPublicMessage);
    stompClient.subscribe('/user/queue/private-messages', onPrivateMessage);
    stompClient.subscribe('/topic/private-notifications', onNotification);
    stompClient.subscribe('/topic/onlineUsers', onOnlineUsersUpdate);
    stompClient.subscribe('/user/queue/send-error', onSendError);

    loadPublicHistory();
    loadInitialOnlineUsers();
}

function onError(error) {
    connectionState = 'disconnected';

    if (!manualDisconnect && reconnectAttempts < maxReconnectAttempts) {
        reconnectAttempts++;
        const delay = Math.min(3000 * reconnectAttempts, 30000);
        setTimeout(connectWebSocket, delay);
    } else if (!manualDisconnect) {
        alert('WebSocket连接失败，请刷新页面重试');
    }
}

function onPublicMessage(res) {
    if (currentPrivateChat) return;

    const msg = JSON.parse(res.body);
    if (msg.messageType === 'file' || msg.messageType === 'image') {
        appendFileMessage(msg);
    } else {
        appendMessage(msg.sendTime, msg.sender, msg.message);
    }
}

function onPrivateMessage(res) {
    handlePrivateMessage(JSON.parse(res.body));
}

function onNotification(res) {
    handlePrivateNotification(JSON.parse(res.body));
}

function onOnlineUsersUpdate(res) {
    const data = JSON.parse(res.body);
    const users = Array.isArray(data) ? data : (data?.onlineUsers || []);
    updateOnlineUsersList(users);
}
function onSendError(res) {
    const data = JSON.parse(res.body);
    if (data.type === 'send_error') {
        showSendError(data.message);
    }
}

function loadInitialOnlineUsers() {
    fetch('/api/online-users')
        .then(r => r.json())
        .then(data => {
            const users = data?.onlineUsers || (Array.isArray(data) ? data : []);
            updateOnlineUsersList(users);
        });
}

window.addEventListener('beforeunload', function() {
    manualDisconnect = true;
    if (stompClient && stompClient.connected) {
        try {
            stompClient.disconnect();
        } catch (e) {
        }
    }
});

window.addEventListener('offline', function() {
    connectionState = 'disconnected';
});

window.addEventListener('online', function() {
    if (connectionState !== 'connected' && !manualDisconnect) {
        reconnectAttempts = 0;
        connectWebSocket();
    }
});
