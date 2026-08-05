import { Component, type ErrorInfo, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
  /** 例外を捕まえたときに代わりに描画する内容。省略すると投稿カードに馴染む既定の 1 行を出す */
  fallback?: ReactNode
}

interface ErrorBoundaryState {
  failed: boolean
}

/**
 * 配下のレンダリングで起きた例外を受け止め、画面全体が落ちるのを防ぐ。
 *
 * React は例外を捕まえる境界が無いとツリー全体をアンマウントするため、
 * 投稿 1 件のデータ不整合でもタイムラインごと真っ白になる。実際、API が
 * `imageUrls` を返さないバージョンのバックエンドに繋いだときにこれが起きた。
 * フロント（S3 + CloudFront）とバックエンド（ALB + EC2）は別々にデプロイされ、
 * フロントは受け取った JSON を実行時に検証していないので、両者のバージョンが
 * ずれれば同じことが再発しうる（docs/09_infrastructure.md 11.5）。
 *
 * 例外捕捉に相当するフックは React に無いので、クラスコンポーネントで書く。
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // 握り潰すと「一部だけ表示されない」という最も追いにくい壊れ方になる。
    // 原因を追えるよう、どのコンポーネントで落ちたかまで残す
    console.error('ErrorBoundary が例外を捕捉しました:', error, errorInfo.componentStack)
  }

  render(): ReactNode {
    if (!this.state.failed) return this.props.children

    return (
      this.props.fallback ?? (
        <p className="border-b border-border px-4 py-3 text-sm text-muted">
          この内容は表示できませんでした。
        </p>
      )
    )
  }
}
