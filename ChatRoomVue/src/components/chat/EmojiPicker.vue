<!-- src/components/chat/EmojiPicker.vue — 表情选择弹窗（向上弹出，带下方三角指示） -->
<template>
  <div class="emoji-picker">
    <!-- Category tabs -->
    <div class="emoji-tabs">
      <button v-for="(cat, idx) in categories" :key="cat.name"
              class="emoji-tab"
              :class="{ active: activeTab === idx }"
              @click="activeTab = idx">
        {{ cat.icon }}
      </button>
    </div>
    <!-- Emoji grid -->
    <div class="emoji-grid scroll-thin">
      <button v-for="emoji in categories[activeTab].emojis"
              :key="emoji"
              class="emoji-item"
              @click="$emit('select', emoji)">
        {{ emoji }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineEmits(['select'])

const activeTab = ref(0)

const categories = [
  {
    name: '表情',
    icon: '😀',
    emojis: ['😀', '😃', '😄', '😁', '😆', '😅', '😂', '🤣', '😊', '😇', '🙂', '🙃', '😉', '😌', '😍', '🥰', '😘', '😗', '😙', '😚', '😋', '😛', '😝', '😜', '🤪', '🤨', '🧐', '🤓', '😎', '🤩', '🥳', '😏', '😒', '😞', '😔', '😟', '😕', '🙁', '😣', '😖', '😫', '😩', '🥺', '😢', '😭', '😤', '😠', '😡', '🤬', '🤯', '😳', '🥵', '🥶', '😱', '😨', '😰', '😥', '😓', '🤗', '🤔', '🤭', '🤫', '🤥', '😶', '😐', '😑', '😬', '🙄', '😮', '😯', '😧', '😮‍💨', '😷', '🤒', '🤕', '🤢', '🤮', '🥴', '😴']
  },
  {
    name: '手势',
    icon: '👍',
    emojis: ['👍', '👎', '👌', '✌️', '🤞', '🤟', '🤘', '🤙', '👈', '👉', '👆', '👇', '☝️', '✋', '🤚', '🖐', '🖖', '👋', '🤝', '🙏', '✍️', '💪', '🦾', '👏', '🙌', '👐', '🤲', '🤜', '🤛', '👊', '✊']
  },
  {
    name: '自然',
    icon: '🌸',
    emojis: ['🌅', '🌄', '🌠', '🎇', '🎆', '🌇', '🌆', '🏙', '🌃', '🌉', '🌌', '🌎', '🌍', '🌏', '🌑', '🌒', '🌓', '🌔', '🌕', '🌖', '🌗', '🌘', '🌙', '🌚', '🌛', '🌜', '⭐', '🌟', '✨', '⚡', '☄', '💥', '🔥', '🌪', '🌈', '☀', '🌤', '⛅', '🌥', '☁', '🌦', '🌧', '⛈', '🌩', '🌨', '❄', '⛄', '☔', '☂', '🌊', '💧', '🌸', '🌺', '🌻', '🌹', '🌷', '🌼', '🌱', '🌲', '🌳', '🌴', '🌵', '🌾', '🌿', '🍀', '🍁', '🍂', '🍃']
  },
  {
    name: '物品',
    icon: '💡',
    emojis: ['💻', '⌨', '🖥', '🖨', '🖱', '🖲', '💽', '💾', '💿', '📀', '📱', '📲', '☎', '📞', '📟', '📠', '🔋', '🔌', '💡', '🔦', '🕯', '🗑', '🛠', '⚒', '🔨', '⛏', '⚙', '⛓', '🔫', '💣', '🔪', '🗡', '⚔', '🛡', '🚬', '⚰', '⚱', '🏺', '🔮', '📿', '💈', '⚗', '🔭', '📡', '💉', '🩸', '💊', '🩹', '🩺', '🚪', '🛏', '🛋', '🚽', '🚿', '🛁', '🧴', '🧷', '🧹', '🧺', '🧻', '🧼', '🧽', '🧯', '🛒']
  },
  {
    name: '符号',
    icon: '❤',
    emojis: ['❤', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '🤎', '💔', '❣', '💕', '💞', '💓', '💗', '💖', '💘', '💝', '💟', '☮', '✝', '☪', '🕉', '☸', '✡', '🔯', '🕎', '☯', '🔯', '📛', '🔰', '⭕', '✅', '☑', '✔', '✖', '❌', '❎', '➕', '➖', '➗', '➰', '➿', '〽', '✳', '✅', '❇', '©', '®', '™', '#️⃣', '*️⃣', '0️⃣', '1️⃣']
  }
]
</script>

<style scoped>
.emoji-picker {
  position: absolute;
  bottom: 100%;
  left: 0;
  margin-bottom: 12px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 50;
  width: 320px;
  overflow: hidden;
}

/* 向下三角指示 */
.emoji-picker::after {
  content: '';
  position: absolute;
  bottom: -7px;
  left: 24px;
  width: 12px;
  height: 12px;
  background: var(--color-card);
  border-right: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  transform: rotate(45deg);
}

.emoji-tabs {
  display: flex;
  border-bottom: 1px solid var(--color-border);
  padding: 4px 4px 0;
}

.emoji-tab {
  flex: 1;
  padding: 8px;
  text-align: center;
  cursor: pointer;
  font-size: 18px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  transition: background 0.15s ease;
  color: var(--color-ink-soft);
}

.emoji-tab:hover {
  background: var(--color-hover);
}

.emoji-tab.active {
  border-bottom-color: var(--color-signal);
  color: var(--color-ink);
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  max-height: 200px;
  overflow-y: auto;
  padding: 8px;
}

.emoji-item {
  font-size: 22px;
  padding: 4px;
  text-align: center;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 6px;
  transition: background 0.15s ease;
  color: var(--color-ink);
  line-height: 1.4;
}

.emoji-item:hover {
  background: var(--color-hover);
}
</style>
