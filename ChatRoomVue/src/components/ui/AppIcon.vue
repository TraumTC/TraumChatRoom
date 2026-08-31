<!-- src/components/ui/AppIcon.vue — 统一 Lucide 图标封装 -->
<template>
  <component :is="iconComponent" :size="size" :class="className" :stroke-width="1.8" />
</template>

<script setup>
import { computed } from 'vue'
import {
  ArrowLeft, AtSign, BadgeCheck, Bell, Bot, ChevronDown, ChevronLeft, Circle,
  CornerUpLeft, Download, Ellipsis, Eye, FileText, ImageOff, Lock, LogIn, LogOut,
  Menu, MessageSquare, MessagesSquare, Paperclip, Plus, Radio, RefreshCw,
  RotateCcw, Search, Send, Smile, User, UserRoundPlus, Users, Video, X
} from '@lucide/vue'

const icons = {
  'arrow-left': ArrowLeft,
  'at-sign': AtSign,
  'badge-check': BadgeCheck,
  bell: Bell,
  bot: Bot,
  'chevron-down': ChevronDown,
  'chevron-left': ChevronLeft,
  circle: Circle,
  'corner-up-left': CornerUpLeft,
  download: Download,
  ellipsis: Ellipsis,
  eye: Eye,
  'file-text': FileText,
  'image-off': ImageOff,
  lock: Lock,
  'log-in': LogIn,
  'log-out': LogOut,
  menu: Menu,
  'message-square': MessageSquare,
  'messages-square': MessagesSquare,
  paperclip: Paperclip,
  plus: Plus,
  radio: Radio,
  'refresh-cw': RefreshCw,
  'rotate-ccw': RotateCcw,
  search: Search,
  send: Send,
  smile: Smile,
  user: User,
  'user-round-plus': UserRoundPlus,
  users: Users,
  video: Video,
  x: X
}

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 16 },
  className: { type: String, default: '' }
})

const iconComponent = computed(() => {
  return icons[props.name] || Circle
})
</script>
