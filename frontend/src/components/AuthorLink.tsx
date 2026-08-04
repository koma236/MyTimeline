import { Link } from 'react-router-dom'
import type { PostAuthor } from '../types/post'

interface AuthorLinkProps {
  author: PostAuthor
}

/**
 * 「表示名 @username」をプロフィール（SCR-05）へのリンクとして表示する。
 *
 * docs/06_ui_design.md 7.1 の共通ルール「投稿・コメント・検索結果に表示される
 * ユーザー名はすべてプロフィールへのリンクとする」を 1 箇所にまとめたもの。
 * 投稿カードとコメントカードで同じマークアップを書き写していたのを共通化している。
 *
 * カード全体をリンクにはしていないので、この中のリンクが入れ子になることはない。
 */
export function AuthorLink({ author }: AuthorLinkProps) {
  return (
    <Link
      to={`/users/${encodeURIComponent(author.username)}`}
      className="flex min-w-0 items-center gap-1.5"
    >
      <span className="truncate font-bold hover:underline">{author.displayName}</span>
      <span className="truncate text-sm text-muted">@{author.username}</span>
    </Link>
  )
}
