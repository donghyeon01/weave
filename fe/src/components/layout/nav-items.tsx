import type { ComponentType, SVGProps } from 'react'
import {
  CalendarIcon,
  ClockIcon,
  DiaryIcon,
  FriendsIcon,
  MailIcon,
  MessageIcon,
  ProfileIcon,
  SparklesIcon,
  TodoIcon,
} from './nav-icons'

export type NavBadge =
  | 'todo-count'
  | 'friend-requests'
  | 'new'
  | 'proposal-pending'
  | 'chat-unread'

export interface NavItem {
  label: string
  to: string
  icon: ComponentType<SVGProps<SVGSVGElement>>
  badge?: NavBadge
}

// 시각 계약 원본은 Figma 4:292 사이드바 — 순서·라벨을 프레임과 맞춘다.
// 모바일 TopNav는 같은 목록을 텍스트만으로 렌더한다 (top-nav.md).
export const NAV_ITEMS: NavItem[] = [
  { label: '할 일', to: '/app/todo', icon: TodoIcon, badge: 'todo-count' },
  { label: '캘린더', to: '/app/calendar', icon: CalendarIcon },
  { label: '친구', to: '/app/friends', icon: FriendsIcon, badge: 'friend-requests' },
  { label: '일정 조율', to: '/app/scheduling', icon: SparklesIcon, badge: 'new' },
  { label: '가능 시간', to: '/app/availability', icon: ClockIcon },
  { label: '제안 보관함', to: '/app/proposals', icon: MailIcon, badge: 'proposal-pending' },
  { label: '채팅', to: '/app/chat', icon: MessageIcon, badge: 'chat-unread' },
  { label: '일기', to: '/app/diary', icon: DiaryIcon },
  { label: '내 프로필', to: '/app/profile', icon: ProfileIcon },
]
