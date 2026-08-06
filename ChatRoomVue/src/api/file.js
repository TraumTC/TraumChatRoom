// src/api/file.js — 文件相关 API
import api from './index'
import { getRequestId } from '@/utils/request-id'

export const fileApi = {
  // 幂等：同一文件复用同一 X-Request-Id
  upload: (formData) => api.post('/api/file/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      'X-Request-Id': getRequestId('file-upload')
    },
    timeout: 300000  // 文件上传超时 5 分钟
  })
}
