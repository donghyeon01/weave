import { useEffect, useState } from 'react'
import { Toast } from './toast'

interface QueryErrorToastProps {
  /** 쿼리의 isError — true 동안 표시, 닫으면 같은 오류 주기에는 재표시하지 않는다 */
  show: boolean
  message: string
}

// 시안 오류 상태 프레임(할 일 116:845·친구 116:3173) — 목록 로딩 실패는 danger 토스트로 신호.
export function QueryErrorToast({ show, message }: QueryErrorToastProps) {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    if (show) setVisible(true)
  }, [show])

  if (!visible) return null
  return <Toast variant="danger" message={message} onClose={() => setVisible(false)} />
}
