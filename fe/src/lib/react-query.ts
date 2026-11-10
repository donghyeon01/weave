import { QueryClient } from '@tanstack/react-query'

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: 1,
      },
    },
  })
}

// auth-store·api-client 같은 비React 모듈도 같은 캐시를 써야 하므로 싱글턴으로 노출한다.
// provider는 이 인스턴스를 QueryClientProvider에 전달한다.
export const queryClient = createQueryClient()

export const queryKeys = {
  users: {
    me: ['users', 'me'],
    search: (email: string) => ['users', 'search', email],
  },
  tasks: {
    all: ['tasks'],
    list: ['tasks', 'list'],
  },
  events: {
    all: ['events'],
    range: (start: string, end: string) => ['events', 'range', start, end],
  },
  diaries: {
    month: (yearMonth: string) => ['diaries', 'month', yearMonth],
    detail: (date: string) => ['diaries', 'detail', date],
  },
  friends: {
    all: ['friends'],
    list: ['friends', 'list'],
    requestsReceived: ['friends', 'requests', 'received'],
    requestsSent: ['friends', 'requests', 'sent'],
    feedSubscriptions: ['friends', 'feed-subscriptions'],
  },
  friendGroups: {
    all: ['friendGroups'],
  },
  share: {
    all: ['share'],
    mine: ['share', 'mine'],
    feed: ['share', 'feed'],
    byOwner: (userId: number) => ['share', 'by', userId],
    detail: (shareId: number) => ['share', 'detail', shareId],
    replies: (shareId: number) => ['share', 'detail', shareId, 'replies'],
  },
  notifications: {
    list: (page?: number, size?: number) => ['notifications', 'list', page, size],
  },
  scheduling: {
    proposals: (box: 'received' | 'sent', page?: number, size?: number) => [
      'scheduling',
      'proposals',
      box,
      page,
      size,
    ],
    proposal: (id: number) => ['scheduling', 'proposals', 'detail', id],
  },
  chat: {
    rooms: ['chat', 'rooms'],
    messages: (roomId: number) => ['chat', 'rooms', roomId, 'messages'],
  },
} as const
