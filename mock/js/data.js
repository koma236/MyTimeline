/* ==========================================================================
   data.js - シードデータ
   docs/07_er_diagram.md のテーブル定義をそのまま JS オブジェクトに写したもの。
   本番では PostgreSQL の users / posts / post_images / comments / likes / follows
   に相当する。
   ========================================================================== */

(function (global) {
  'use strict';

  var MT = global.MT || (global.MT = {});

  /** 分前の ISO 文字列を作る（シードの投稿日時を「〇分前」で表現するため） */
  function minutesAgo(min) {
    return new Date(Date.now() - min * 60 * 1000).toISOString();
  }

  /**
   * 単色＋ラベルの SVG を data URL にして返す。
   * 本番では post_images.s3_key から S3 の画像 URL を組み立てるが、
   * モックではオフラインで動くようインライン SVG を使う。
   */
  function svgImage(color, label) {
    var svg =
      '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400">' +
      '<rect width="600" height="400" fill="' + color + '"/>' +
      '<text x="300" y="210" font-size="34" font-family="sans-serif" fill="#ffffff" ' +
      'text-anchor="middle">' + label + '</text></svg>';
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  }

  /**
   * 初期データを毎回新しく生成して返す（リセット時に同じ状態へ戻せるように、
   * 使い回しではなく都度生成する）。
   */
  MT.createSeed = function createSeed() {
    return {
      // users: password は本番では password_hash（ハッシュ化必須）。モックのため平文。
      users: [
        { id: 1, username: 'taro',   displayName: '山田太郎', email: 'taro@example.com',   password: 'password123', bio: '散歩と写真が好きです。週末はだいたい歩いています。', createdAt: minutesAgo(60 * 24 * 90) },
        { id: 2, username: 'hanako', displayName: '佐藤花子', email: 'hanako@example.com', password: 'password123', bio: 'カフェ巡りが趣味。おすすめのお店を探しています☕', createdAt: minutesAgo(60 * 24 * 80) },
        { id: 3, username: 'ichiro', displayName: '鈴木一郎', email: 'ichiro@example.com', password: 'password123', bio: '毎朝ランニングしてます。天気の話が多めです。', createdAt: minutesAgo(60 * 24 * 70) },
        { id: 4, username: 'taichi', displayName: '田中太一', email: 'taichi@example.com', password: 'password123', bio: 'エンジニアやってます。最近は Spring Boot と React。', createdAt: minutesAgo(60 * 24 * 60) },
        { id: 5, username: 'misaki', displayName: '中村美咲', email: 'misaki@example.com', password: 'password123', bio: '料理と読書。積読が減りません。', createdAt: minutesAgo(60 * 24 * 50) }
      ],

      posts: [
        { id: 1,  userId: 1, body: '今日はいい天気！散歩してきた。',                                          createdAt: minutesAgo(3) },
        { id: 2,  userId: 2, body: '新しいカフェ行ってきました☕\n豆の種類が多くて迷う…',                      createdAt: minutesAgo(62) },
        { id: 3,  userId: 3, body: '朝ラン 8km 完了。今日はペースが上がらなかった。',                          createdAt: minutesAgo(140) },
        { id: 4,  userId: 4, body: 'Spring Boot のバリデーション、Bean Validation でだいたい済むの助かる。',   createdAt: minutesAgo(200) },
        { id: 5,  userId: 1, body: '新しいカメラを買いました📷 週末に試し撮りしてきます。',                     createdAt: minutesAgo(320) },
        { id: 6,  userId: 5, body: '週末につくりおき。今週は煮物多め。',                                        createdAt: minutesAgo(400) },
        { id: 7,  userId: 2, body: '読みかけの本が3冊たまってる。1冊ずつ終わらせたい。',                        createdAt: minutesAgo(540) },
        { id: 8,  userId: 4, body: 'React の状態管理、結局サーバー状態と UI 状態を分けるのが一番わかりやすい。', createdAt: minutesAgo(700) },
        { id: 9,  userId: 3, body: '雨なので今日はお休み。ストレッチだけしておく。',                            createdAt: minutesAgo(900) },
        { id: 10, userId: 5, body: '図書館で借りた本、返却期限が明日だった…',                                   createdAt: minutesAgo(1200) },
        { id: 11, userId: 1, body: '公園の桜がきれいだったので写真を貼っておきます🌸',                          createdAt: minutesAgo(1500) },
        { id: 12, userId: 4, body: 'ローカル環境を Docker Compose に寄せたら立ち上げが一瞬になった。',          createdAt: minutesAgo(1800) }
      ],

      // post_images: 本番は s3_key を保持し S3 から配信する。モックは dataUrl を直接保持。
      postImages: [
        { id: 1, postId: 1,  dataUrl: svgImage('#7aa6c2', '散歩道'),   position: 0 },
        { id: 2, postId: 1,  dataUrl: svgImage('#8fbf8f', '川沿い'),   position: 1 },
        { id: 3, postId: 11, dataUrl: svgImage('#e0a3b8', '桜 1'),     position: 0 },
        { id: 4, postId: 11, dataUrl: svgImage('#d891a8', '桜 2'),     position: 1 },
        { id: 5, postId: 11, dataUrl: svgImage('#c9829a', '桜 3'),     position: 2 },
        { id: 6, postId: 11, dataUrl: svgImage('#b8748c', '桜 4'),     position: 3 }
      ],

      comments: [
        { id: 1, postId: 1, userId: 3, body: 'いい天気でしたね！',       createdAt: minutesAgo(2) },
        { id: 2, postId: 1, userId: 2, body: '散歩いいですね🚶',         createdAt: minutesAgo(1) },
        { id: 3, postId: 1, userId: 5, body: 'どのあたりを歩きました？', createdAt: minutesAgo(1) },
        { id: 4, postId: 2, userId: 1, body: 'どこのカフェですか？',     createdAt: minutesAgo(50) },
        { id: 5, postId: 4, userId: 1, body: 'これは便利ですね',         createdAt: minutesAgo(180) },
        { id: 6, postId: 4, userId: 3, body: 'アノテーションだけで済むの楽です', createdAt: minutesAgo(150) },
        { id: 7, postId: 5, userId: 2, body: '作例楽しみにしてます📷',   createdAt: minutesAgo(300) },
        { id: 8, postId: 6, userId: 2, body: 'つくりおき憧れます',       createdAt: minutesAgo(380) },
        { id: 9, postId: 11, userId: 5, body: 'きれい！どこの公園ですか？', createdAt: minutesAgo(1400) }
      ],

      // likes: (postId, userId) は複合 UNIQUE
      likes: [
        { id: 1,  postId: 1,  userId: 2, createdAt: minutesAgo(2) },
        { id: 2,  postId: 1,  userId: 3, createdAt: minutesAgo(2) },
        { id: 3,  postId: 1,  userId: 4, createdAt: minutesAgo(1) },
        { id: 4,  postId: 1,  userId: 5, createdAt: minutesAgo(1) },
        { id: 5,  postId: 2,  userId: 1, createdAt: minutesAgo(50) },
        { id: 6,  postId: 2,  userId: 5, createdAt: minutesAgo(40) },
        { id: 7,  postId: 3,  userId: 1, createdAt: minutesAgo(120) },
        { id: 8,  postId: 4,  userId: 1, createdAt: minutesAgo(180) },
        { id: 9,  postId: 4,  userId: 3, createdAt: minutesAgo(170) },
        { id: 10, postId: 4,  userId: 5, createdAt: minutesAgo(160) },
        { id: 11, postId: 5,  userId: 2, createdAt: minutesAgo(300) },
        { id: 12, postId: 5,  userId: 3, createdAt: minutesAgo(290) },
        { id: 13, postId: 6,  userId: 2, createdAt: minutesAgo(380) },
        { id: 14, postId: 8,  userId: 1, createdAt: minutesAgo(650) },
        { id: 15, postId: 11, userId: 2, createdAt: minutesAgo(1400) },
        { id: 16, postId: 11, userId: 3, createdAt: minutesAgo(1350) },
        { id: 17, postId: 11, userId: 5, createdAt: minutesAgo(1300) },
        { id: 18, postId: 12, userId: 1, createdAt: minutesAgo(1700) }
      ],

      // follows: (followerId, followeeId) は複合 UNIQUE / follower ≠ followee
      // taro（テストアカウント）は hanako・ichiro をフォロー済み。
      // → 「フォロー中」タブには taro / hanako / ichiro の投稿だけが並び、
      //    「すべて」タブとの件数差がわかるようにしている。
      follows: [
        { id: 1, followerId: 1, followeeId: 2, createdAt: minutesAgo(60 * 24 * 30) },
        { id: 2, followerId: 1, followeeId: 3, createdAt: minutesAgo(60 * 24 * 25) },
        { id: 3, followerId: 2, followeeId: 1, createdAt: minutesAgo(60 * 24 * 28) },
        { id: 4, followerId: 3, followeeId: 1, createdAt: minutesAgo(60 * 24 * 20) },
        { id: 5, followerId: 4, followeeId: 1, createdAt: minutesAgo(60 * 24 * 18) },
        { id: 6, followerId: 5, followeeId: 1, createdAt: minutesAgo(60 * 24 * 15) },
        { id: 7, followerId: 2, followeeId: 5, createdAt: minutesAgo(60 * 24 * 12) },
        { id: 8, followerId: 4, followeeId: 3, createdAt: minutesAgo(60 * 24 * 10) },
        { id: 9, followerId: 5, followeeId: 2, createdAt: minutesAgo(60 * 24 * 8) }
      ],

      // ログイン状態。初期は未ログイン（SCR-01 から始まる）
      session: null,

      // 自動採番カウンタ（本番は BIGINT の自動採番に相当）
      nextId: { user: 6, post: 13, postImage: 7, comment: 10, like: 19, follow: 10 }
    };
  };

})(window);
