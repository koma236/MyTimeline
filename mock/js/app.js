/* ==========================================================================
   app.js - ハッシュルーター + 各画面（SCR-01〜06）の描画とイベント配線
   ========================================================================== */

(function (global) {
  'use strict';

  var MT = global.MT;
  var doc = global.document;
  var store = MT.store;
  var ui = MT.ui;
  var LIMITS = store.LIMITS;

  var view = doc.getElementById('view');
  var header = doc.getElementById('header');

  /** 「すべて / フォロー中」タブの選択状態。詳細画面から戻っても保持する */
  var timelineTab = 'all';

  /* ============================================================== ルーター */

  var PUBLIC_ROUTES = ['login', 'signup'];

  /** location.hash を { name, param } に解釈する */
  function parseRoute() {
    var hash = global.location.hash.replace(/^#/, '');
    var parts = hash.split('/').filter(Boolean); // "/post/3" → ["post","3"]
    var name = parts[0] || '';
    var param = parts[1] ? decodeURIComponent(parts[1]) : null;

    switch (name) {
      case 'login':
      case 'signup':
      case 'timeline':
      case 'search':
        return { name: name, param: null };
      case 'post':
        return param ? { name: 'post', param: param } : { name: 'timeline', param: null };
      case 'user':
        return param ? { name: 'user', param: param } : { name: 'search', param: null };
      default:
        return { name: '', param: null };
    }
  }

  function navigate(hash) {
    if (global.location.hash === hash) render();
    else global.location.hash = hash;
  }

  function render() {
    var route = parseRoute();
    var me = store.me();

    // 未定義ハッシュはログイン状態に応じてフォールバック
    if (!route.name) {
      navigate(me ? '#/timeline' : '#/login');
      return;
    }
    // 認証ガード（F01: 未ログインで保護画面 → ログイン画面へ誘導）
    if (!me && PUBLIC_ROUTES.indexOf(route.name) === -1) {
      ui.toast('ログインが必要です', true);
      navigate('#/login');
      return;
    }
    // ログイン済みで認証画面に来たらタイムラインへ
    if (me && PUBLIC_ROUTES.indexOf(route.name) !== -1) {
      navigate('#/timeline');
      return;
    }

    renderHeader(me);
    view.innerHTML = '';
    global.scrollTo(0, 0);

    switch (route.name) {
      case 'login':    renderLogin(); break;
      case 'signup':   renderSignup(); break;
      case 'timeline': renderTimeline(); break;
      case 'post':     renderPostDetail(Number(route.param)); break;
      case 'user':     renderProfile(route.param); break;
      case 'search':   renderSearch(); break;
    }
  }

  function renderHeader(me) {
    header.hidden = !me;
    if (!me) return;
    var link = doc.getElementById('header-profile');
    link.href = '#/user/' + encodeURIComponent(me.username);
    link.textContent = me.displayName;
  }

  /** テンプレートを複製して #view に差し込み、そのフラグメントの親を返す */
  function mountTemplate(id) {
    var tpl = doc.getElementById(id);
    view.appendChild(tpl.content.cloneNode(true));
    return view;
  }

  function back() {
    if (global.history.length > 1) global.history.back();
    else navigate('#/timeline');
  }

  /* ======================================================= SCR-01 ログイン */

  function renderLogin() {
    mountTemplate('tpl-login');
    var form = view.querySelector('[data-form="login"]');

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      ui.clearFormErrors(form);
      var res = store.login(form.identifier.value, form.password.value);
      if (!res.ok) { ui.applyError(form, res.error); return; }
      ui.toast('ログインしました');
      navigate('#/timeline');
    });
  }

  /* ===================================================== SCR-02 新規登録 */

  function renderSignup() {
    mountTemplate('tpl-signup');
    var form = view.querySelector('[data-form="signup"]');

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      ui.clearFormErrors(form);
      var res = store.signup({
        username: form.username.value,
        displayName: form.displayName.value,
        email: form.email.value,
        password: form.password.value,
        bio: form.bio.value
      });
      if (!res.ok) { ui.applyError(form, res.error); return; }
      ui.toast('アカウントを作成しました');
      navigate('#/timeline'); // 登録成功でそのままログイン状態
    });
  }

  /* =================================================== SCR-03 タイムライン */

  function renderTimeline() {
    mountTemplate('tpl-timeline');
    var me = store.me();

    // --- タブ（F02）
    var tabs = view.querySelectorAll('.tabs__tab');
    tabs.forEach(function (tab) {
      tab.classList.toggle('is-active', tab.dataset.tab === timelineTab);
      tab.addEventListener('click', function () {
        if (timelineTab === tab.dataset.tab) return;
        timelineTab = tab.dataset.tab;
        render();
      });
    });

    // --- 投稿フォーム（F03）
    setupComposer(view.querySelector('[data-form="post"]'), me);

    // --- フィード
    var feed = view.querySelector('[data-feed]');
    var empty = timelineTab === 'following'
      ? ui.emptyState(
          'まだ投稿がありません',
          'ユーザーを検索してフォローすると、その人の投稿がここに並びます。',
          '<a class="btn btn--primary" href="#/search">ユーザーを検索する</a>')
      : ui.emptyState('まだ投稿がありません', '最初の投稿をしてみましょう。');

    mountFeed(feed, function (cursor) {
      return timelineTab === 'following'
        ? store.timelineFollowing({ cursor: cursor })
        : store.timelineAll({ cursor: cursor });
    }, empty);
  }

  /**
   * 投稿フォームの配線（文字数カウンタ・画像添付・投稿）。
   * F03 のバリデーション（280文字 / 最大4枚 / JPEG・PNG / サイズ上限）を再現する。
   */
  function setupComposer(form, me) {
    var textarea = form.querySelector('[name="body"]');
    var counter = form.querySelector('[data-counter]');
    var submit = form.querySelector('[data-submit]');
    var fileInput = form.querySelector('[data-image-input]');
    var previews = form.querySelector('[data-image-previews]');
    var imageCount = form.querySelector('[data-image-count]');
    var pendingImages = []; // data URL の配列（本番では S3 へアップロード後の s3_key）

    ui.fillAvatar(form.querySelector('[data-composer-avatar]'), me);

    function refresh() {
      var len = textarea.value.trim().length;
      var over = len > LIMITS.postBody;
      counter.textContent = len + ' / ' + LIMITS.postBody;
      counter.classList.toggle('is-over', over);
      imageCount.textContent = pendingImages.length + ' / ' + LIMITS.postImages;
      // 本文が空かつ画像も無い場合 / 文字数超過は投稿できない
      submit.disabled = over || (len === 0 && pendingImages.length === 0);
    }

    function renderPreviews() {
      previews.innerHTML = '';
      previews.hidden = pendingImages.length === 0;
      pendingImages.forEach(function (src, i) {
        var item = ui.html(
          '<div class="composer__preview">'
          + '<img src="' + src + '" alt="添付画像プレビュー' + (i + 1) + '">'
          + '<button class="composer__preview-remove" type="button" title="この画像を削除">×</button>'
          + '</div>'
        );
        item.querySelector('button').addEventListener('click', function () {
          pendingImages.splice(i, 1);
          renderPreviews();
          refresh();
        });
        previews.appendChild(item);
      });
    }

    textarea.addEventListener('input', refresh);

    fileInput.addEventListener('change', function () {
      var files = Array.prototype.slice.call(fileInput.files);
      fileInput.value = ''; // 同じファイルを再選択できるようにリセット
      ui.showFieldError(form, 'images', '');

      if (pendingImages.length + files.length > LIMITS.postImages) {
        ui.showFieldError(form, 'images', '画像は最大' + LIMITS.postImages + '枚までです');
        files = files.slice(0, LIMITS.postImages - pendingImages.length);
      }

      files.forEach(function (file) {
        if (LIMITS.imageTypes.indexOf(file.type) === -1) {
          ui.showFieldError(form, 'images', 'JPEG または PNG の画像を選択してください（' + file.name + '）');
          return;
        }
        if (file.size > LIMITS.imageBytes) {
          ui.showFieldError(form, 'images', '1枚あたり ' + (LIMITS.imageBytes / 1024 / 1024) + 'MB 以内の画像を選択してください（' + file.name + '）');
          return;
        }
        var reader = new FileReader();
        reader.onload = function () {
          pendingImages.push(reader.result);
          renderPreviews();
          refresh();
        };
        reader.readAsDataURL(file); // 本番は S3 へ直接アップロードし s3_key を受け取る
      });
    });

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      ui.clearFormErrors(form);
      var res = store.createPost({ body: textarea.value, images: pendingImages });
      if (!res.ok) { ui.applyError(form, res.error); return; }
      textarea.value = '';
      pendingImages = [];
      renderPreviews();
      refresh();
      ui.toast('投稿しました');
      render(); // タイムライン先頭に反映
    });

    refresh();
  }

  /**
   * カーソルページングのフィードを描画する（F02）。
   * fetcher(cursor) は { ok, data: { items, nextCursor } } を返す関数。
   */
  function mountFeed(container, fetcher, emptyNode) {
    var loadMoreBtn = null;

    function loadPage(cursor) {
      var res = fetcher(cursor);
      if (!res.ok) {
        container.appendChild(ui.emptyState('読み込みに失敗しました', res.error.message,
          '<button class="btn btn--outline" type="button" data-action="reload">再読み込み</button>'));
        return;
      }
      if (loadMoreBtn) { loadMoreBtn.remove(); loadMoreBtn = null; }

      res.data.items.forEach(function (post) {
        container.appendChild(ui.postCard(post));
      });

      if (cursor == null && res.data.items.length === 0) {
        container.appendChild(emptyNode);
        return;
      }
      if (res.data.nextCursor != null) {
        loadMoreBtn = ui.html('<button class="loadmore" type="button">もっと見る</button>');
        loadMoreBtn.addEventListener('click', function () { loadPage(res.data.nextCursor); });
        container.appendChild(loadMoreBtn);
      }
    }

    loadPage(null);
  }

  /* ==================================================== SCR-04 投稿詳細 */

  function renderPostDetail(postId) {
    var res = store.getPost(postId);
    if (!res.ok) {
      view.appendChild(ui.emptyState('投稿が見つかりません', res.error.message,
        '<a class="btn btn--primary" href="#/timeline">タイムラインへ戻る</a>'));
      return;
    }

    mountTemplate('tpl-post-detail');
    var post = res.data;
    var me = store.me();

    view.querySelector('[data-back]').addEventListener('click', back);
    view.querySelector('[data-post-body]').appendChild(ui.postCard(post, { detail: true }));

    var commentsBox = view.querySelector('[data-comments]');
    var countLabel = view.querySelector('[data-comment-count]');

    function renderComments() {
      var list = store.listComments(postId);
      commentsBox.innerHTML = '';
      countLabel.textContent = list.data.length;
      if (!list.data.length) {
        commentsBox.appendChild(ui.emptyState('まだコメントはありません', '最初のコメントを書いてみましょう。'));
        return;
      }
      list.data.forEach(function (c) { commentsBox.appendChild(ui.commentItem(c)); });
    }

    // --- コメント投稿フォーム（F04）
    var form = view.querySelector('[data-form="comment"]');
    var textarea = form.querySelector('[name="body"]');
    var counter = form.querySelector('[data-counter]');
    var submit = form.querySelector('[data-submit]');
    ui.fillAvatar(form.querySelector('[data-composer-avatar]'), me);

    function refresh() {
      var len = textarea.value.trim().length;
      var over = len > LIMITS.commentBody;
      counter.textContent = len + ' / ' + LIMITS.commentBody;
      counter.classList.toggle('is-over', over);
      submit.disabled = over || len === 0;
    }

    textarea.addEventListener('input', refresh);
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      ui.clearFormErrors(form);
      var created = store.createComment(postId, textarea.value);
      if (!created.ok) { ui.applyError(form, created.error); return; }
      textarea.value = '';
      refresh();
      renderComments();
      syncPostCounts(postId); // コメント数 +1 を投稿カードに反映
      ui.toast('コメントしました');
    });

    refresh();
    renderComments();
  }

  /* ==================================================== SCR-05 プロフィール */

  function renderProfile(username) {
    var res = store.getProfile(username);
    if (!res.ok) {
      view.appendChild(ui.emptyState('ユーザーが見つかりません', res.error.message,
        '<a class="btn btn--primary" href="#/search">ユーザーを検索する</a>'));
      return;
    }

    mountTemplate('tpl-profile');
    var user = res.data;

    view.querySelector('[data-back]').addEventListener('click', back);
    view.querySelector('[data-head-name]').textContent = user.displayName;
    view.querySelector('[data-display-name]').textContent = user.displayName;
    view.querySelector('[data-username]').textContent = '@' + user.username;
    view.querySelector('[data-bio]').textContent = user.bio || '自己紹介はまだありません。';
    view.querySelector('[data-following-count]').textContent = user.followCount;
    view.querySelector('[data-follower-count]').textContent = user.followerCount;
    ui.fillAvatar(view.querySelector('[data-avatar]'), user);

    // フォロー中/フォロワーの一覧画面は今回のスコープ外のため、検索画面へ誘導する
    view.querySelectorAll('[data-following-link], [data-follower-link]').forEach(function (a) {
      a.setAttribute('href', '#/search');
    });

    // フォローボタン（自分のプロフィールでは非表示）
    var slot = view.querySelector('[data-follow-slot]');
    var btn = ui.followButton(user);
    if (btn) slot.appendChild(btn);

    mountFeed(view.querySelector('[data-feed]'), function (cursor) {
      return store.listUserPosts(username, { cursor: cursor });
    }, ui.emptyState('まだ投稿がありません', user.isMe ? '最初の投稿をしてみましょう。' : ''));
  }

  /* ================================================== SCR-06 ユーザー検索 */

  function renderSearch() {
    mountTemplate('tpl-search');
    var input = view.querySelector('[data-search-input]');
    var results = view.querySelector('[data-results]');

    view.querySelector('[data-back]').addEventListener('click', back);

    function search() {
      var res = store.searchUsers(input.value);
      results.innerHTML = '';
      if (!res.ok) {
        results.appendChild(ui.emptyState('検索に失敗しました', res.error.message));
        return;
      }
      if (!res.data.length) {
        results.appendChild(ui.emptyState('該当するユーザーがいません', 'ユーザー名または表示名の一部で検索してください。'));
        return;
      }
      res.data.forEach(function (u) { results.appendChild(ui.userCard(u)); });
    }

    input.addEventListener('input', search);
    search();
    input.focus();
  }

  /* ============================================== 画面共通のイベント委譲 */

  /** 指定投稿のカードすべて（一覧・詳細）のいいね数・コメント数を最新化する */
  function syncPostCounts(postId) {
    var res = store.getPost(postId);
    if (!res.ok) return;
    var post = res.data;
    doc.querySelectorAll('[data-post-id="' + postId + '"]').forEach(function (card) {
      var likeBtn = card.querySelector('[data-action="like"]');
      likeBtn.classList.toggle('is-active', post.likedByMe);
      likeBtn.querySelector('.action__icon').textContent = post.likedByMe ? '♥' : '♡';
      likeBtn.title = post.likedByMe ? 'いいねを取り消す' : 'いいね';
      card.querySelector('[data-like-count]').textContent = post.likeCount;
      card.querySelector('[data-comment-count]').textContent = post.commentCount;
    });
  }

  function closeMenus() {
    doc.querySelectorAll('.post__menu').forEach(function (m) { m.remove(); });
  }

  view.addEventListener('click', function (e) {
    var actionEl = e.target.closest('[data-action]');
    var card = e.target.closest('[data-post-id]');
    var commentEl = e.target.closest('[data-comment-id]');
    var action = actionEl && actionEl.dataset.action;

    if (action !== 'post-menu' && action !== 'comment-menu') closeMenus();

    if (action) {
      switch (action) {
        // --- F05 いいねトグル
        case 'like': {
          e.preventDefault();
          var liked = store.toggleLike(Number(card.dataset.postId));
          if (!liked.ok) { ui.toast(liked.error.message, true); return; }
          syncPostCounts(Number(card.dataset.postId));
          return;
        }
        // --- コメント数クリックで投稿詳細へ（F02 → SCR-04）
        case 'comment':
          e.preventDefault();
          navigate('#/post/' + card.dataset.postId);
          return;

        // --- F03 投稿の [⋯] メニュー
        case 'post-menu': {
          e.preventDefault();
          var wasOpen = actionEl.parentNode.querySelector('.post__menu');
          closeMenus();
          if (wasOpen) return;
          actionEl.parentNode.appendChild(ui.deleteMenu('投稿を削除'));
          return;
        }
        // --- F04 コメントの [⋯] メニュー
        case 'comment-menu': {
          e.preventDefault();
          var openMenu = actionEl.parentNode.querySelector('.post__menu');
          closeMenus();
          if (openMenu) return;
          actionEl.parentNode.appendChild(ui.deleteMenu('コメントを削除'));
          return;
        }
        // --- 削除実行（投稿 / コメント）
        case 'delete': {
          e.preventDefault();
          closeMenus();
          if (commentEl) {
            if (!global.confirm('このコメントを削除しますか？')) return;
            var dc = store.deleteComment(Number(commentEl.dataset.commentId));
            if (!dc.ok) { ui.toast(dc.error.message, true); return; }
            ui.toast('コメントを削除しました');
            render();
            return;
          }
          if (!global.confirm('この投稿を削除しますか？\nコメント・いいね・画像もあわせて削除されます。')) return;
          var dp = store.deletePost(Number(card.dataset.postId));
          if (!dp.ok) { ui.toast(dp.error.message, true); return; }
          ui.toast('投稿を削除しました');
          // 詳細画面から削除した場合はタイムラインへ戻る
          if (parseRoute().name === 'post') navigate('#/timeline');
          else render();
          return;
        }
        // --- F06 フォロートグル
        case 'follow': {
          e.preventDefault();
          var fr = store.toggleFollow(Number(actionEl.dataset.userId));
          if (!fr.ok) { ui.toast(fr.error.message, true); return; }
          ui.toast(fr.data.followingByMe ? 'フォローしました' : 'フォローを解除しました');
          render();
          return;
        }
        case 'reload':
          e.preventDefault();
          render();
          return;
      }
    }

    // --- 投稿カード本体のクリックで投稿詳細へ（リンク・ボタン・画像は除く）
    if (card && !card.classList.contains('post--detail')
      && !e.target.closest('a, button, .post__image')) {
      navigate('#/post/' + card.dataset.postId);
    }
  });

  // メニューの外側クリックで閉じる
  doc.addEventListener('click', function (e) {
    if (!e.target.closest('.post__menu-wrap')) closeMenus();
  });

  /* ============================================ モック専用の補助 UI（本番なし） */

  var userSwitch = doc.getElementById('mock-user-switch');

  /** ユーザー切替セレクトの選択肢を作り直す（「← ログイン中」表示の更新を兼ねる） */
  function refreshUserOptions() {
    var me = store.me();
    userSwitch.innerHTML = '<option value="">ユーザー切替…</option>';
    store.listUsers().forEach(function (u) {
      var opt = doc.createElement('option');
      opt.value = u.id;
      opt.textContent = u.displayName + ' (@' + u.username + ')' + (me && me.id === u.id ? ' ← ログイン中' : '');
      userSwitch.appendChild(opt);
    });
  }

  function setupMockBar() {
    userSwitch.addEventListener('change', function () {
      if (!userSwitch.value) return;
      var res = store.switchUser(Number(userSwitch.value));
      if (!res.ok) { ui.toast(res.error.message, true); return; }
      ui.toast(res.data.displayName + ' に切り替えました');
      timelineTab = 'all';
      refreshUserOptions();
      navigate('#/timeline');
    });

    doc.getElementById('mock-reset').addEventListener('click', function () {
      if (!global.confirm('モックのデータを初期状態に戻しますか？')) return;
      store.reset();
      refreshUserOptions();
      ui.toast('データをリセットしました');
      navigate('#/login');
    });
  }

  function setupHeader() {
    doc.getElementById('header-logout').addEventListener('click', function () {
      store.logout();
      refreshUserOptions();
      ui.toast('ログアウトしました');
      navigate('#/login');
    });
  }

  /* ==================================================================== 起動 */

  store.init();
  setupMockBar();
  setupHeader();
  refreshUserOptions();
  global.addEventListener('hashchange', refreshUserOptions);
  global.addEventListener('hashchange', render);
  render();

})(window);
