import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PostResponse } from '../types/post'
import { MAX_IMAGES, PostComposer } from './PostComposer'

vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    user: { id: 1, username: 'saki', displayName: 'さき', avatarUrl: null },
  }),
}))

// jsdom には createObjectURL が無い。プレビュー用の URL はダミーで賄う
let objectUrlSeq = 0
URL.createObjectURL = vi.fn(() => `blob:preview-${++objectUrlSeq}`)
URL.revokeObjectURL = vi.fn()

function imageFile(name: string): File {
  return new File([new Uint8Array([1, 2, 3])], name, { type: 'image/jpeg' })
}

function created(): PostResponse {
  return {
    id: 10,
    body: '',
    author: { id: 1, username: 'saki', displayName: 'さき', avatarUrl: null },
    imageUrls: ['https://s3.example/1'],
    likeCount: 0,
    commentCount: 0,
    likedByMe: false,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  }
}

function selectImages(files: File[]) {
  fireEvent.change(screen.getByLabelText('添付する画像を選択'), { target: { files } })
}

/**
 * 画像添付まわりの受け付けを押さえる。形式・サイズの検証はサーバーの責務なので
 * ここでは扱わない（PostComposer は枚数と「本文か画像のどちらかは必要」だけを見る）。
 */
describe('PostComposer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('本文が空でも画像があれば投稿でき、選んだファイルを渡す', async () => {
    const onSubmit = vi.fn().mockResolvedValue(created())
    const onCreated = vi.fn()
    render(<PostComposer onSubmit={onSubmit} onCreated={onCreated} />)

    // 本文も画像も無い間は投稿できない
    expect(screen.getByRole('button', { name: '投稿' })).toBeDisabled()

    const file = imageFile('a.jpg')
    selectImages([file])
    expect(screen.getByAltText('添付画像1')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '投稿' }))

    await waitFor(() => expect(onCreated).toHaveBeenCalled())
    expect(onSubmit).toHaveBeenCalledWith('', [file])
    // 成功したらプレビューは片付く
    expect(screen.queryByAltText('添付画像1')).not.toBeInTheDocument()
  })

  it('取り外した画像は送信されない', async () => {
    const onSubmit = vi.fn().mockResolvedValue(created())
    render(<PostComposer onSubmit={onSubmit} onCreated={vi.fn()} />)

    const keep = imageFile('keep.jpg')
    selectImages([keep, imageFile('remove.jpg')])
    fireEvent.click(screen.getByRole('button', { name: '添付画像2を取り外す' }))

    fireEvent.change(screen.getByLabelText('投稿の本文'), { target: { value: '本文' } })
    fireEvent.click(screen.getByRole('button', { name: '投稿' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('本文', [keep]))
  })

  it('上限を超える枚数はエラーを表示して受け付けない', async () => {
    render(<PostComposer onSubmit={vi.fn()} onCreated={vi.fn()} />)

    selectImages(
      Array.from({ length: MAX_IMAGES + 1 }, (_, index) => imageFile(`over-${index}.jpg`)),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('画像は4枚まで添付できます')
    // 1 枚も受け付けていないので投稿もできない
    expect(screen.queryByAltText('添付画像1')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '投稿' })).toBeDisabled()
  })

  it('上限まで添付すると画像の追加ボタンが無効になる', () => {
    render(<PostComposer onSubmit={vi.fn()} onCreated={vi.fn()} />)

    selectImages(Array.from({ length: MAX_IMAGES }, (_, index) => imageFile(`full-${index}.jpg`)))

    expect(screen.getByRole('button', { name: '画像を添付' })).toBeDisabled()
  })
})
