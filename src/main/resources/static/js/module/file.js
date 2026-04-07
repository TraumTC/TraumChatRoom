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
