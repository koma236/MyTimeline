/* ==========================================================================
   ui.js - 共通描画部品
   投稿カード・コメント・ユーザーカード・アバター・相対時刻・トーストなど、
   複数画面で使い回す UI をここにまとめる。
   ========================================================================== */

(function (global) {
  'use strict';

  var MT = global.MT || (global.MT = {});
  var doc = global.document;

  /* ---------------------------------------------------------------- 汎用 */

  function escapeHtml(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  /** HTML 文字列から最初の要素を作る */
  function html(str) {
    var tpl = doc.createElement('template');
    tpl.innerHTML = str.trim();
    return tpl.content.firstElementChild;
  }

  /** 相対時刻（3分前 / 2時間前 / 3日前 / 2026年7月1日） */
  function relativeTime(iso) {
    var diff = Date.now() - new Date(iso).getTime();
    var min = Math.floor(diff / 60000);
    if (min < 1) return 'たった今';
    if (min < 60) return min + '分前';
    var hour = Math.floor(min / 60);
    if (hour < 24) return hour + '時間前';
    var day = Math.floor(hour / 24);
    if (day < 7) return day + '日前';
    var d = new Date(iso);
    return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日';
  }

  /** username から安定した色を作る（アバター用。画像は持たない） */
  var AVATAR_COLORS = ['#1d9bf0', '#00ba7c', '#f91880', '#7856ff', '#ff7a00', '#e0245e', '#17bf63'];
  function avatarColor(username) {
    var sum = 0;
    for (var i = 0; i < username.length; i++) sum += username.charCodeAt(i);
    return AVATAR_COLORS[sum % AVATAR_COLORS.length];
  }

  /** 既存の .avatar 要素にユーザーの色と頭文字を反映する */
  function fillAvatar(el, user) {
    if (!el || !user) return;
    el.style.background = avatarColor(user.username);
    el.textContent = (user.displayName || user.username).charAt(0);
    el.title = user.displayName + ' @' + user.username;
  }

  function avatarHtml(user, extraClass) {
    return '<div class="avatar ' + (extraClass || '') + '" style="background:' + avatarColor(user.username) + '">'
      + escapeHtml((user.displayName || user.username).charAt(0)) + '</div>';
  }

  /* ------------------------------------------------------------ 部品：投稿 */

  function imagesHtml(images) {
    if (!images.length) return '';
    var items = images.map(function (src, i) {
      return '<img class="post__image" src="' + src + '" alt="添付画像' + (i + 1) + '">';
    }).join('');
    return '<div class="post__images post__images--' + images.length + '">' + items + '</div>';
  }

  /**
   * 投稿カード（F02 / F03 / F05）。
   * detail=true で投稿詳細用（クリックで遷移しない・本文大きめ）。
   */
  function postCard(post, options) {
    options = options || {};
    var detail = !!options.detail;

    // [⋯] メニューは自分の投稿にのみ表示する（F03 認可）
    var menu = post.isMine
      ? '<div class="post__menu-wrap">'
      + '<button class="post__menu-btn" type="button" data-action="post-menu" title="メニュー">⋯</button>'
      + '</div>'
      : '';

    var el = html(
      '<article class="post' + (detail ? ' post--detail' : '') + '" data-post-id="' + post.id + '">'
      + avatarHtml(post.author)
      + '<div class="post__main">'
      + '  <div class="post__head">'
      + '    <a class="post__author" href="#/user/' + encodeURIComponent(post.author.username) + '">' + escapeHtml(post.author.displayName) + '</a>'
      + '    <span class="post__username">@' + escapeHtml(post.author.username) + '</span>'
      + '    <span class="post__time">・' + escapeHtml(relativeTime(post.createdAt)) + '</span>'
      + menu
      + '  </div>'
      + (post.body ? '<p class="post__body">' + escapeHtml(post.body) + '</p>' : '')
      + imagesHtml(post.images)
      + '  <div class="post__actions">'
      + '    <button class="action action--like' + (post.likedByMe ? ' is-active' : '') + '" type="button" data-action="like" '
      + '      title="' + (post.likedByMe ? 'いいねを取り消す' : 'いいね') + '">'
      + '      <span class="action__icon">' + (post.likedByMe ? '♥' : '♡') + '</span>'
      + '      <span data-like-count>' + post.likeCount + '</span>'
      + '    </button>'
      + '    <button class="action action--comment" type="button" data-action="comment" title="コメントを見る">'
      + '      <span class="action__icon">💬</span>'
      + '      <span data-comment-count>' + post.commentCount + '</span>'
      + '    </button>'
      + '  </div>'
      + '</div>'
      + '</article>'
    );
    return el;
  }

  /** コメント 1 件（F04） */
  function commentItem(comment) {
    var menu = comment.isMine
      ? '<div class="post__menu-wrap">'
      + '<button class="post__menu-btn" type="button" data-action="comment-menu" title="メニュー">⋯</button>'
      + '</div>'
      : '';

    return html(
      '<article class="comment" data-comment-id="' + comment.id + '">'
      + avatarHtml(comment.author)
      + '<div class="comment__main">'
      + '  <div class="post__head">'
      + '    <a class="post__author" href="#/user/' + encodeURIComponent(comment.author.username) + '">' + escapeHtml(comment.author.displayName) + '</a>'
      + '    <span class="post__username">@' + escapeHtml(comment.author.username) + '</span>'
      + '    <span class="post__time">・' + escapeHtml(relativeTime(comment.createdAt)) + '</span>'
      + menu
      + '  </div>'
      + '  <p class="comment__body">' + escapeHtml(comment.body) + '</p>'
      + '</div>'
      + '</article>'
    );
  }

  /**
   * フォローボタン（F06）。
   * 自分自身には表示しない。フォロー中はホバーで「フォロー解除」に変わる。
   */
  function followButton(user) {
    if (user.isMe) return null;
    if (user.followingByMe) {
      return html(
        '<button class="btn btn--following" type="button" data-action="follow" data-user-id="' + user.id + '">'
        + '<span class="btn__label">フォロー中</span><span class="btn__hover">フォロー解除</span>'
        + '</button>'
      );
    }
    return html(
      '<button class="btn btn--follow" type="button" data-action="follow" data-user-id="' + user.id + '">フォロー</button>'
    );
  }

  /** ユーザーカード（SCR-06 検索結果） */
  function userCard(user) {
    var el = html(
      '<article class="usercard" data-username="' + escapeHtml(user.username) + '">'
      + avatarHtml(user)
      + '<div class="usercard__main">'
      + '  <div>'
      + '    <a class="usercard__name" href="#/user/' + encodeURIComponent(user.username) + '">' + escapeHtml(user.displayName) + '</a>'
      + '    <span class="usercard__username">@' + escapeHtml(user.username) + '</span>'
      + (user.isMe ? '<span class="usercard__username">（自分）</span>' : '')
      + '  </div>'
      + (user.bio ? '<p class="usercard__bio">' + escapeHtml(user.bio) + '</p>' : '')
      + '</div>'
      + '</article>'
    );
    var btn = followButton(user);
    if (btn) el.appendChild(btn);
    return el;
  }

  /** 空状態 */
  function emptyState(title, text, actionHtml) {
    return html(
      '<div class="empty">'
      + '<p class="empty__title">' + escapeHtml(title) + '</p>'
      + (text ? '<p class="empty__text">' + escapeHtml(text) + '</p>' : '')
      + (actionHtml || '')
      + '</div>'
    );
  }

  /** 削除メニュー（投稿・コメント共通） */
  function deleteMenu(label) {
    return html(
      '<div class="post__menu">'
      + '<button class="post__menu-item" type="button" data-action="delete">' + escapeHtml(label) + '</button>'
      + '</div>'
    );
  }

  /* ------------------------------------------------------------- トースト */

  var toastTimer = null;
  function toast(message, isError) {
    var el = doc.getElementById('toast');
    el.textContent = message;
    el.className = 'toast' + (isError ? ' toast--error' : '');
    el.hidden = false;
    global.clearTimeout(toastTimer);
    toastTimer = global.setTimeout(function () { el.hidden = true; }, 2600);
  }

  /* ---------------------------------------------------------- フォーム補助 */

  /** data-error="<field>" の要素にメッセージを出す。null でクリア */
  function showFieldError(form, field, message) {
    var el = form.querySelector('[data-error="' + field + '"]');
    if (el) el.textContent = message || '';
    var input = form.querySelector('[name="' + field + '"]');
    if (input) input.classList.toggle('is-invalid', !!message);
  }

  function clearFormErrors(form) {
    form.querySelectorAll('[data-error]').forEach(function (el) { el.textContent = ''; });
    form.querySelectorAll('.is-invalid').forEach(function (el) { el.classList.remove('is-invalid'); });
  }

  /** 疑似 API のエラーをフォームに反映する */
  function applyError(form, error) {
    clearFormErrors(form);
    showFieldError(form, error.field || '_form', error.message);
  }

  MT.ui = {
    escapeHtml: escapeHtml,
    html: html,
    relativeTime: relativeTime,
    fillAvatar: fillAvatar,
    avatarHtml: avatarHtml,
    postCard: postCard,
    commentItem: commentItem,
    followButton: followButton,
    userCard: userCard,
    emptyState: emptyState,
    deleteMenu: deleteMenu,
    toast: toast,
    showFieldError: showFieldError,
    clearFormErrors: clearFormErrors,
    applyError: applyError
  };

})(window);
