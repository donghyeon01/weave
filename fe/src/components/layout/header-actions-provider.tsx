import { useState } from 'react'
import type { ReactNode } from 'react'
import { HeaderActionsContext, SetHeaderActionsContext } from './header-actions'

export function HeaderActionsProvider({ children }: { children: ReactNode }) {
  const [actions, setActions] = useState<ReactNode>(null)
  return (
    <SetHeaderActionsContext.Provider value={setActions}>
      <HeaderActionsContext.Provider value={actions}>
        {children}
      </HeaderActionsContext.Provider>
    </SetHeaderActionsContext.Provider>
  )
}
