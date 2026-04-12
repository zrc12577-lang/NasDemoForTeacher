const $ = (id) => document.getElementById(id);

function formatBytes(bytes) {
  if (bytes === Number.MAX_SAFE_INTEGER) return "无限";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let b = bytes, i = 0;
  while (b >= 1024 && i < units.length - 1) { b /= 1024; i++; }
  return `${b.toFixed(i === 0 ? 0 : 2)} ${units[i]}`;
}

/** Session cookie (登录后由服务端下发 JSESSIONID)，必须带 cookie 才能访问 /api/** */
async function api(path, opts = {}) {
  const res = await fetch(path, {
    credentials: "include",
    ...opts,
    headers: {
      ...(opts.headers || {}),
    },
  });
  if (!res.ok) {
    const txt = await res.text().catch(() => "");
    const err = new Error(txt || `${res.status} ${res.statusText}`);
    err.status = res.status;
    handleKickedOutIfNeeded(res.status);
    throw err;
  }
  const ct = res.headers.get("content-type") || "";
  if (ct.includes("application/json")) return await res.json();
  return await res.text();
}

function setErr(el, msg) {
  el.textContent = msg;
  el.classList.toggle("hidden", !msg);
}

/** 网盘内相对路径（与服务器 FileItem.path 一致） */
function normalizeRelDir(s) {
  if (!s) return "";
  return String(s).replace(/\\/g, "/").replace(/^\/+|\/+$/g, "");
}

function parentRelPath(s) {
  const t = normalizeRelDir(s);
  if (!t) return "";
  const i = t.lastIndexOf("/");
  return i < 0 ? "" : t.slice(0, i);
}

let selectedUsername = "";
let kickedOutAlertShown = false;

function handleKickedOutIfNeeded(status) {
  if (status !== 401) return;
  if ($("app").classList.contains("hidden")) return;
  if (kickedOutAlertShown) return;
  kickedOutAlertShown = true;
  alert("账号已在其他设备登录");
  showLogin().catch(() => {});
}

async function refreshMe() {
  const me = await api("/api/me");
  kickedOutAlertShown = false;
  $("who").textContent = me.username;
  $("role").textContent = me.role;
  $("used").textContent = formatBytes(me.usedBytes);
  $("quota").textContent = me.quotaBytes === 9223372036854775807 ? "无限" : formatBytes(me.quotaBytes);
  const upl = (me.defaultUploadRelPath || "").trim();
  $("uploadTargetHint").textContent = upl
    ? `Root 指定的上传前缀：${upl}/（再叠加下方「目录」）`
    : "Root 未指定上传前缀；文件落在当前「目录」下（留空=网盘根）。";
  $("logoutBtn").classList.remove("hidden");
  $("app").classList.remove("hidden");
  $("loginCard").classList.add("hidden");

  const isAdmin = me.role === "ADMIN";
  $("adminPanel").classList.toggle("hidden", !isAdmin);
  if (isAdmin) {
    await refreshAdmin();
  }
}

