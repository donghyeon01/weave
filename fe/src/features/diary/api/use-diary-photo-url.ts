import { useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../../lib/api-client'

/**
 * 사진 바이트를 인증 fetch(blob)로 받아 objectURL로 반환한다.
 * <img src>는 Authorization 헤더를 못 보내므로 직접 경로를 쓸 수 없다.
 * url이 바뀌면 이전 objectURL은 revoke한다.
 */
export function useDiaryPhotoUrl(date: string, photoId: number): string | null {
  const query = useQuery({
    queryKey: ['diary-photo', photoId],
    queryFn: () =>
      apiFetch<Blob>(`/api/diaries/${date}/photos/${photoId}`, { responseType: 'blob' }),
    staleTime: Infinity,
  })

  const url = useMemo(
    () => (query.data ? URL.createObjectURL(query.data) : null),
    [query.data],
  )

  useEffect(() => {
    return () => {
      if (url) URL.revokeObjectURL(url)
    }
  }, [url])

  return url
}
