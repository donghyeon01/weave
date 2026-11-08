import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch } from '../../../lib/api-client'
import { createQueryClient } from '../../../lib/react-query'
import type { DiaryPhotoMeta } from '../types'
import { DiaryPhotos } from './diary-photos'

vi.mock('../../../lib/api-client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../lib/api-client')>()
  return { ...actual, apiFetch: vi.fn() }
})

const apiFetchMock = vi.mocked(apiFetch)

const photo = (id: number): DiaryPhotoMeta => ({
  id,
  contentType: 'image/jpeg',
  sizeBytes: 8,
  sortOrder: id - 1,
})

function renderStrip(photos: DiaryPhotoMeta[], enabled = true) {
  const queryClient = createQueryClient()
  render(
    <QueryClientProvider client={queryClient}>
      <DiaryPhotos date="2027-06-01" photos={photos} enabled={enabled} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  apiFetchMock.mockReset()
  apiFetchMock.mockResolvedValue(new Blob([new Uint8Array([1, 2])]))
  vi.stubGlobal('URL', {
    ...URL,
    createObjectURL: vi.fn(() => 'blob:mock-url'),
    revokeObjectURL: vi.fn(),
  })
})

describe('diary-photos', () => {
  it('미저장 일기는 안내 문구를 보여주고 추가 버튼이 없다', () => {
    renderStrip([], false)

    expect(
      screen.getByText('일기를 먼저 저장하면 사진을 첨부할 수 있어요.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '사진 추가' })).not.toBeInTheDocument()
  })

  it('파일을 고르면 FormData로 업로드 엔드포인트를 호출한다', async () => {
    renderStrip([])
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    const file = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'p.jpg', {
      type: 'image/jpeg',
    })

    fireEvent.change(input, { target: { files: [file] } })

    await waitFor(() => {
      expect(apiFetchMock).toHaveBeenCalledWith(
        '/api/diaries/2027-06-01/photos',
        expect.objectContaining({ method: 'POST', body: expect.any(FormData) }),
      )
    })
  })

  it('4장이면 추가 버튼을 숨기고 삭제는 확인 모달을 거친다', async () => {
    renderStrip([photo(1), photo(2), photo(3), photo(4)])

    expect(screen.queryByRole('button', { name: '사진 추가' })).not.toBeInTheDocument()

    fireEvent.click(screen.getAllByRole('button', { name: '사진 삭제' })[0])
    fireEvent.click(await screen.findByRole('button', { name: '삭제' }))

    await waitFor(() => {
      expect(apiFetchMock).toHaveBeenCalledWith('/api/diaries/2027-06-01/photos/1', {
        method: 'DELETE',
      })
    })
  })
})
