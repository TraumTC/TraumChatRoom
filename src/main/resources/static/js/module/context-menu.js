// context-menu.js - 右键/长按菜单管理
let contextMenuTarget = null;

// 初始化上下文菜单
function initContextMenu() {
    // PC 端右键菜单
    document.addEventListener('contextmenu', function(e) {
        const senderName = e.target.closest('.message-sender')?.textContent;
        if (senderName) {
            e.preventDefault();
            showContextMenu(e.clientX, e.clientY, senderName);
        }
    });

    // 移动端长按菜单
    let touchTimer = null;
    document.addEventListener('touchstart', function(e) {
        const senderName = e.target.closest('.message-sender')?.textContent;
        if (senderName) {
            touchTimer = setTimeout(() => {
                const touch = e.touches[0];
                showContextMenu(touch.clientX, touch.clientY, senderName);
            }, 500); // 500ms 长按触发
        }
    });

    document.addEventListener('touchend', function() {
        if (touchTimer) {
            clearTimeout(touchTimer);
            touchTimer = null;
        }
    });

    document.addEventListener('touchmove', function() {
        if (touchTimer) {
            clearTimeout(touchTimer);
            touchTimer = null;
        }
    });

    // 点击其他地方隐藏菜单
    document.addEventListener('click', function(e) {
        const menu = document.getElementById('contextMenu');
        if (menu && !menu.contains(e.target)) {
            menu.style.display = 'none';
        }
    });
}

// 显示上下文菜单
function showContextMenu(x, y, username) {
    contextMenuTarget = username;

    let menu = document.getElementById('contextMenu');
    if (!menu) {
        menu = document.createElement('div');
        menu.id = 'contextMenu';
        menu.className = 'fixed bg-white rounded-lg shadow-lg border border-gray-200 py-2 z-50 min-w-[120px]';
        menu.style.display = 'none';
        document.body.appendChild(menu);

        const atItem = document.createElement('div');
        atItem.className = 'px-4 py-2 hover:bg-blue-50 cursor-pointer text-sm text-gray-700 transition-colors flex items-center gap-2';
        atItem.innerHTML = `<span class="text-blue-500 font-bold">@</span> ${username}`;
        atItem.onclick = function() {
            if (contextMenuTarget) {
                insertAtMention(contextMenuTarget);
                menu.style.display = 'none';
            }
        };
        menu.appendChild(atItem);
    }

    // 定位菜单
    const menuWidth = 120;
    const menuHeight = 40;
    const posX = Math.min(x, window.innerWidth - menuWidth - 10);
    const posY = Math.min(y, window.innerHeight - menuHeight - 10);

    menu.style.left = posX + 'px';
    menu.style.top = posY + 'px';
    menu.style.display = 'block';
}

// 在输入框插入 @提及
function insertAtMention(username) {
    const textarea = document.getElementById('content');
    if (!textarea) return;

    const currentValue = textarea.value;
    const cursorPos = textarea.selectionStart || currentValue.length;

    // 在光标位置插入 @昵称
    const beforeCursor = currentValue.substring(0, cursorPos);
    const afterCursor = currentValue.substring(cursorPos);

    // 如果光标前已经有 @ 了，替换掉
    const lastAtPos = beforeCursor.lastIndexOf('@');
    let newValue;
    if (lastAtPos !== -1 && beforeCursor.substring(lastAtPos + 1).trim() === '') {
        newValue = beforeCursor.substring(0, lastAtPos) + '@' + username + ' ' + afterCursor;
    } else {
        newValue = beforeCursor + '@' + username + ' ' + afterCursor;
    }

    textarea.value = newValue;

    // 设置光标位置
    const newCursorPos = lastAtPos !== -1 ? lastAtPos + username.length + 2 : cursorPos + username.length + 2;
    textarea.setSelectionRange(newCursorPos, newCursorPos);
    textarea.focus();

    // 触发自定义调整大小和 @触发检测
    if (typeof autoResize === 'function') {
        autoResize(textarea);
    }
}

// 页面加载时初始化
document.addEventListener('DOMContentLoaded', initContextMenu);
