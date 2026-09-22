// テストデータを API で直接投入するためのクライアント。
//
// 画面を操作して前提を作ると 1 テストが長くなり、前提づくりの失敗と本題の失敗が区別できなくなる。
// 「確かめたい操作」だけを画面で行い、それ以外の前提（相手ユーザー・既存の投稿・フォロー関係）は
// ここで作る。
//
// 注意: refresh は絶対に呼ばない。リフレッシュトークンはローテーションし、使用済みトークンの
// 再提示は盗用とみなしてそのユーザーの全セッションを失効させる（backend の RefreshTokenService）。
// ブラウザ側が持つ Cookie と食い違うと、テスト中の画面が突然ログアウトされる。
import type { APIRequestContext } from '@playwright/test'
import type { ImageFile } from './images'

export interface TestUser {
  id: number
  username: string
  displayName: string
  email: string
  password: string
  /** 15 分有効。テストは 30 秒で終わるので取り直さない */
  accessToken: string
}

export interface PostAuthor {
  id: number
  username: string
  displayName: string
  avatarUrl: string | null
}

export interface PostResponse {
  id: number
  body: string
  author: PostAuthor
  imageUrls: string[]
  likeCount: number
  commentCount: number
  likedByMe: boolean
  createdAt: string
  updatedAt: string
}

export interface CommentResponse {
  id: number
  postId: number
  body: string
  author: PostAuthor
  createdAt: string
  updatedAt: string
}

interface UserResponse {
  id: number
  username: string
  displayName: string
  bio: string | null
  avatarUrl: string | null
}

interface AuthResponse {
  accessToken: string
  user: UserResponse
}

/** 全テストユーザー共通のパスワード（SignupRequest の 8 文字以上を満たす） */
export const PASSWORD = 'E2eTest1234'

// ユーザー名は実行ごと・worker ごとに一意にする。run.sh がテーブルを空にしてから実行するが、
// 空にしない（E2E_KEEP_DATA=1）場合や並列 worker でも衝突しないようにしておく
const runTag = Date.now().toString(36)
let sequence = 0

export function nextUsername(): string {
  sequence += 1
  const worker = process.env.TEST_WORKER_INDEX ?? '0'
  return `e2e_${runTag}_w${worker}_${sequence}`
}

type Method = 'get' | 'post' | 'put' | 'delete'

export class ApiClient {
  constructor(private readonly request: APIRequestContext) {}

  private async call<T>(
    method: Method,
    path: string,
    options: { user?: TestUser; data?: unknown; multipart?: FormData } = {},
  ): Promise<T> {
    const headers: Record<string, string> = {}
    if (options.user) headers.Authorization = `Bearer ${options.user.accessToken}`
    const response = await this.request[method](path, {
      headers,
      data: options.data,
      multipart: options.multipart,
    })
    if (!response.ok()) {
      throw new Error(`${method.toUpperCase()} ${path} → ${response.status()}: ${await response.text()}`)
    }
    const text = await response.text()
    return (text ? JSON.parse(text) : undefined) as T
  }

  /** 一意なユーザーを新規登録する。label は表示名の接頭辞（画面上で見分けるため） */
  async signup(label = 'ユーザー'): Promise<TestUser> {
    const username = nextUsername()
    const credentials = {
      username,
      displayName: `${label}${sequence}`,
      email: `${username}@example.com`,
      password: PASSWORD,
    }
    const response = await this.call<AuthResponse>('post', '/api/auth/signup', { data: credentials })
    return { ...credentials, id: response.user.id, accessToken: response.accessToken }
  }

  async createPost(user: TestUser, body: string, images: ImageFile[] = []): Promise<PostResponse> {
    const form = new FormData()
    form.append('body', body)
    for (const image of images) {
      form.append('images', new Blob([new Uint8Array(image.buffer)], { type: image.mimeType }), image.name)
    }
    return this.call<PostResponse>('post', '/api/posts', { user, multipart: form })
  }

  /** 連番の投稿を古い順に作る。無限スクロールやページングの前提づくり用 */
  async createPosts(user: TestUser, count: number, prefix: string): Promise<PostResponse[]> {
    const posts: PostResponse[] = []
    for (let i = 1; i <= count; i += 1) {
      posts.push(await this.createPost(user, `${prefix} ${i}`))
    }
    return posts
  }

  async createComment(user: TestUser, postId: number, body: string): Promise<CommentResponse> {
    return this.call<CommentResponse>('post', `/api/posts/${postId}/comments`, { user, data: { body } })
  }

  async likePost(user: TestUser, postId: number): Promise<void> {
    await this.call('post', `/api/posts/${postId}/like`, { user })
  }

  async follow(user: TestUser, targetUserId: number): Promise<void> {
    await this.call('post', `/api/users/${targetUserId}/follow`, { user })
  }

  async updateProfile(user: TestUser, profile: { displayName: string; bio: string }): Promise<void> {
    await this.call('put', '/api/users/me', { user, data: profile })
  }

  async uploadAvatar(user: TestUser, image: ImageFile): Promise<void> {
    const form = new FormData()
    form.append('file', new Blob([new Uint8Array(image.buffer)], { type: image.mimeType }), image.name)
    await this.call('put', '/api/users/me/avatar', { user, multipart: form })
  }
}
