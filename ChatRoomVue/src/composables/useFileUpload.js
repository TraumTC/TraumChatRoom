// src/composables/useFileUpload.js — 文件/图片上传（占位 + 进度 + 落位）
import { fileApi } from '@/api/file'
import { fileRequestId } from '@/utils/request-id'
import { useAuthStore } from '@/stores/auth'

// 临时消息 id 用递减序号保证唯一：同毫秒内两次上传不会撞出重复的列表 key
let tempSeq = 0

export function useFileUpload(chatStore) {
  const authStore = useAuthStore()

  // 上传文件（私聊时自动附带 receiver）
  async function handleFileUpload(file) {
    const type = file.type.startsWith('image/') ? 'image' : 'file'
    const isPrivateMode = chatStore.currentChat.type === 'private'
    const receiver = isPrivateMode ? chatStore.currentChat.username : null
    const clientId = crypto.randomUUID()

    // 1. 先在聊天框占位：上传大文件时不占位的话，界面上什么都不发生，
    //    用户无法判断是没点上还是在传，容易反复点击（继而撞上后端 5 次/分钟的限流）
    const placeholder = {
      id: -(Date.now() + (++tempSeq)),   // 负数临时 id，不会与数据库 id 冲突
      sender: {
        id: authStore.user?.id,
        username: authStore.user?.username,
        name: authStore.user?.name,
        avatar: authStore.user?.avatar
      },
      receiver: receiver ? { id: null, username: receiver, name: receiver } : undefined,
      content: '',
      messageType: type,
      fileName: file.name,
      filePath: null,                    // 尚未落库，没有可下载地址
      fileSize: file.size,
      aiReply: false,
      recalled: false,
      replyToId: null,
      createdAt: new Date().toISOString(),
      _tempCreatedAt: Date.now(),
      _clientId: clientId,
      _uploading: true,
      _progress: 0
    }
    if (isPrivateMode) chatStore.addPrivateMessage(placeholder)
    else chatStore.addMessage(placeholder)

    const formData = new FormData()
    formData.append('file', file)
    formData.append('type', type)
    if (receiver) formData.append('receiver', receiver)

    try {
      const res = await fileApi.upload(formData, {
        // 同文件 + 同目标才算重复提交（见 fileRequestId 的说明）
        requestId: fileRequestId(file, receiver || 'group'),
        onProgress: (e) => {
          if (!e.total) return
          // 封顶 99%：字节传完后服务端还要落盘、入库、广播，
          // 提前显示 100% 却迟迟不消失看起来像卡死
          chatStore.updateUploadProgress(clientId, Math.min(99, Math.round((e.loaded * 100) / e.total)))
        }
      })

      const realMsg = res.data?.data?.message
      if (res.data?.code === 200 && realMsg) {
        // 2. 直接用 HTTP 响应里的真实消息落位。
        //    原实现丢弃了这个响应、完全等 WS 推送，于是 WS 断开时会出现
        //    「上传成功了但聊天框里看不到这条消息」，要刷新才出现。
        //    WS 推送随后到达时会按 id 去重，不会重复显示（见 settleUpload）。
        chatStore.settleUpload(clientId, realMsg)
      } else {
        chatStore.removePendingMessage(clientId)
        window.$message?.error(res.data?.message || '上传失败')
      }
    } catch (e) {
      // 3. 失败必须摘掉占位，否则会留下一条永远停在某个百分比的幽灵消息
      chatStore.removePendingMessage(clientId)
      window.$message?.error(e.response?.data?.message || '上传失败，请重试')
    }
  }

  return { handleFileUpload }
}
