// src/composables/useFileUpload.js — 文件/图片上传
import { fileApi } from '@/api/file'

export function useFileUpload(chatStore) {
  // 上传文件（私聊时自动附带 receiver）
  async function handleFileUpload(file) {
    const type = file.type.startsWith('image/') ? 'image' : 'file'
    const formData = new FormData()
    formData.append('file', file)
    formData.append('type', type)

    const isPrivateMode = chatStore.currentChat.type === 'private'
    if (isPrivateMode) {
      formData.append('receiver', chatStore.currentChat.username)
    }

    try {
      const res = await fileApi.upload(formData)
      if (res.data.code !== 200) {
        window.$message?.error(res.data.message || '上传失败')
      }
    } catch (e) {
      window.$message?.error('上传失败，请重试')
    }
  }

  return { handleFileUpload }
}
