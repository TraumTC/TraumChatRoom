function appendMessage(timeStr, sender, message) {
    const time = formatTime(timeStr);
    const isMyMessage = sender === currentName || sender === '我';

    const msgContainer = document.createElement('div');
    msgContainer.className = `message-container ${isMyMessage ? 'message-right' : 'message-left'}`;

    const bubbleDiv = document.createElement('div');
    bubbleDiv.className = 'message-bubble';

    if (!isMyMessage) {
        const senderDiv = document.createElement('div');
        senderDiv.className = 'message-sender select-none';
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

        const isValidMention = mentionableUsersLoaded && (mentionedName.startsWith('游客_') || mentionableUsersCache.includes(mentionedName));

        if (isValidMention) {
            const mentionSpan = document.createElement('span');
            mentionSpan.className = 'at-mention';
            mentionSpan.textContent = `@${mentionedName}`;
            mentionSpan.style.cursor = 'pointer';
            mentionSpan.addEventListener('click', () => quickPrivateChat(mentionedName));
            container.appendChild(mentionSpan);
        } else {
            container.appendChild(document.createTextNode(`@${mentionedName}`));
        }

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
        senderDiv.className = 'message-sender select-none';
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
    } else {
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
