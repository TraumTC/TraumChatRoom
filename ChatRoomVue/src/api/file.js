// src/api/file.js — 文件相关 API
import api from './index'

export const fileApi = {
  /**
   * 上传文件。
   *
   * requestId 必须由调用方给出：api/index.js 的请求拦截器在缺失时会补一个随机 UUID，
   * 那会让后端 @Idempotent 永远拿不到重复的号、防重形同虚设。
   * 上传场景要的是「同文件同目标才算重复」，见 utils/request-id.js 的 fileRequestId。
   *
   * onProgress 直接透给 axios 的 onUploadProgress，用于聊天框里的上传进度条。
   */
  upload: (formData, { requestId, onProgress } = {}) => api.post('/api/file/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      ...(requestId ? { 'X-Request-Id': requestId } : {})
    },
    timeout: 300000,  // 文件上传超时 5 分钟
    onUploadProgress: onProgress
  })
}
