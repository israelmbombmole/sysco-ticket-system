(function () {
  function readI18n(key, fallback) {
    var root = document.getElementById("sysco-upload-i18n");
    if (!root || !root.dataset) {
      return fallback;
    }
    var v = root.dataset[key];
    return v != null && String(v).trim() !== "" ? String(v) : fallback;
  }

  function escapeHtml(s) {
    return String(s || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function formatSize(bytes) {
    if (bytes == null || bytes < 0) {
      return "";
    }
    if (bytes < 1024) {
      return bytes + " B";
    }
    if (bytes < 1024 * 1024) {
      return (bytes / 1024).toFixed(1) + " KB";
    }
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  }

  function applyFiles(input, fileArr) {
    var dt = new DataTransfer();
    (fileArr || []).forEach(function (f) {
      dt.items.add(f);
    });
    input.files = dt.files;
  }

  function buildUploader(input) {
    if (!input || input.dataset.enhancedUploader === "true") {
      return;
    }
    input.dataset.enhancedUploader = "true";
    var singleMode = input.dataset.uploadSingle === "true";
    if (!singleMode) {
      input.multiple = true;
    }

    /** Keeps prior picks when the OS file dialog replaces input.files on each open (multi mode). */
    var accumulatedFiles = Array.from(input.files || []);

    function commitFiles(nextArr) {
      var raw = Array.from(nextArr || []);
      accumulatedFiles = singleMode ? raw.slice(0, 1) : raw.slice();
      applyFiles(input, accumulatedFiles);
      renderList();
    }

    function renderList() {
      var files = Array.from(input.files || []);
      accumulatedFiles = files.slice();
      list.innerHTML = "";
      if (!files.length) {
        list.classList.add("upload-file-list--empty");
        var empty = document.createElement("p");
        empty.className = "upload-file-list-empty";
        empty.textContent = readI18n("empty", "No files selected yet.");
        list.appendChild(empty);
        return;
      }
      list.classList.remove("upload-file-list--empty");
      files.forEach(function (f, idx) {
        var row = document.createElement("div");
        row.className = "upload-file-row";
        row.setAttribute("role", "listitem");
        var btnLabel = readI18n("removeAria", "Remove file");
        row.innerHTML =
          '<div class="upload-file-main">' +
          '<span class="upload-file-name">' +
          escapeHtml(f.name) +
          "</span>" +
          '<span class="upload-file-meta">' +
          escapeHtml(formatSize(f.size)) +
          "</span></div>" +
          '<button type="button" class="upload-file-remove" aria-label="' +
          escapeHtml(btnLabel) +
          '"><span aria-hidden="true">&times;</span></button>';
        row.querySelector(".upload-file-remove").addEventListener("click", function (e) {
          e.preventDefault();
          e.stopPropagation();
          var copy = accumulatedFiles.slice();
          copy.splice(idx, 1);
          commitFiles(copy);
        });
        list.appendChild(row);
      });
    }

    var wrap = document.createElement("div");
    wrap.className = "upload-dropzone-wrap";
    input.parentNode.insertBefore(wrap, input);

    var shell = document.createElement("div");
    shell.className = "upload-dropzone-shell";

    var headerBtn = document.createElement("button");
    headerBtn.type = "button";
    headerBtn.className = "upload-dropzone-header";
    headerBtn.innerHTML =
      '<span class="upload-dropzone-graphic" aria-hidden="true">' +
      '<svg class="upload-dropzone-svg" viewBox="0 0 56 56" width="44" height="44" xmlns="http://www.w3.org/2000/svg">' +
      '<rect x="6" y="18" width="44" height="30" rx="4" fill="currentColor" opacity="0.08"/>' +
      '<path d="M28 10v16m0 0l-5-5m5 5l5-5" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>' +
      '<path d="M14 34h28" stroke="currentColor" stroke-width="2" stroke-linecap="round" opacity="0.35"/>' +
      "</svg></span>" +
      '<span class="upload-dropzone-copy">' +
      '<span class="upload-dropzone-title">' +
      escapeHtml(readI18n("dropTitle", "Add files")) +
      "</span>" +
      '<span class="upload-dropzone-hint">' +
      escapeHtml(readI18n("dropHint", "Drag & drop here or click to browse")) +
      "</span></span>";

    var list = document.createElement("div");
    list.className = "upload-file-list";
    list.setAttribute("role", "list");

    shell.appendChild(headerBtn);
    shell.appendChild(list);
    wrap.appendChild(shell);
    wrap.appendChild(input);

    var progress = document.createElement("div");
    progress.className = "upload-progress";
    progress.innerHTML = '<div class="upload-progress-bar"></div>';
    wrap.appendChild(progress);
    var progressBar = progress.querySelector(".upload-progress-bar");

    input.classList.add("upload-native-input");

    headerBtn.addEventListener("click", function (e) {
      e.stopPropagation();
      input.click();
    });

    list.addEventListener("click", function (e) {
      e.stopPropagation();
    });

    input.addEventListener("change", function () {
      var picked = Array.from(input.files || []);
      if (singleMode) {
        commitFiles(picked);
      } else {
        commitFiles(accumulatedFiles.concat(picked));
      }
    });

    var dragDepth = 0;
    shell.addEventListener("dragenter", function (e) {
      e.preventDefault();
      e.stopPropagation();
      dragDepth++;
      shell.classList.add("dragover");
    });
    shell.addEventListener("dragleave", function (e) {
      e.preventDefault();
      e.stopPropagation();
      dragDepth--;
      if (dragDepth <= 0) {
        dragDepth = 0;
        shell.classList.remove("dragover");
      }
    });
    shell.addEventListener("dragover", function (e) {
      e.preventDefault();
      e.stopPropagation();
    });
    shell.addEventListener("drop", function (e) {
      e.preventDefault();
      e.stopPropagation();
      dragDepth = 0;
      shell.classList.remove("dragover");
      var dropped = Array.from(e.dataTransfer.files || []);
      if (singleMode) {
        commitFiles(dropped[0] ? [dropped[0]] : []);
      } else {
        commitFiles(accumulatedFiles.concat(dropped));
      }
    });

    renderList();

    var form = input.closest("form");
    if (form) {
      form.addEventListener("reset", function () {
        window.setTimeout(function () {
          accumulatedFiles = Array.from(input.files || []);
          renderList();
        }, 0);
      });
    }

    if (form && form.getAttribute("data-upload-enhanced") === "true" && !form.dataset.uploadHooked) {
      form.dataset.uploadHooked = "true";
      /* Full navigation preserves Spring flash messages (success / OTP summary). XHR + location.assign consumes flash. */
      if (form.getAttribute("data-upload-native-submit") !== "true") {
        form.addEventListener("submit", function (e) {
          var enhancedInputs = Array.from(form.querySelectorAll("input[type='file'][data-dnd-uploader='true']"));
          var hasFiles = enhancedInputs.some(function (fi) {
            return (fi.files || []).length > 0;
          });
          if (!hasFiles) {
            return;
          }
          e.preventDefault();
          progress.style.display = "block";
          progressBar.style.width = "1%";

          var xhr = new XMLHttpRequest();
          xhr.open((form.method || "POST").toUpperCase(), form.action, true);
          xhr.upload.onprogress = function (ev) {
            if (!ev.lengthComputable) {
              return;
            }
            var pct = Math.max(1, Math.min(100, Math.round((ev.loaded / ev.total) * 100)));
            progressBar.style.width = pct + "%";
          };
          xhr.onload = function () {
            progressBar.style.width = "100%";
            var target = xhr.responseURL || window.location.href;
            window.location.assign(target);
          };
          xhr.onerror = function () {
            progressBar.style.width = "0%";
            progress.style.display = "none";
            form.submit();
          };
          xhr.send(new FormData(form));
        });
      }
    }
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("input[type='file'][data-dnd-uploader='true']").forEach(buildUploader);
  });
})();
