import { createContext, useContext } from 'react'
import type { Dispatch, ReactNode, SetStateAction } from 'react'

// 데스크톱 콘텐츠 헤더(시안 Header - TopNavBar) 우측 클러스터에 화면별 액션을
// 주입하는 채널 — 화면은 useHeaderActionsSetter로 액션 노드를 올리고,
// ContentHeader가 useHeaderActionsValue로 꺼내 렌더한다. 모바일 탑바에는 미표시.
//
// 사용 패턴 — 화면이 자기 useEffect에서 등록·회수한다:
//   const setActions = useHeaderActionsSetter()
//   useEffect(() => {
//     setActions(<Button …/>)
//     return () => setActions(null)
//   }, [setActions, /* 화면별 의존 */])
export const HeaderActionsContext = createContext<ReactNode>(null)
export const SetHeaderActionsContext = createContext<Dispatch<SetStateAction<ReactNode>>>(
  () => {},
)

export function useHeaderActionsSetter(): Dispatch<SetStateAction<ReactNode>> {
  return useContext(SetHeaderActionsContext)
}

export function useHeaderActionsValue(): ReactNode {
  return useContext(HeaderActionsContext)
}
