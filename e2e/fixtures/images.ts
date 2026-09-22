// アップロードに使う画像。perf/k6/lib/api.js と同じ 1×1 px の PNG（約 70 バイト）を
// 文字列で持ち、リポジトリに画像ファイルを置かずに済ませる。
// 測れるのは「形式検証 + S3 への保存 + 署名付き URL の表示」の往復で、転送量の影響は含まない。
export interface ImageFile {
  name: string
  mimeType: string
  buffer: Buffer
}

const TINY_PNG_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='

export function tinyPng(name = 'tiny.png'): ImageFile {
  return { name, mimeType: 'image/png', buffer: Buffer.from(TINY_PNG_BASE64, 'base64') }
}

/** PNG を名乗るだけの中身が違うファイル。サーバー側の形式検証（マジックバイト）を通らない */
export function fakePng(name = 'fake.png'): ImageFile {
  return { name, mimeType: 'image/png', buffer: Buffer.from('this is not a png') }
}
