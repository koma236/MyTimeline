import { useLocation } from 'react-router-dom'

/** 画面遷移の検証用。ui 以外のパスに来たら現在地を表示する */
export function LocationProbe() {
  const { pathname } = useLocation()
  return <p data-testid="location">{pathname}</p>
}
