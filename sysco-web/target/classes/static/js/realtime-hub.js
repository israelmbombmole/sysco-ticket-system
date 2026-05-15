(function () {
  if (!window.StompJs || !window.SockJS) return;
  if ("Notification" in window && Notification.permission === "default") {
    try { Notification.requestPermission(); } catch (e) {}
  }
  const client = new StompJs.Client({
    webSocketFactory: () => new SockJS("/ws"),
    reconnectDelay: 5000
  });
  const currentUsername = ((document.body && document.body.dataset && document.body.dataset.syscoUsername) || "")
    .trim()
    .toLowerCase();
  function incrementBadgeInLink(linkSelector, badgeClass) {
    const link = document.querySelector(linkSelector);
    if (!link) return;
    let badge = link.querySelector("." + badgeClass);
    if (!badge) {
      badge = document.createElement("span");
      badge.className = "header-badge " + badgeClass;
      link.appendChild(badge);
    }
    const cur = parseInt(badge.textContent || "0", 10) || 0;
    badge.textContent = String(cur + 1);
  }

  function incrementFloatingChatBadge() {
    const btn = document.querySelector(".floating-chat");
    if (!btn) return;
    let badge = btn.querySelector(".floating-chat-badge");
    if (!badge) {
      badge = document.createElement("span");
      badge.className = "floating-chat-badge";
      btn.appendChild(badge);
    }
    const cur = parseInt(badge.textContent || "0", 10) || 0;
    badge.textContent = String(cur + 1);
  }

  client.onConnect = function () {
    client.subscribe("/user/queue/notifications", function (frame) {
      incrementBadgeInLink("a.header-notif-link", "header-notif-badge");
      let payload = {};
      try {
        payload = JSON.parse(frame.body || "{}");
      } catch (e) {
        payload = {};
      }
      const toast = document.createElement("div");
      toast.className = "toast-notif";
      const typeLabel = payload.typeLabel || "Info";
      toast.textContent = "[" + typeLabel + "] " + (payload.title || "Notification") + " - " + (payload.message || "Nouvelle activité");
      if (payload.targetType === "TICKET" && payload.targetId) {
        toast.style.cursor = "pointer";
        toast.addEventListener("click", function () {
          window.location.href = "/app/ticket-management/" + payload.targetId;
        });
      }
      document.body.appendChild(toast);
      setTimeout(() => {
        toast.remove();
      }, 3500);
    });
    client.subscribe("/user/queue/chat", function (frame) {
      let payload = {};
      try {
        payload = JSON.parse(frame.body || "{}");
      } catch (e) {
        payload = {};
      }
      window.dispatchEvent(new CustomEvent("sysco:chat-message", { detail: payload }));
      const senderUsername = String(payload.senderUsername || "").trim().toLowerCase();
      const isIncomingForCurrentUser = !!senderUsername && !!currentUsername && senderUsername !== currentUsername;
      if (isIncomingForCurrentUser) {
        incrementBadgeInLink("a.header-chat-link", "header-chat-badge");
        incrementFloatingChatBadge();
      }
      const notifTitle = "Nouveau message - " + (payload.senderUsername || "Chat");
      const notifBody = payload.text || "Pièce jointe reçue";
      const chatHref =
        payload && payload.conversationId != null && String(payload.conversationId).length > 0
          ? "/app/chat?conversationId=" + encodeURIComponent(String(payload.conversationId))
          : "/app/chat";
      if ("Notification" in window && document.hidden && Notification.permission === "granted") {
        const n = new Notification(notifTitle, { body: notifBody });
        n.onclick = function () { window.focus(); window.location.href = chatHref; };
      } else {
        const toast = document.createElement("div");
        toast.className = "toast-notif";
        toast.textContent = notifTitle + " - " + notifBody;
        toast.style.cursor = "pointer";
        toast.addEventListener("click", function () { window.location.href = chatHref; });
        document.body.appendChild(toast);
        setTimeout(() => toast.remove(), 3500);
      }
    });
  };
  client.activate();
  const chatBtn = document.querySelector(".floating-chat");
  if (chatBtn) {
    const KEY_X = "sysco.chatBtn.x";
    const KEY_Y = "sysco.chatBtn.y";
    const clamp = (v, min, max) => Math.max(min, Math.min(max, v));
    const place = (x, y) => {
      const maxX = window.innerWidth - chatBtn.offsetWidth - 8;
      const maxY = window.innerHeight - chatBtn.offsetHeight - 8;
      const nx = clamp(x, 8, maxX);
      const ny = clamp(y, 8, maxY);
      chatBtn.style.left = nx + "px";
      chatBtn.style.top = ny + "px";
      chatBtn.style.right = "auto";
      chatBtn.style.bottom = "auto";
      localStorage.setItem(KEY_X, String(nx));
      localStorage.setItem(KEY_Y, String(ny));
    };
    const savedX = parseInt(localStorage.getItem(KEY_X) || "", 10);
    const savedY = parseInt(localStorage.getItem(KEY_Y) || "", 10);
    if (Number.isFinite(savedX) && Number.isFinite(savedY)) {
      place(savedX, savedY);
    }

    let dragging = false;
    let moved = false;
    let pointerId = null;
    let startX = 0;
    let startY = 0;
    let startLeft = 0;
    let startTop = 0;

    chatBtn.addEventListener("pointerdown", function (e) {
      pointerId = e.pointerId;
      dragging = true;
      moved = false;
      chatBtn.setPointerCapture(pointerId);
      const rect = chatBtn.getBoundingClientRect();
      if (!chatBtn.style.left || !chatBtn.style.top) {
        place(rect.left, rect.top);
      }
      startX = e.clientX;
      startY = e.clientY;
      startLeft = parseFloat(chatBtn.style.left || "0");
      startTop = parseFloat(chatBtn.style.top || "0");
    });

    chatBtn.addEventListener("pointermove", function (e) {
      if (!dragging || e.pointerId !== pointerId) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) moved = true;
      place(startLeft + dx, startTop + dy);
    });

    const endDrag = function (e) {
      if (!dragging || e.pointerId !== pointerId) return;
      dragging = false;
      chatBtn.releasePointerCapture(pointerId);
      pointerId = null;
      if (!moved) {
        window.location.href = "/app/chat";
      }
    };

    chatBtn.addEventListener("pointerup", endDrag);
    chatBtn.addEventListener("pointercancel", endDrag);

    window.addEventListener("resize", function () {
      const rect = chatBtn.getBoundingClientRect();
      place(rect.left, rect.top);
    });

    chatBtn.addEventListener("click", function () {
      // Navigation is handled from pointerup to avoid accidental open while dragging.
    });
  }
})();