async function refreshAdmin() {
  const s = await api("/api/admin/settings");
  $("baseDir").value = s.baseDir;
  $("totalGb").value = Math.round(s.totalQuotaBytes / 1024 / 1024 / 1024);
  $("defaultUserGb").value = Math.round(s.defaultUserQuotaBytes / 1024 / 1024 / 1024);
  $("defaultUploadRel").value = s.defaultUploadRelPath || "";

  const users = await api("/api/admin/users");
  const tbody = $("usersTbody");
  tbody.innerHTML = "";
  users.forEach(u => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td class="mono">${u.username}</td>
      <td>${u.role}</td>
      <td>${u.quotaBytes === 9223372036854775807 ? "无限" : formatBytes(u.quotaBytes)}</td>
      <td>${u.username === "root" ? "" : `<button class="btn danger" data-del="${u.username}">删除</button>`}</td>
    `;
    tbody.appendChild(tr);
  });

  tbody.querySelectorAll("button[data-del]").forEach(btn => {
    btn.addEventListener("click", async () => {
      const user = btn.getAttribute("data-del");
      if (!confirm(`确定删除用户 ${user} ?`)) return;
      await api(`/api/admin/users/${encodeURIComponent(user)}`, { method: "DELETE" });
      await refreshAdmin();
    });
  });
}

function enterSubdir(relPath) {
  const p = normalizeRelDir(relPath);
  $("dir").value = p ? (p.endsWith("/") ? p : p + "/") : "";
}

async function refreshFiles() {
  setErr($("fileErr"), "");
  const dir = $("dir").value.trim();
  const items = await api(`/api/files?dir=${encodeURIComponent(dir)}`);
  const tbody = $("filesTbody");
  tbody.innerHTML = "";

  const upBtn = $("upDirBtn");
  if (upBtn) {
    upBtn.disabled = !normalizeRelDir(dir);
  }

  items.forEach(it => {
    const tr = document.createElement("tr");
    const type = it.isDir ? "文件夹" : "文件";
    const size = it.isDir ? "-" : formatBytes(it.sizeBytes);
    const time = new Date(it.modifiedAtMs).toLocaleString();

    const tdName = document.createElement("td");
    tdName.className = "mono";
    tdName.textContent = it.name;

    const tdType = document.createElement("td");
    tdType.textContent = type;

    const tdSize = document.createElement("td");
    tdSize.textContent = size;

    const tdTime = document.createElement("td");
    tdTime.textContent = time;

    const tdAct = document.createElement("td");
    tdAct.className = "file-actions";

    if (it.isDir) {
      const bSelect = document.createElement("button");
      bSelect.type = "button";
      bSelect.className = "btn secondary";
      bSelect.textContent = "选择";
      bSelect.addEventListener("click", async () => {
        enterSubdir(it.path);
        await refreshFiles();
      });

      const aZip = document.createElement("a");
      aZip.className = "btn secondary";
      aZip.textContent = "下载文件夹";
      aZip.href = `/api/files/download-zip?path=${encodeURIComponent(it.path)}`;
      aZip.target = "_blank";
      aZip.rel = "noopener";

      const bDel = document.createElement("button");
      bDel.type = "button";
      bDel.className = "btn danger";
      bDel.textContent = "删除";
      bDel.addEventListener("click", async () => {
        if (!confirm(`确定删除文件夹 ${it.path} ?`)) return;
        await api(`/api/files?path=${encodeURIComponent(it.path)}`, { method: "DELETE" });
        await refreshMe();
        await refreshFiles();
      });

      tdAct.append(bSelect, document.createTextNode(" "), aZip, document.createTextNode(" "), bDel);
    } else {
      const aDl = document.createElement("a");
      aDl.className = "btn secondary";
      aDl.textContent = "下载";
      aDl.href = `/api/files/download?path=${encodeURIComponent(it.path)}`;
      aDl.target = "_blank";
      aDl.rel = "noopener";

      const bDel = document.createElement("button");
      bDel.type = "button";
      bDel.className = "btn danger";
      bDel.textContent = "删除";
      bDel.addEventListener("click", async () => {
        if (!confirm(`确定删除 ${it.path} ?`)) return;
        await api(`/api/files?path=${encodeURIComponent(it.path)}`, { method: "DELETE" });
        await refreshMe();
        await refreshFiles();
      });

      tdAct.append(aDl, document.createTextNode(" "), bDel);
    }

    tr.append(tdName, tdType, tdSize, tdTime, tdAct);
    tbody.appendChild(tr);
  });
}

async function loadUserPickList() {
  const wrap = $("userList");
  wrap.innerHTML = "";
  const names = await api("/api/auth/usernames");
  names.forEach((name, i) => {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "btn secondary user-chip";
    b.textContent = `用户${i + 1}：${name}`;
    b.addEventListener("click", () => {
      selectedUsername = name;
      $("pickedUser").textContent = name;
      $("pickStep").classList.add("hidden");
      $("passwordStep").classList.remove("hidden");
      setErr($("loginErr"), "");
      $("password").value = "";
      $("password").focus();
    });
    wrap.appendChild(b);
  });
}

async function showLogin() {
  $("who").textContent = "";
  $("logoutBtn").classList.add("hidden");
  $("app").classList.add("hidden");
  $("loginCard").classList.remove("hidden");
  $("pickStep").classList.remove("hidden");
  $("passwordStep").classList.add("hidden");
  selectedUsername = "";
  setErr($("loginErr"), "");
  $("password").value = "";
  try {
    await loadUserPickList();
  } catch (e) {
    setErr($("loginErr"), "无法加载用户列表：" + (e.message || e));
  }
}

$("backPickBtn").addEventListener("click", () => {
  $("passwordStep").classList.add("hidden");
  $("pickStep").classList.remove("hidden");
  setErr($("loginErr"), "");
  $("password").value = "";
});

$("loginBtn").addEventListener("click", async () => {
  setErr($("loginErr"), "");
  const p = $("password").value;
  if (!selectedUsername || !p) return setErr($("loginErr"), "请选择账号并输入密码");
  try {
    await api("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: selectedUsername, password: p }),
    });
  } catch (e) {
    if (e.status === 401) {
      return setErr($("loginErr"), "密码错误，或该账号在数据库里不是默认密码（若曾改过密码请用新密码）。");
    }
    return setErr($("loginErr"), "登录请求失败：" + (e.message || e));
  }
  try {
    await refreshMe();
    await refreshFiles();
  } catch (e) {
    setErr(
      $("loginErr"),
      "密码已通过，但进入主页失败：" +
        (e.message || e) +
        "。若你用 127.0.0.1 打开而之前用 localhost 登录（或反过来），请固定用同一地址访问，否则会话 Cookie 不会带上。"
    );
  }
});

$("logoutBtn").addEventListener("click", async () => {
  try {
    await fetch("/api/auth/logout", { method: "POST", credentials: "include" });
  } catch (_) { /* ignore */ }
  await showLogin();
});

$("refreshBtn").addEventListener("click", async () => {
  await refreshMe();
  await refreshFiles();
});

$("upDirBtn").addEventListener("click", async () => {
  $("dir").value = parentRelPath($("dir").value.trim());
  await refreshFiles();
});

$("dir").addEventListener("input", () => {
  const upBtn = $("upDirBtn");
  if (upBtn) {
    upBtn.disabled = !normalizeRelDir($("dir").value.trim());
  }
});

$("mkdirBtn").addEventListener("click", async () => {
  setErr($("fileErr"), "");
  const base = $("dir").value.trim();
  const name = $("mkdirName").value.trim();
  if (!name) return;
  const full = (base ? base.replace(/\/?$/, "/") : "") + name;
  try {
    await api(`/api/files/mkdir?dir=${encodeURIComponent(full)}`, { method: "POST" });
    $("mkdirName").value = "";
    await refreshFiles();
  } catch (e) {
    setErr($("fileErr"), String(e.message || e));
  }
});

function collectUploadFileList() {
  const folder = $("folderPicker").files;
  const files = $("filesPicker").files;
  if (folder && folder.length) return Array.from(folder);
  if (files && files.length) return Array.from(files);
  return [];
}

function relativePathForUpload(file) {
  if (file.webkitRelativePath && file.webkitRelativePath.length > 0) {
    return file.webkitRelativePath.replace(/\\/g, "/");
  }
  return file.name;
}

$("filesPicker").addEventListener("change", () => {
  $("folderPicker").value = "";
});
$("folderPicker").addEventListener("change", () => {
  $("filesPicker").value = "";
});

$("uploadBtn").addEventListener("click", async () => {
  setErr($("fileErr"), "");
  const dir = $("dir").value.trim();
  const list = collectUploadFileList();
  if (!list.length) return setErr($("fileErr"), "请先「选择文件」或「选择文件夹」");
  const form = new FormData();
  for (const f of list) {
    form.append("files", f, relativePathForUpload(f));
  }
  try {
    await api(`/api/files/upload?dir=${encodeURIComponent(dir)}`, { method: "POST", body: form });
    $("filesPicker").value = "";
    $("folderPicker").value = "";
    await refreshMe();
    await refreshFiles();
  } catch (e) {
    setErr($("fileErr"), String(e.message || e));
  }
});

$("saveSettingsBtn").addEventListener("click", async () => {
  $("settingsMsg").textContent = "";
  const baseDir = $("baseDir").value.trim();
  const totalGb = Number($("totalGb").value || 0);
  const defGb = Number($("defaultUserGb").value || 0);
  if (!baseDir || totalGb <= 0 || defGb <= 0) {
    $("settingsMsg").textContent = "请填写存储目录、总配额、默认用户配额";
    return;
  }
  const payload = {
    baseDir,
    totalQuotaBytes: Math.round(totalGb * 1024 * 1024 * 1024),
    defaultUserQuotaBytes: Math.round(defGb * 1024 * 1024 * 1024),
    defaultUploadRelPath: $("defaultUploadRel").value.trim(),
  };
  await api("/api/admin/settings", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  $("settingsMsg").textContent = "已保存（上传前缀与配额已生效）";
  await refreshMe();
});

$("createUserBtn").addEventListener("click", async () => {
  const username = $("newU").value.trim();
  const password = $("newP").value;
  const q = $("newQ").value.trim();
  if (!username || !password) return;
  const payload = { username, password, quotaBytes: q ? Math.round(Number(q) * 1024 * 1024 * 1024) : null };
  await api("/api/admin/users", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  $("newU").value = "";
  $("newP").value = "";
  $("newQ").value = "";
  await refreshAdmin();
});

function parseBatchUsersText(raw) {
  const lines = String(raw || "").split(/\r?\n/);
  const users = [];
  for (const line of lines) {
    const t = line.trim();
    if (!t || t.startsWith("#")) continue;
    const parts = t.split(",").map((x) => x.trim());
    if (parts.length < 2) {
      throw new Error(`格式错误：${line}`);
    }
    const username = parts[0];
    const password = parts[1];
    if (!username || !password) {
      throw new Error(`用户名或密码为空：${line}`);
    }
    let quotaBytes = null;
    if (parts[2]) {
      const quotaGb = Number(parts[2]);
      if (!Number.isFinite(quotaGb) || quotaGb <= 0) {
        throw new Error(`配额必须是正数GB：${line}`);
      }
      quotaBytes = Math.round(quotaGb * 1024 * 1024 * 1024);
    }
    users.push({ username, password, quotaBytes });
  }
  return users;
}

$("batchCreateBtn")?.addEventListener("click", async () => {
  const text = $("batchUsers")?.value || "";
  const users = parseBatchUsersText(text);
  if (!users.length) {
    return alert("没有可创建的用户，请至少填写一行。");
  }
  const result = await api("/api/admin/users/batch", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ users }),
  });
  $("batchUsers").value = "";
  await refreshAdmin();
  alert(`批量创建完成：${result.created ?? users.length} 个用户`);
});

$("setPwdBtn")?.addEventListener("click", async () => {
  const username = $("pwdU").value.trim();
  const password = $("pwdP").value;
  if (!username || !password) return;
  await api("/api/admin/users/password", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  $("pwdP").value = "";
  alert("密码已更新");
});

$("selfChangePwdBtn")?.addEventListener("click", async () => {
  const oldPassword = $("selfOldPwd").value;
  const newPassword = $("selfNewPwd").value;
  $("selfPwdMsg").textContent = "";
  if (!oldPassword || !newPassword) {
    $("selfPwdMsg").textContent = "请填写当前密码和新密码";
    return;
  }
  try {
    await api("/api/auth/change-password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ oldPassword, newPassword }),
    });
    $("selfOldPwd").value = "";
    $("selfNewPwd").value = "";
    $("selfPwdMsg").textContent = "密码修改成功";
    alert("密码修改成功");
  } catch (e) {
    if (e.status === 401) {
      $("selfPwdMsg").textContent = "当前密码不正确";
      return;
    }
    $("selfPwdMsg").textContent = "修改失败：" + (e.message || e);
  }
});

/** 服务器网盘内相对路径（非本机盘符）；用于目录浏览弹窗 */
let dirPickerState = "";

function escapeHtml(t) {
  const d = document.createElement("div");
  d.textContent = t;
  return d.innerHTML;
}

function openDirPickerModal() {
  dirPickerState = normalizeRelDir($("dir").value);
  $("dirPickerModal").classList.remove("hidden");
  $("dirPickerModal").setAttribute("aria-hidden", "false");
  dirPickerLoad().catch((e) => {
    setErr($("fileErr"), String(e.message || e));
    closeDirPickerModal();
  });
}

function closeDirPickerModal() {
  $("dirPickerModal").classList.add("hidden");
  $("dirPickerModal").setAttribute("aria-hidden", "true");
}

async function dirPickerLoad() {
  const dir = dirPickerState;
  const items = await api(`/api/files?dir=${encodeURIComponent(dir)}`);
  const folders = items.filter((it) => it.isDir);
  $("dirPickerCrumb").textContent = dir
    ? `当前位置：${dir}（相对你的网盘根）`
    : "当前位置：网盘根目录";
  const list = $("dirPickerList");
  const empty = $("dirPickerEmpty");
  list.innerHTML = "";
  if (folders.length === 0) {
    empty.classList.remove("hidden");
  } else {
    empty.classList.add("hidden");
    folders.forEach((it) => {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "dir-picker-row";
      b.innerHTML = `<span>📁 ${escapeHtml(it.name)}</span><span class="muted">进入</span>`;
      b.addEventListener("click", () => {
        dirPickerState = normalizeRelDir(it.path);
        dirPickerLoad().catch((err) => alert(err.message || err));
      });
      list.appendChild(b);
    });
  }
}

$("pickDirBtn").addEventListener("click", () => openDirPickerModal());
$("dirPickerCancel").addEventListener("click", () => closeDirPickerModal());
$("dirPickerBackdrop").addEventListener("click", () => closeDirPickerModal());
$("dirPickerUp").addEventListener("click", () => {
  dirPickerState = parentRelPath(dirPickerState);
  dirPickerLoad().catch((e) => alert(e.message || e));
});
$("dirPickerRefresh").addEventListener("click", () => {
  dirPickerLoad().catch((e) => alert(e.message || e));
});
$("dirPickerOk").addEventListener("click", async () => {
  $("dir").value = dirPickerState;
  closeDirPickerModal();
  await refreshFiles();
});

document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && !$("dirPickerModal").classList.contains("hidden")) {
    closeDirPickerModal();
  }
});

(async () => {
  try {
    await refreshMe();
    await refreshFiles();
  } catch {
    await showLogin();
  }
})();
