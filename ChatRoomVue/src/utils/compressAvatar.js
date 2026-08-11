// src/utils/compressAvatar.js — 头像压缩（Canvas 居中裁剪 + JPEG 压缩）

const TARGET_SIZE = 256       // 输出尺寸 256x256
const QUALITY = 0.85          // JPEG 质量
const MAX_FILE_SIZE = 5 * 1024 * 1024  // 前端校验上限 5MB

/**
 * 校验头像文件
 * @param {File} file
 * @returns {{ valid: boolean, error?: string }}
 */
export function validateAvatarFile(file) {
  if (!file) {
    return { valid: false, error: '请选择文件' }
  }
  if (!file.type.startsWith('image/')) {
    return { valid: false, error: '只能上传图片文件' }
  }
  if (file.size > MAX_FILE_SIZE) {
    return { valid: false, error: '图片大小不能超过 5MB' }
  }
  return { valid: true }
}

/**
 * 将图片文件压缩为 256x256 的 JPEG Blob（居中裁剪）
 * @param {File} file 原始图片文件
 * @returns {Promise<Blob>} 压缩后的 Blob
 */
export function compressAvatar(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      const img = new Image()
      img.onload = () => {
        // 居中正方形裁剪
        const minDim = Math.min(img.width, img.height)
        const sx = (img.width - minDim) / 2
        const sy = (img.height - minDim) / 2

        const canvas = document.createElement('canvas')
        canvas.width = TARGET_SIZE
        canvas.height = TARGET_SIZE
        const ctx = canvas.getContext('2d')

        // 白色背景（防止透明 PNG 转 JPEG 后变黑）
        ctx.fillStyle = '#FFFFFF'
        ctx.fillRect(0, 0, TARGET_SIZE, TARGET_SIZE)

        // 居中裁剪并绘制
        ctx.drawImage(img, sx, sy, minDim, minDim, 0, 0, TARGET_SIZE, TARGET_SIZE)

        canvas.toBlob(
          (blob) => {
            if (blob) {
              resolve(blob)
            } else {
              reject(new Error('图片压缩失败'))
            }
          },
          'image/jpeg',
          QUALITY
        )
      }
      img.onerror = () => reject(new Error('图片加载失败'))
      img.src = e.target.result
    }
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsDataURL(file)
  })
}

/**
 * 读取图片为 DataURL（用于预览）
 * @param {File} file
 * @returns {Promise<string>}
 */
export function readImageAsDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target.result)
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsDataURL(file)
  })
}
