/* HindiAnimestuff — watch page player
 * Custom controls around a plain <video>: play/pause, stop, ±10s, previous/next
 * episode, mute + volume, quality, fullscreen, resume position, auto-next.
 * Stream URLs are NOT in the page HTML — they are resolved at play time from
 * window.DOWNLOAD_MAP (the same base64 data the download flow already uses).
 * Config comes from window.PLAYER_CFG = { sid, title, season, eps:[{key,label,q:[..]}] }.
 */
(function () {
  "use strict";
  var cfg = window.PLAYER_CFG;
  if (!cfg || !cfg.eps || !cfg.eps.length) return;

  var $ = function (id) { return document.getElementById(id); };
  var player = $("wpPlayer"), video = $("wpVideo"), surface = $("wpSurface");
  var startEl = $("wpStart"), startLabel = $("wpStartLabel"), bigPlay = $("wpBigPlay");
  var spinner = $("wpSpinner"), toastEl = $("wpToast");
  var errEl = $("wpError"), errText = $("wpErrorText"), errDl = $("wpErrorDownload"), errRetry = $("wpErrorRetry");
  var seek = $("wpSeek"), bufEl = $("wpBuf"), playedEl = $("wpPlayed"), thumbEl = $("wpThumb");
  var tNow = $("wpTimeNow"), tTotal = $("wpTimeTotal");
  var btnPlay = $("wpPlay"), btnStop = $("wpStop"), btnBack = $("wpBack"), btnFwd = $("wpFwd");
  var btnPrev = $("wpPrev"), btnNext = $("wpNext"), btnMute = $("wpMute"), volEl = $("wpVol");
  var btnQ = $("wpQBtn"), menuQ = $("wpQMenu"), btnFs = $("wpFs");
  var nowTop = $("wpNow"), nowEp = $("wpNowEp"), dlBtn = $("wpDownload");
  var epBtns = Array.prototype.slice.call(document.querySelectorAll(".wp-ep"));

  var root = document.body.getAttribute("data-root") || "";
  var map = (window.DOWNLOAD_MAP && window.DOWNLOAD_MAP[cfg.sid]) || {};
  var eps = cfg.eps;
  var SEEK_STEP = 10;
  var Q_PREF = ["720p", "480p", "1080p"]; // x264 720p/480p play in far more browsers than x265 1080p

  var epIndex = 0, quality = "", srcAttached = false, wantPlay = false;
  var pendingTime = 0, failed = {}, idleTimer = null, lastSave = 0, toastTimer = null;
  var dragging = false, lastTouchAt = 0, lastTap = { t: 0, x: 0 }, kbActive = false;

  /* ---------- storage helpers (never throw) ---------- */
  function lsGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function lsSet(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
  function lsDel(k) { try { localStorage.removeItem(k); } catch (e) {} }
  function tKey(i) { return "wp:t:" + cfg.sid + ":" + eps[i].key; }

  /* ---------- utils ---------- */
  function fmt(t) {
    if (!isFinite(t) || t < 0) t = 0;
    var h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), s = Math.floor(t % 60);
    return (h ? h + ":" + (m < 10 ? "0" : "") : "") + m + ":" + (s < 10 ? "0" : "") + s;
  }
  // toggleAttribute works on HTML *and* SVG elements (the .hidden property does not exist on SVG)
  function show(el, on) { if (el) el.toggleAttribute("hidden", !on); }
  function toast(msg, ms) {
    toastEl.textContent = msg; show(toastEl, true);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { show(toastEl, false); }, ms || 1600);
  }
  function entryUrl(i, q) {
    var e = map[eps[i].key];
    return (e && e[q] && e[q].url) || "";
  }
  function pickQuality(i, preferred) {
    var avail = eps[i].q.filter(function (q) { return entryUrl(i, q) && !failed[i + ":" + q]; });
    if (!avail.length) return "";
    if (preferred && avail.indexOf(preferred) > -1) return preferred;
    for (var n = 0; n < Q_PREF.length; n++) if (avail.indexOf(Q_PREF[n]) > -1) return Q_PREF[n];
    return avail[0];
  }
  function epLabel(i) { return eps[i].label; }
  function downloadHref(i, q) {
    return root + "download/file.html?sid=" + encodeURIComponent(cfg.sid) +
      "&ep=" + encodeURIComponent(eps[i].key) + "&q=" + encodeURIComponent(q || eps[i].q[0]);
  }

  /* ---------- UI sync ---------- */
  function syncPlayIcon() {
    var playing = !video.paused && !video.ended;
    show(btnPlay.querySelector(".i-play"), !playing);
    show(btnPlay.querySelector(".i-pause"), playing);
    btnPlay.setAttribute("aria-label", playing ? "Pause" : "Play");
  }
  function syncVolIcon() {
    var m = video.muted || video.volume === 0;
    show(btnMute.querySelector(".i-vol"), !m);
    show(btnMute.querySelector(".i-muted"), m);
    volEl.value = video.muted ? 0 : video.volume;
  }
  function syncTime() {
    var d = video.duration, t = video.currentTime;
    var pct = isFinite(d) && d > 0 ? (t / d) * 100 : 0;
    playedEl.style.width = pct + "%";
    thumbEl.style.left = pct + "%";
    tNow.textContent = fmt(t);
    tTotal.textContent = isFinite(d) ? fmt(d) : "0:00";
    seek.setAttribute("aria-valuenow", Math.round(pct));
  }
  function syncBuffer() {
    var d = video.duration, b = video.buffered;
    if (!isFinite(d) || d <= 0 || !b.length) { bufEl.style.width = "0%"; return; }
    var end = 0;
    for (var i = 0; i < b.length; i++) {
      if (b.start(i) <= video.currentTime + 0.5) end = Math.max(end, b.end(i));
    }
    bufEl.style.width = Math.min(100, (end / d) * 100) + "%";
  }
  function syncEpisodeUi() {
    var label = epLabel(epIndex);
    nowTop.textContent = cfg.title + (cfg.season ? " · " + cfg.season : "") + (eps.length > 1 ? " — " + label : "");
    nowEp.textContent = eps.length > 1 ? label : "";
    epBtns.forEach(function (b) {
      var i = parseInt(b.getAttribute("data-index"), 10);
      b.classList.toggle("is-active", i === epIndex);
      if (lsGet(tKey(i)) === "done") b.classList.add("is-watched");
    });
    if (btnNext) btnNext.disabled = epIndex >= eps.length - 1;
    if (btnPrev) btnPrev.disabled = epIndex <= 0;
    var a = downloadHref(epIndex, quality);
    dlBtn.href = a; errDl.href = a;
    btnQ.textContent = quality || "—";
    menuQ.innerHTML = "";
    eps[epIndex].q.forEach(function (q) {
      var b = document.createElement("button");
      b.type = "button"; b.textContent = q; b.setAttribute("data-q", q);
      if (q === quality) b.className = "is-active";
      menuQ.appendChild(b);
    });
    try {
      history.replaceState(null, "", "?ep=" + encodeURIComponent(eps[epIndex].key));
    } catch (e) {}
    if ("mediaSession" in navigator && window.MediaMetadata) {
      try {
        navigator.mediaSession.metadata = new MediaMetadata({
          title: eps.length > 1 ? label : cfg.title,
          artist: cfg.title + (cfg.season ? " " + cfg.season : ""),
          album: "HindiAnimestuff"
        });
      } catch (e) {}
    }
  }
  function showStart(label) {
    startLabel.textContent = label || "";
    show(startEl, true);
    player.classList.add("is-start");
  }
  function hideStart() { show(startEl, false); player.classList.remove("is-start"); }

  /* ---------- source handling ---------- */
  function attach() {
    var url = entryUrl(epIndex, quality);
    if (!url) { showError("No playable link for this quality."); return false; }
    srcAttached = true;
    show(errEl, false);
    video.preload = "auto";
    video.src = url;
    video.load();
    show(spinner, true);
    return true;
  }

  function loadEpisode(i, opts) {
    opts = opts || {};
    if (i < 0 || i >= eps.length) return;
    // leaving an episode: remember where we were
    if (srcAttached && video.currentTime > 5 && !video.ended) lsSet(tKey(epIndex), String(Math.floor(video.currentTime)));
    epIndex = i;
    failed = {};
    var saved = parseInt(lsGet(tKey(i)), 10);
    pendingTime = (isFinite(saved) && saved > 10) ? saved : 0;
    quality = pickQuality(i, opts.quality || lsGet("wp:q"));
    srcAttached = false;
    video.removeAttribute("src");
    video.load();
    show(errEl, false);
    show(spinner, false);
    syncEpisodeLabels();
    syncEpisodeUi();
    lsSet("wp:last:" + cfg.sid, eps[i].key);
    if (opts.autoplay) {
      wantPlay = true;
      hideStart();
      if (attach()) tryPlay();
    } else {
      showStart(pendingTime ? "Resume from " + fmt(pendingTime) : (eps.length > 1 ? epLabel(i) : ""));
    }
    if (opts.scroll) player.scrollIntoView({ behavior: "smooth", block: "start" });
  }
  function syncEpisodeLabels() { syncTime(); bufEl.style.width = "0%"; tTotal.textContent = "0:00"; tNow.textContent = "0:00"; }

  function setQuality(q) {
    if (q === quality) return;
    var t = srcAttached ? video.currentTime : pendingTime;
    var was = !video.paused || wantPlay;
    quality = q;
    lsSet("wp:q", q);
    pendingTime = t;
    syncEpisodeUi();
    if (srcAttached || was) {
      wantPlay = was;
      if (attach() && was) tryPlay();
    }
    toast("Quality: " + q);
  }

  function showError(msg) {
    show(spinner, false);
    if (msg) errText.textContent = msg;
    show(errEl, true);
    wantPlay = false;
  }

  /* ---------- transport ---------- */
  function tryPlay() {
    var p = video.play();
    if (p && p.catch) p.catch(function () { /* autoplay blocked / interrupted */ syncPlayIcon(); });
  }
  function play() {
    wantPlay = true; kbActive = true;
    hideStart();
    if (!srcAttached && !attach()) return;
    tryPlay();
  }
  function pause() { wantPlay = false; video.pause(); }
  function togglePlay() { (video.paused || video.ended) ? play() : pause(); }
  function stop() {
    wantPlay = false;
    video.pause();
    try { video.currentTime = 0; } catch (e) {}
    lsDel(tKey(epIndex));
    pendingTime = 0;
    // fully release the connection too, so "stop" really stops downloading
    srcAttached = false;
    video.removeAttribute("src");
    video.load();
    show(spinner, false);
    syncTime(); syncPlayIcon();
    showStart("Stopped" + (eps.length > 1 ? " · " + epLabel(epIndex) : ""));
  }
  function seekBy(delta) {
    if (!srcAttached) { play(); return; }
    var d = video.duration;
    var t = video.currentTime + delta;
    if (isFinite(d)) t = Math.min(t, Math.max(0, d - 0.25));
    video.currentTime = Math.max(0, t);
    toast((delta > 0 ? "+" : "−") + Math.abs(delta) + "s", 700);
    syncTime();
  }
  function next() { if (epIndex < eps.length - 1) { toast("Next: " + epLabel(epIndex + 1)); loadEpisode(epIndex + 1, { autoplay: true }); } }
  function prev() { if (epIndex > 0) loadEpisode(epIndex - 1, { autoplay: true }); }
  function toggleMute() {
    video.muted = !video.muted;
    if (!video.muted && video.volume === 0) video.volume = 0.6;
    lsSet("wp:muted", video.muted ? "1" : "0");
  }

  /* ---------- fullscreen ---------- */
  function fsEl() { return document.fullscreenElement || document.webkitFullscreenElement || null; }
  function toggleFs() {
    if (fsEl()) {
      (document.exitFullscreen || document.webkitExitFullscreen).call(document);
      return;
    }
    var req = player.requestFullscreen || player.webkitRequestFullscreen;
    if (req) {
      var r = req.call(player);
      if (r && r.then) r.then(lockLandscape).catch(function () {});
    } else if (video.webkitEnterFullscreen) {
      try { video.webkitEnterFullscreen(); } // iPhone Safari: native fullscreen only, needs a loaded video
      catch (e) { toast("Start the video first, then tap fullscreen"); }
    }
  }
  function lockLandscape() {
    try { if (screen.orientation && screen.orientation.lock) screen.orientation.lock("landscape").catch(function () {}); } catch (e) {}
  }
  function onFsChange() {
    var on = !!fsEl();
    player.classList.toggle("is-fs", on);
    show(btnFs.querySelector(".i-fs"), !on);
    show(btnFs.querySelector(".i-fsx"), on);
    if (!on) { try { if (screen.orientation && screen.orientation.unlock) screen.orientation.unlock(); } catch (e) {} }
  }
  document.addEventListener("fullscreenchange", onFsChange);
  document.addEventListener("webkitfullscreenchange", onFsChange);

  /* ---------- auto-hide controls ---------- */
  function wake() {
    player.classList.remove("is-idle");
    clearTimeout(idleTimer);
    if (!video.paused && !dragging && menuQ.hasAttribute("hidden")) {
      idleTimer = setTimeout(function () { player.classList.add("is-idle"); }, 3000);
    }
  }
  ["pointermove", "pointerdown", "keydown"].forEach(function (ev) { player.addEventListener(ev, wake); });

  /* ---------- seek bar (pointer events: mouse + touch + pen) ---------- */
  function seekFromEvent(e) {
    var r = seek.getBoundingClientRect();
    var ratio = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    var d = video.duration;
    if (!isFinite(d) || d <= 0) {
      if (!srcAttached) { pendingTime = 0; play(); }
      return;
    }
    video.currentTime = ratio * d;
    syncTime();
  }
  seek.addEventListener("pointerdown", function (e) {
    dragging = true; seek.classList.add("is-drag");
    try { seek.setPointerCapture(e.pointerId); } catch (er) {}
    seekFromEvent(e);
    e.preventDefault();
  });
  seek.addEventListener("pointermove", function (e) { if (dragging) seekFromEvent(e); });
  function endDrag() { dragging = false; seek.classList.remove("is-drag"); wake(); }
  seek.addEventListener("pointerup", endDrag);
  seek.addEventListener("pointercancel", endDrag);
  seek.addEventListener("keydown", function (e) {
    if (e.key === "ArrowLeft") { seekBy(-5); e.preventDefault(); e.stopPropagation(); }
    if (e.key === "ArrowRight") { seekBy(5); e.preventDefault(); e.stopPropagation(); }
  });

  /* ---------- tap surface ---------- */
  surface.addEventListener("pointerdown", function (e) { if (e.pointerType === "touch") lastTouchAt = Date.now(); });
  surface.addEventListener("click", function (e) {
    var isTouch = Date.now() - lastTouchAt < 800;
    show(menuQ, false);
    if (!isTouch) { togglePlay(); return; } // mouse: click = play/pause
    // touch: single tap shows/hides controls, double-tap left/right seeks, centre toggles play
    var now = Date.now();
    var r = surface.getBoundingClientRect();
    var x = (e.clientX - r.left) / r.width;
    if (now - lastTap.t < 320 && Math.abs(x - lastTap.x) < 0.2) {
      lastTap.t = 0;
      if (x < 0.35) seekBy(-SEEK_STEP);
      else if (x > 0.65) seekBy(SEEK_STEP);
      else togglePlay();
      wake();
      return;
    }
    lastTap = { t: now, x: x };
    if (player.classList.contains("is-idle")) wake();
    else if (!video.paused) { clearTimeout(idleTimer); player.classList.add("is-idle"); }
    else wake();
  });
  surface.addEventListener("dblclick", function (e) {
    if (Date.now() - lastTouchAt < 800) return; // touch double-tap handled above
    toggleFs();
  });

  /* ---------- button wiring ---------- */
  bigPlay.addEventListener("click", play);
  startEl.addEventListener("click", function (e) { if (e.target === startEl || e.target.classList.contains("wp-start-shade")) play(); });
  btnPlay.addEventListener("click", togglePlay);
  btnStop.addEventListener("click", stop);
  btnBack.addEventListener("click", function () { seekBy(-SEEK_STEP); });
  btnFwd.addEventListener("click", function () { seekBy(SEEK_STEP); });
  if (btnNext) btnNext.addEventListener("click", next);
  if (btnPrev) btnPrev.addEventListener("click", prev);
  btnMute.addEventListener("click", toggleMute);
  volEl.addEventListener("input", function () {
    var v = parseFloat(volEl.value);
    video.volume = v; video.muted = v === 0;
    lsSet("wp:vol", String(v)); lsSet("wp:muted", video.muted ? "1" : "0");
  });
  btnFs.addEventListener("click", toggleFs);
  btnQ.addEventListener("click", function (e) { e.stopPropagation(); show(menuQ, menuQ.hasAttribute("hidden")); wake(); });
  menuQ.addEventListener("click", function (e) {
    var b = e.target.closest("button[data-q]");
    if (!b) return;
    show(menuQ, false);
    setQuality(b.getAttribute("data-q"));
  });
  document.addEventListener("click", function (e) {
    if (!menuQ.hasAttribute("hidden") && !e.target.closest(".wp-qwrap")) show(menuQ, false);
  });
  errRetry.addEventListener("click", function () {
    failed = {};
    quality = pickQuality(epIndex, quality) || quality;
    syncEpisodeUi();
    wantPlay = true;
    if (attach()) tryPlay();
  });
  epBtns.forEach(function (b) {
    b.addEventListener("click", function () {
      loadEpisode(parseInt(b.getAttribute("data-index"), 10), { autoplay: true, scroll: true });
    });
  });

  /* ---------- keyboard ---------- */
  // shortcuts only apply after the user has used the player (so Space/arrows still scroll the page normally)
  document.addEventListener("pointerdown", function (e) { kbActive = player.contains(e.target); });
  document.addEventListener("keydown", function (e) {
    if (!kbActive && !fsEl()) return;
    var tag = (e.target && e.target.tagName) || "";
    if (/^(INPUT|TEXTAREA|SELECT)$/.test(tag) && e.target !== volEl) return;
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    switch (e.key) {
      case " ": case "k": case "K":
        if (tag === "BUTTON" || tag === "A") return; // let focused buttons keep Space/Enter
        e.preventDefault(); togglePlay(); break;
      case "ArrowLeft": if (tag === "INPUT") return; seekBy(-SEEK_STEP); e.preventDefault(); break;
      case "ArrowRight": if (tag === "INPUT") return; seekBy(SEEK_STEP); e.preventDefault(); break;
      case "j": case "J": seekBy(-SEEK_STEP); break;
      case "l": case "L": seekBy(SEEK_STEP); break;
      case "ArrowUp": if (tag === "INPUT") return; video.muted = false; video.volume = Math.min(1, video.volume + 0.1); e.preventDefault(); break;
      case "ArrowDown": if (tag === "INPUT") return; video.volume = Math.max(0, video.volume - 0.1); e.preventDefault(); break;
      case "m": case "M": toggleMute(); break;
      case "f": case "F": toggleFs(); break;
      case "n": case "N": next(); break;
      case "p": case "P": prev(); break;
      case "s": case "S": stop(); break;
      default: return;
    }
  });

  /* ---------- video events ---------- */
  video.addEventListener("play", function () { syncPlayIcon(); wake(); });
  video.addEventListener("pause", function () { syncPlayIcon(); player.classList.remove("is-idle"); clearTimeout(idleTimer); });
  video.addEventListener("playing", function () { show(spinner, false); show(errEl, false); syncPlayIcon(); wake(); });
  video.addEventListener("waiting", function () { if (srcAttached) show(spinner, true); });
  video.addEventListener("canplay", function () { show(spinner, false); });
  video.addEventListener("loadedmetadata", function () {
    if (pendingTime > 0 && isFinite(video.duration) && pendingTime < video.duration - 20) {
      try { video.currentTime = pendingTime; } catch (e) {}
    }
    pendingTime = 0;
    syncTime();
  });
  video.addEventListener("durationchange", syncTime);
  video.addEventListener("progress", syncBuffer);
  video.addEventListener("timeupdate", function () {
    if (!dragging) syncTime();
    syncBuffer();
    var now = Date.now();
    if (now - lastSave > 5000 && video.currentTime > 5) {
      lastSave = now;
      lsSet(tKey(epIndex), String(Math.floor(video.currentTime)));
    }
  });
  video.addEventListener("volumechange", syncVolIcon);
  video.addEventListener("ended", function () {
    lsSet(tKey(epIndex), "done");
    syncPlayIcon();
    if (epIndex < eps.length - 1) {
      toast("Next: " + epLabel(epIndex + 1), 2000);
      loadEpisode(epIndex + 1, { autoplay: true });
    } else {
      wantPlay = false;
      showStart("Finished · tap to watch again");
      srcAttached = false; pendingTime = 0;
      video.removeAttribute("src"); video.load();
      lsDel(tKey(epIndex));
    }
  });
  // Some browsers happily play the audio of an x265/10-bit MKV and show a black frame.
  // videoWidth stays 0 in that case, so treat it exactly like a load error.
  video.addEventListener("loadeddata", function () {
    if (srcAttached && video.videoWidth === 0) onFail(true);
  });
  video.addEventListener("error", function () { onFail(false); });
  function onFail(codecIssue) {
    if (!srcAttached) return; // load() after removeAttribute("src") fires nothing useful
    var failedQ = quality, t = video.currentTime || pendingTime || 0;
    failed[epIndex + ":" + failedQ] = true;
    var alt = pickQuality(epIndex, "");
    if (alt) {
      toast(failedQ + " can\u2019t play here \u2014 trying " + alt + "\u2026", 2500);
      quality = alt;
      pendingTime = t;
      syncEpisodeUi();
      if (attach() && wantPlay) tryPlay();
      return;
    }
    var code = video.error && video.error.code;
    showError((codecIssue || code === 4)
      ? "Your browser can\u2019t play this file format (usually x265/10-bit or MKV audio). Download it and watch in VLC / MX Player."
      : "The video failed to load. Check your connection and retry, or download it instead.");
  }

  /* ---------- Media Session (lock-screen / headset controls) ---------- */
  if ("mediaSession" in navigator) {
    try {
      var ms = navigator.mediaSession;
      ms.setActionHandler("play", play);
      ms.setActionHandler("pause", pause);
      ms.setActionHandler("seekbackward", function () { seekBy(-SEEK_STEP); });
      ms.setActionHandler("seekforward", function () { seekBy(SEEK_STEP); });
      if (eps.length > 1) {
        ms.setActionHandler("nexttrack", next);
        ms.setActionHandler("previoustrack", prev);
      }
    } catch (e) {}
  }

  /* ---------- init ---------- */
  (function init() {
    var v = parseFloat(lsGet("wp:vol"));
    video.volume = isFinite(v) ? Math.min(1, Math.max(0, v)) : 1;
    video.muted = lsGet("wp:muted") === "1";
    syncVolIcon(); syncPlayIcon();

    // which episode to open: ?ep=<key> -> last watched -> first
    var want = new URLSearchParams(window.location.search).get("ep") || lsGet("wp:last:" + cfg.sid);
    var idx = 0;
    for (var i = 0; i < eps.length; i++) if (eps[i].key === want) { idx = i; break; }
    loadEpisode(idx, { autoplay: false });
    if (!document.fullscreenEnabled && !document.webkitFullscreenEnabled && !video.webkitEnterFullscreen) btnFs.style.display = "none";
  })();
})();
