import type { TimelineTab } from '../types/post'

const TABS: { value: TimelineTab; label: string }[] = [
  { value: 'following', label: 'フォロー中' },
  { value: 'all', label: 'すべて' },
]

interface TimelineTabsProps {
  active: TimelineTab
  onChange: (tab: TimelineTab) => void
}

/** タイムラインのタブ切替（mock/css/style.css の .tabs 相当・SCR-03）。 */
export function TimelineTabs({ active, onChange }: TimelineTabsProps) {
  return (
    <div role="tablist" className="sticky top-[49px] z-10 flex border-b border-border bg-bg/90 backdrop-blur">
      {TABS.map((tab) => (
        <button
          key={tab.value}
          type="button"
          role="tab"
          aria-selected={active === tab.value}
          onClick={() => onChange(tab.value)}
          className={`relative flex-1 py-4 text-[15px] font-bold transition-colors hover:bg-bg-subtle ${
            active === tab.value ? 'text-text' : 'text-muted'
          }`}
        >
          {tab.label}
          {active === tab.value && (
            <span className="absolute inset-x-0 bottom-0 mx-auto h-1 w-14 rounded-full bg-accent" />
          )}
        </button>
      ))}
    </div>
  )
}
