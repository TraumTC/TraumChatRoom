/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        bg: '#F7F7F5',
        card: '#FFFFFF',
        hover: '#F3F4F6',
        ghost: '#F9FAFB',
        border: '#E5E7EB',
        ink: '#1F2328',
        'ink-soft': '#6B7280',
        'ink-faint': '#9CA3AF',
        signal: {
          DEFAULT: '#3B82F6',
          deep: '#2563EB',
          ghost: '#EFF6FF',
        },
        live: '#10B981',
        alarm: '#EF4444',
        warn: '#F59E0B',
      },
      fontFamily: {
        sans: ["-apple-system", "BlinkMacSystemFont", "'Segoe UI'", "Roboto", "'PingFang SC'", "'Microsoft YaHei'", "sans-serif"],
      },
      boxShadow: {
        card: '0 4px 24px rgba(0, 0, 0, 0.06), 0 1px 3px rgba(0, 0, 0, 0.04)',
        'card-hover': '0 8px 32px rgba(0, 0, 0, 0.08), 0 2px 6px rgba(0, 0, 0, 0.04)',
        'btn-hover': '0 4px 12px rgba(59, 130, 246, 0.25)',
        'btn-active': '0 1px 4px rgba(59, 130, 246, 0.15)',
        'feature-hover': '0 8px 25px rgba(0, 0, 0, 0.07)',
      },
      keyframes: {
        signalPulse: {
          '0%': { opacity: '0', transform: 'translateY(6px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        'signal-pulse': 'signalPulse 300ms ease-out both',
      },
    },
  },
  plugins: [],
}
