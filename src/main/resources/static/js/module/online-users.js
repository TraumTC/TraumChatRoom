let onlineUsersCache = [];

function toggleOnlineUsers() {
    const popup = document.getElementById('onlineUsersPopup');
    popup.classList.toggle('hidden');
}

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
