import {
  AxiosError,
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

type ClientModule = typeof import('./client')

function respond(
  config: InternalAxiosRequestConfig,
  status: number,
  data: unknown,
): AxiosResponse {
  return { data, status, statusText: '', headers: new AxiosHeaders(), config }
}

function reject(
  config: InternalAxiosRequestConfig,
  status: number,
  data: unknown = { message: '認証が必要です' },
): Promise<AxiosResponse> {
  return Promise.reject(
    new AxiosError('failed', AxiosError.ERR_BAD_REQUEST, config, {}, respond(config, status, data)),
  )
}

describe('apiClient の 401 ハンドリング', () => {
  let client: ClientModule
  /** アダプタが受け取ったリクエスト設定。呼ばれた順に積む */
  let calls: InternalAxiosRequestConfig[]

  /**
   * HTTP を実際には出さず、テストごとに応答を組み立てる。
   * refreshPromise / accessToken はモジュールレベルの状態なので、
   * テスト間で漏れないよう resetModules で読み直している。
   */
  function useAdapter(handler: (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>) {
    client.apiClient.defaults.adapter = ((config: InternalAxiosRequestConfig) => {
      calls.push(config)
      return handler(config)
    }) as AxiosAdapter
  }

  beforeEach(async () => {
    vi.resetModules()
    calls = []
    client = await import('./client')
  })

  it('401 を受けたらリフレッシュして 1 度だけ再送する', async () => {
    client.setAccessToken('古いトークン')
    let timelineCalls = 0
    useAdapter((config) => {
      if (config.url === '/auth/refresh') {
        return Promise.resolve(respond(config, 200, { accessToken: '新しいトークン' }))
      }
      timelineCalls += 1
      return timelineCalls === 1
        ? reject(config, 401)
        : Promise.resolve(respond(config, 200, { posts: [] }))
    })

    const response = await client.apiClient.get('/timeline/all')

    expect(response.data).toEqual({ posts: [] })
    expect(calls.map((config) => config.url)).toEqual([
      '/timeline/all',
      '/auth/refresh',
      '/timeline/all',
    ])
    // 再送は取り直したトークンで行われる（古いトークンのままなら無限に 401 が続く）
    expect(calls.at(-1)?.headers.Authorization).toBe('Bearer 新しいトークン')
  })

  it('再送も 401 なら諦め、2 度目のリフレッシュはしない', async () => {
    useAdapter((config) =>
      config.url === '/auth/refresh'
        ? Promise.resolve(respond(config, 200, { accessToken: '新しいトークン' }))
        : reject(config, 401),
    )

    await expect(client.apiClient.get('/timeline/all')).rejects.toBeInstanceOf(AxiosError)

    expect(calls.map((config) => config.url)).toEqual([
      '/timeline/all',
      '/auth/refresh',
      '/timeline/all',
    ])
  })

  it('同時に起きた 401 を 1 本のリフレッシュにまとめる', async () => {
    // 2 本走るとローテーション済みトークンの再提示になり、
    // バックエンドが盗用とみなして全セッションを失効させてしまう
    let refreshCalls = 0
    const unauthorized = new Set<string>()
    useAdapter((config) => {
      if (config.url === '/auth/refresh') {
        refreshCalls += 1
        return new Promise((resolve) => {
          setTimeout(() => resolve(respond(config, 200, { accessToken: '新しいトークン' })), 10)
        })
      }
      const url = config.url ?? ''
      if (!unauthorized.has(url)) {
        unauthorized.add(url)
        return reject(config, 401)
      }
      return Promise.resolve(respond(config, 200, { ok: url }))
    })

    const [timeline, comments] = await Promise.all([
      client.apiClient.get('/timeline/all'),
      client.apiClient.get('/posts/1/comments'),
    ])

    expect(refreshCalls).toBe(1)
    expect(timeline.data).toEqual({ ok: '/timeline/all' })
    expect(comments.data).toEqual({ ok: '/posts/1/comments' })
  })

  it('リフレッシュにも失敗したらトークンを捨ててセッション切れを通知する', async () => {
    const onSessionExpired = vi.fn()
    client.setOnSessionExpired(onSessionExpired)
    client.setAccessToken('古いトークン')
    useAdapter((config) => reject(config, 401))

    await expect(client.apiClient.get('/timeline/all')).rejects.toBeInstanceOf(AxiosError)
    expect(onSessionExpired).toHaveBeenCalledTimes(1)

    // 以降のリクエストに Authorization が付かないこと＝トークンが捨てられていること
    useAdapter((config) => Promise.resolve(respond(config, 200, {})))
    await client.apiClient.get('/timeline/all')
    expect(calls.at(-1)?.headers.Authorization).toBeUndefined()
  })

  it('ログインの 401 はリフレッシュせずそのまま返す', async () => {
    // ここでの 401 は「認証情報が違う」という結果そのもの。
    // リフレッシュして再送すると画面にエラーを出せなくなる
    useAdapter((config) => reject(config, 401))

    await expect(client.apiClient.post('/auth/login', {})).rejects.toBeInstanceOf(AxiosError)

    expect(calls.map((config) => config.url)).toEqual(['/auth/login'])
  })

  it('401 以外のエラーはそのまま呼び出し元へ返す', async () => {
    useAdapter((config) => reject(config, 403, { message: '権限がありません' }))

    await expect(client.apiClient.delete('/posts/1')).rejects.toBeInstanceOf(AxiosError)

    expect(calls.map((config) => config.url)).toEqual(['/posts/1'])
  })
})

describe('toApiError', () => {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig

  it('バックエンドが返したメッセージと fieldErrors をそのまま渡す', async () => {
    const { toApiError } = await import('./client')
    const body = { message: '入力内容を確認してください', fieldErrors: { body: '本文は必須です' } }

    expect(toApiError(new AxiosError('failed', '400', config, {}, respond(config, 400, body)))).toEqual(
      body,
    )
  })

  it('レスポンスが無ければ接続エラーとして扱う', async () => {
    const { toApiError } = await import('./client')

    expect(toApiError(new AxiosError('Network Error', AxiosError.ERR_NETWORK, config))).toEqual({
      message: 'サーバーに接続できませんでした。時間をおいて再度お試しください',
    })
  })

  it('レスポンスはあるがメッセージが無ければ既定の文言にする', async () => {
    const { toApiError } = await import('./client')

    expect(toApiError(new AxiosError('failed', '500', config, {}, respond(config, 500, {})))).toEqual({
      message: '予期しないエラーが発生しました',
    })
  })

  it('axios 由来でない例外も既定の文言にする', async () => {
    const { toApiError } = await import('./client')

    expect(toApiError(new TypeError('想定外'))).toEqual({
      message: '予期しないエラーが発生しました',
    })
  })
})
