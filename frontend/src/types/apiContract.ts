/**
 * 手書きの API 型と、API 仕様書から生成した型（api.generated.ts）の一致検査。
 *
 * 手書き型を残しているのは、画面や機能の文脈に沿った日本語コメントを
 * フィールド単位で書けるため。その代わりバックエンドの DTO とズレるリスクを
 * このファイルの型アサーションで塞ぐ。バックエンドが DTO を変更すると
 * `npm run generate:api` で api.generated.ts が変わり、ここが tsc エラーになる。
 *
 * エラーになったら: `Type 'false' does not satisfy the constraint 'true'` の行の
 * 型名を見て、手書き型（auth.ts / user.ts / post.ts / comment.ts）を
 * api.generated.ts の同名スキーマに合わせて直すこと。
 *
 * ApiError（バックエンドの ErrorResponse）はエラーレスポンスを仕様書に
 * 載せていないため検査できない。@ApiResponse の整備（#39 スコープ外）後に追加する。
 */
import type { components } from './api.generated'
import type { AuthResponse, LoginRequest, SignupRequest, UserResponse } from './auth'
import type { CommentListResponse, CommentRequest, CommentResponse } from './comment'
import type { LikeResponse, PostAuthor, PostRequest, PostResponse, TimelineResponse } from './post'
import type {
  FollowResponse,
  ProfileResponse,
  UpdateProfileRequest,
  UserSearchResponse,
  UserSummary,
} from './user'

type Schemas = components['schemas']

/** A と B が相互に代入可能（= 同じ形）なら true */
type Equals<A, B> = [A] extends [B] ? ([B] extends [A] ? true : false) : false

type Expect<T extends true> = T

// auth.ts
export type CheckUserResponse = Expect<Equals<UserResponse, Schemas['UserResponse']>>
export type CheckSignupRequest = Expect<Equals<SignupRequest, Schemas['SignupRequest']>>
export type CheckLoginRequest = Expect<Equals<LoginRequest, Schemas['LoginRequest']>>
export type CheckAuthResponse = Expect<Equals<AuthResponse, Schemas['AuthResponse']>>

// user.ts
export type CheckProfileResponse = Expect<Equals<ProfileResponse, Schemas['ProfileResponse']>>
export type CheckUserSummary = Expect<Equals<UserSummary, Schemas['UserSummary']>>
export type CheckUserSearchResponse = Expect<Equals<UserSearchResponse, Schemas['UserSearchResponse']>>
export type CheckFollowResponse = Expect<Equals<FollowResponse, Schemas['FollowResponse']>>
export type CheckUpdateProfileRequest = Expect<Equals<UpdateProfileRequest, Schemas['UpdateProfileRequest']>>

// post.ts
export type CheckPostAuthor = Expect<Equals<PostAuthor, Schemas['PostAuthor']>>
export type CheckPostResponse = Expect<Equals<PostResponse, Schemas['PostResponse']>>
export type CheckLikeResponse = Expect<Equals<LikeResponse, Schemas['LikeResponse']>>
export type CheckPostRequest = Expect<Equals<PostRequest, Schemas['PostRequest']>>
export type CheckTimelineResponse = Expect<Equals<TimelineResponse, Schemas['TimelineResponse']>>

// comment.ts
export type CheckCommentResponse = Expect<Equals<CommentResponse, Schemas['CommentResponse']>>
export type CheckCommentRequest = Expect<Equals<CommentRequest, Schemas['CommentRequest']>>
export type CheckCommentListResponse = Expect<Equals<CommentListResponse, Schemas['CommentListResponse']>>
