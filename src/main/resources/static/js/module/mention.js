let atPopupVisible = false;
let atPopupTarget = null;
let atSelectedIndex = 0;
let atMultiSelectMode = false;
let atSelectedUsers = [];
let atOriginalCursorPos = 0;
let atOriginalAtPos = 0;
let mentionableUsersCache = [];
let atManuallyClosed = false;
let mentionableUsersLoaded = false;
let isDragging = false;
let dragStartX = 0;
let dragStartY = 0;
let popupStartX = 0;
let popupStartY = 0;

function loadMentionableUsers() {
    return fetch('/api/mentionable-users')
        .then(res => res.json())
        .then(users => {
            mentionableUsersCache = users || [];
            mentionableUsersLoaded = true;
            console.log('可@用户列表加载完成:', mentionableUsersCache);
        })
        .catch(err => {
            console.error('加载可@用户列表失败:', err);
            mentionableUsersCache = [];
            mentionableUsersLoaded = true;
        });
}

function autoResize(textarea) {
    textarea.style.height = 'auto';
    textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px';

    checkAtTrigger(textarea);
}

function checkAtTrigger(textarea) {
    if (currentPrivateChat) {
        hideAtUserPopup();
        return;
    }

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

        // 如果是新的@位置，重置手动关闭标志
        if (atManuallyClosed && lastAtPos !== atOriginalAtPos) {
            atManuallyClosed = false;
        }

        if (atManuallyClosed) {
            return;
        }

        showAtUserPopup(textAfterAt, cursorPos, lastAtPos);
    } else {
        hideAtUserPopup();
        atManuallyClosed = false;
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
        // 计算实际可用空间，考虑虚拟键盘的影响
        const totalAvailableSpace = Math.max(availableSpaceAbove, availableSpaceBelow);
        
        // 优先显示在空间较大的一侧
        showAbove = availableSpaceAbove > availableSpaceBelow;

        if (showAbove) {
            topPos = Math.max(popupPadding, textareaRect.top - popupHeight - popupPadding);
        } else {
            topPos = textareaRect.bottom + popupPadding;
        }

        leftPos = Math.max(popupPadding, textareaRect.left);
        leftPos = Math.min(leftPos, window.innerWidth - popup.offsetWidth - popupPadding);

        popup.style.left = leftPos + 'px';
        popup.style.top = topPos + 'px';
        
        // 动态设置最大高度，确保列表完整显示
        const maxHeight = Math.min(popupHeight, totalAvailableSpace - popupPadding * 2);
        popup.style.maxHeight = maxHeight + 'px';
        popup.style.width = 'calc(100% - 20px)';
    } else {
        popup.style.left = textareaRect.left + 'px';
        popup.style.top = (textareaRect.top - topOffset - popupHeight) + 'px';
    }

    // 添加拖拽事件监听器
    const popupHeader = popup.querySelector('.border-b');
    if (popupHeader) {
        popupHeader.style.cursor = 'move';
        popupHeader.addEventListener('mousedown', startDrag);
        popupHeader.addEventListener('touchstart', startDrag, { passive: false });
    }

    // 移除旧的拖拽事件监听器，避免重复添加
    document.removeEventListener('mousemove', drag);
    document.removeEventListener('touchmove', drag, { passive: false });
    document.removeEventListener('mouseup', stopDrag);
    document.removeEventListener('touchend', stopDrag);

    // 添加新的拖拽事件监听器
    document.addEventListener('mousemove', drag);
    document.addEventListener('touchmove', drag, { passive: false });
    document.addEventListener('mouseup', stopDrag);
    document.addEventListener('touchend', stopDrag);

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

    const filteredUsers = mentionableUsersCache.filter(name =>
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

function hideAtUserPopup(manualClose = false) {
    const popup = document.getElementById('atUserPopup');
    // 只有在非多选模式下或手动关闭时才隐藏列表
    if (!atMultiSelectMode || manualClose) {
        popup.classList.add('hidden');
        atPopupVisible = false;
        atPopupTarget = null;
        if (manualClose) {
            atManuallyClosed = true;
        }
        if (!atMultiSelectMode) {
            atSelectedUsers = [];
        }
    }
}

// 监听输入框焦点事件，确保@列表在输入框隐藏时也能正确隐藏
function initMentionEventListeners() {
    const textarea = document.getElementById('content');
    if (textarea) {
        textarea.addEventListener('blur', function(e) {
            // 检查事件目标是否在@列表或多选按钮内
            const atPopup = document.getElementById('atUserPopup');
            const multiSelectBtn = document.getElementById('atMultiSelectBtn');
            const atUserList = document.getElementById('atUserList');
            
            // 如果在多选模式下，即使点击外部也不隐藏@列表
            if (atMultiSelectMode) {
                return;
            }
            
            // 如果点击的是@列表内部、多选按钮或用户项，不隐藏@列表
            if (atPopup && (atPopup.contains(e.relatedTarget) || e.relatedTarget === multiSelectBtn || 
                (atUserList && atUserList.contains(e.relatedTarget)))) {
                return;
            }
            
            // 延迟隐藏，以便用户可以点击@列表中的选项
            setTimeout(() => {
                if (atPopupVisible) {
                    hideAtUserPopup(true);
                }
            }, 200);
        });
        
        // 监听键盘事件
        textarea.addEventListener('keydown', handleKeyDown);
        
        // 监听窗口大小变化，重新计算@列表位置
        window.addEventListener('resize', function() {
            if (atPopupVisible) {
                const textarea = document.getElementById('content');
                const cursorPos = textarea.selectionStart;
                const textBeforeCursor = textarea.value.substring(0, cursorPos);
                const lastAtPos = textBeforeCursor.lastIndexOf('@');
                if (lastAtPos !== -1) {
                    const searchText = textBeforeCursor.substring(lastAtPos + 1);
                    showAtUserPopup(searchText, cursorPos, lastAtPos);
                }
            }
        });
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
    
    // 保持输入框焦点，防止移动端键盘隐藏
    const textarea = document.getElementById('content');
    setTimeout(() => {
        textarea.focus();
    }, 100);
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

// 拖拽开始函数
function startDrag(e) {
    // 检查事件目标是否是多选按钮或其内部元素，如果是则不执行拖拽逻辑
    const multiSelectBtn = document.getElementById('atMultiSelectBtn');
    if (multiSelectBtn && (e.target === multiSelectBtn || multiSelectBtn.contains(e.target))) {
        return;
    }
    
    e.preventDefault();
    isDragging = true;
    
    const popup = document.getElementById('atUserPopup');
    const rect = popup.getBoundingClientRect();
    
    if (e.type === 'touchstart') {
        dragStartX = e.touches[0].clientX;
        dragStartY = e.touches[0].clientY;
    } else {
        dragStartX = e.clientX;
        dragStartY = e.clientY;
    }
    
    popupStartX = rect.left;
    popupStartY = rect.top;
    
    // 添加拖拽样式
    popup.style.opacity = '0.8';
    popup.style.zIndex = '1000';
}

// 拖拽中函数
function drag(e) {
    if (!isDragging) return;
    e.preventDefault();
    
    const popup = document.getElementById('atUserPopup');
    let clientX, clientY;
    
    if (e.type === 'touchmove') {
        clientX = e.touches[0].clientX;
        clientY = e.touches[0].clientY;
    } else {
        clientX = e.clientX;
        clientY = e.clientY;
    }
    
    const deltaX = clientX - dragStartX;
    const deltaY = clientY - dragStartY;
    
    let newLeft = popupStartX + deltaX;
    let newTop = popupStartY + deltaY;
    
    // 限制拖拽范围，确保弹窗不会超出屏幕
    const popupWidth = popup.offsetWidth;
    const popupHeight = popup.offsetHeight;
    const windowWidth = window.innerWidth;
    const windowHeight = window.innerHeight;
    
    newLeft = Math.max(10, Math.min(newLeft, windowWidth - popupWidth - 10));
    newTop = Math.max(10, Math.min(newTop, windowHeight - popupHeight - 10));
    
    popup.style.left = newLeft + 'px';
    popup.style.top = newTop + 'px';
}

// 拖拽结束函数
function stopDrag() {
    if (!isDragging) return;
    isDragging = false;
    
    const popup = document.getElementById('atUserPopup');
    // 移除拖拽样式
    popup.style.opacity = '1';
    popup.style.zIndex = '50';
}

// 页面加载时初始化事件监听器
document.addEventListener('DOMContentLoaded', function() {
    loadMentionableUsers();
    initMentionEventListeners();
    
    // 监听输入框位置变化，用于移动端键盘弹出/收起时调整@列表位置
    const textarea = document.getElementById('content');
    if (textarea) {
        // 监听输入框的focus和blur事件，用于检测键盘弹出/收起
        textarea.addEventListener('focus', function() {
            setTimeout(() => {
                if (atPopupVisible) {
                    const cursorPos = textarea.selectionStart;
                    const textBeforeCursor = textarea.value.substring(0, cursorPos);
                    const lastAtPos = textBeforeCursor.lastIndexOf('@');
                    if (lastAtPos !== -1) {
                        const searchText = textBeforeCursor.substring(lastAtPos + 1);
                        showAtUserPopup(searchText, cursorPos, lastAtPos);
                    }
                }
            }, 300); // 延迟执行，确保键盘已经弹出
        });
        
        textarea.addEventListener('blur', function() {
            setTimeout(() => {
                if (atPopupVisible) {
                    const cursorPos = textarea.selectionStart;
                    const textBeforeCursor = textarea.value.substring(0, cursorPos);
                    const lastAtPos = textBeforeCursor.lastIndexOf('@');
                    if (lastAtPos !== -1) {
                        const searchText = textBeforeCursor.substring(lastAtPos + 1);
                        showAtUserPopup(searchText, cursorPos, lastAtPos);
                    }
                }
            }, 300); // 延迟执行，确保键盘已经收起
        });
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
            hideAtUserPopup(true);
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