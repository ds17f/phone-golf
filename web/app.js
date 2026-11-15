// Phone Golf web client. No build step: plain ES modules.
const $app = document.getElementById("app");
const $who = document.getElementById("who");
const KEY = "phonegolf.token";

// ---- helpers -------------------------------------------------------------

const store = {
  get() { try { return localStorage.getItem(KEY); } catch { return null; } },
  set(v) { try { v ? localStorage.setItem(KEY, v) : localStorage.removeItem(KEY); } catch {} },
};

function localDate(offsetDays = 0) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

const esc = (s) =>
  String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

function fmtToPar(n) {
  if (n === null || n === undefined) return '<span class="muted">–</span>';
  if (n === 0) return '<span class="even">E</span>';
  return n < 0 ? `<span class="under">${n}</span>` : `<span class="over">+${n}</span>`;
}

function fmtDate(d) {
  return new Date(d + "T12:00:00").toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" });
}

async function api(method, path, body) {
  const headers = { "x-local-date": localDate() };
  const token = store.get();
  if (token) headers.authorization = `Bearer ${token}`;
  if (body !== undefined) headers["content-type"] = "application/json";
  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(data.error || `Request failed (${res.status})`);
    err.status = res.status;
    throw err;
  }
  return data;
}

function form(el, handler) {
  el.addEventListener("submit", async (e) => {
    e.preventDefault();
    const msg = el.querySelector(".msg");
    const btn = el.querySelector("button[type=submit]");
    if (msg) msg.textContent = "";
    btn && (btn.disabled = true);
    try {
      await handler(Object.fromEntries(new FormData(el)));
    } catch (err) {
      if (msg) { msg.className = "msg err"; msg.textContent = err.message; }
    } finally {
      btn && (btn.disabled = false);
    }
  });
}

let me = null;

// ---- views ---------------------------------------------------------------

function viewWelcome() {
  $who.textContent = "";
  $app.innerHTML = `
    <h1>Golf, but for your phone</h1>
    <p class="sub">Each day is a hole. Your score is how many times you unlocked your phone. Low score wins.</p>
    <form class="card" id="f-new">
      <h2>New player</h2>
      <div class="row">
        <div><label for="name">Your name</label><input id="name" name="name" maxlength="40" required autocomplete="nickname"></div>
        <div style="flex:0 0 auto"><button type="submit">Start playing</button></div>
      </div>
      <p class="msg"></p>
    </form>
    <form class="card" id="f-key">
      <h2>Already playing on your phone?</h2>
      <p class="muted">Open the app, go to Settings, and copy your player key. Paste it here to see the same player on this computer.</p>
      <div class="row">
        <div><label for="key">Player key</label><input id="key" name="key" required autocomplete="off"></div>
        <div style="flex:0 0 auto"><button type="submit" class="ghost">Sign in</button></div>
      </div>
      <p class="msg"></p>
    </form>`;
  form(document.getElementById("f-new"), async ({ name }) => {
    const r = await api("POST", "/api/players", { name, platform: "web" });
    store.set(r.token);
    await boot();
  });
  form(document.getElementById("f-key"), async ({ key }) => {
    store.set(key.trim());
    try { await api("GET", "/api/me"); } catch (e) { store.set(null); throw new Error("That key does not match a player."); }
    await boot();
  });
}

// The round that the home page shows. A player plays one round at a time:
// the active course, else the next course, else the last course.
function featuredRound(groups) {
  const all = groups.flatMap((g) => g.courses.map((c) => ({ group: g, course: c })));
  const by = (status) => all.filter((x) => x.course.status === status).sort((a, b) => a.course.startDate.localeCompare(b.course.startDate));
  return by("active").pop() ?? by("upcoming")[0] ?? by("finished").pop();
}

const groupForms = (handicapHint) => `
    <div class="row">
      <form class="card" id="f-join">
        <h2>Join a group</h2>
        <label for="code">Invite code</label><input id="code" name="code" required autocomplete="off" style="text-transform:uppercase">
        <label for="jh" style="margin-top:8px">Your handicap</label><input id="jh" name="handicap" type="number" min="0" required inputmode="numeric">
        <p class="muted" style="font-size:13px">${handicapHint}</p>
        <button type="submit">Join</button><p class="msg"></p>
      </form>
      <form class="card" id="f-group">
        <h2>Make a group</h2>
        <label for="gn">Group name</label><input id="gn" name="name" required maxlength="40">
        <label for="gh" style="margin-top:8px">Your handicap</label><input id="gh" name="handicap" type="number" min="0" required inputmode="numeric">
        <p class="muted" style="font-size:13px">You can accept a calculated handicap later.</p>
        <button type="submit">Make group</button><p class="msg"></p>
      </form>
    </div>`;

function wireGroupForms() {
  const done = async () => {
    if (location.hash === "#/" || location.hash === "") await route();
    else location.hash = "#/";
  };
  form(document.getElementById("f-join"), async ({ code, handicap }) => {
    await api("POST", "/api/groups/join", { code, handicap: Number(handicap) });
    await done();
  });
  form(document.getElementById("f-group"), async ({ name, handicap }) => {
    await api("POST", "/api/groups", { name, handicap: Number(handicap) });
    await done();
  });
}

// The home page is the scorecard of the current round.
async function viewHome() {
  const { groups } = await api("GET", "/api/groups");
  const pick = featuredRound(groups);
  const today = localDate();

  if (!pick) {
    const g = groups[0];
    $app.innerHTML = g
      ? `<section class="empty"><p>No round is on.</p><button id="start">Start a round</button><p class="msg"></p></section>`
      : `<h1>Play with a group</h1>
         <p class="sub">The website needs a group. To play alone, use the phone app.</p>
         ${groupForms("The number of unlocks or pickups on a normal day.")}`;
    if (!g) return wireGroupForms();
    document.getElementById("start").addEventListener("click", async () => {
      await api("POST", `/api/groups/${g.id}/courses`, { name: `The ${g.name} Open`, startDate: today, weekdayPar: 0, weekendPar: 0 });
      await viewHome();
    });
    return;
  }

  const data = await api("GET", `/api/courses/${pick.course.id}/leaderboard`);
  const mine = data.leaderboard.find((p) => p.playerId === me.id);
  $app.innerHTML = `
    ${stripHtml(data.course, mine, today)}
    <form class="mark" id="f-score">
      <strong>Mark your card</strong>
      <input name="date" type="date" value="${localDate(-1)}" max="${today}" required aria-label="Day">
      <input name="count" type="number" min="0" max="10000" inputmode="numeric" required placeholder="Count" aria-label="Count">
      <select name="metric" aria-label="What you counted"><option value="pickups">pickups</option><option value="unlocks">unlocks</option></select>
      <button type="submit">Write it down</button>
      <p class="msg"></p>
    </form>
    ${paperHtml(data, today)}
    ${data.leaderboard.length > 1 ? boardHtml(data, today) : ""}
    ${data.course.status === "finished" ? `<p><button id="start">Start a new round</button></p>` : ""}`;

  form(document.getElementById("f-score"), async ({ date, count, metric }) => {
    await api("PUT", `/api/scores/${date}`, { count: Number(count), metric, source: "manual" });
    await viewHome();
  });
  wirePar(pick.course.id, viewHome);
  document.getElementById("start")?.addEventListener("click", async () => {
    await api("POST", `/api/groups/${pick.group.id}/courses`, { name: `The ${pick.group.name} Open`, startDate: today, weekdayPar: 0, weekendPar: 0 });
    await viewHome();
  });
}

async function viewGroup(id) {
  const [{ group: g }, hcp] = await Promise.all([api("GET", `/api/groups/${id}`), api("GET", `/api/groups/${id}/handicap`)]);
  $app.innerHTML = `
    <p><a href="#/settings">← Settings</a></p>
    <h1>${esc(g.name)}</h1>
    <p class="sub">Invite code: <span class="code">${esc(g.inviteCode)}</span></p>

    <section class="card">
      <h2>Courses</h2>
      ${g.courses.length ? `<ul class="list">${g.courses.map((c) => `<li>
        <a href="#/course/${c.id}">${esc(c.name)}</a>
        <span><span class="muted">${fmtDate(c.startDate)} – ${fmtDate(c.endDate)}</span>
        <span class="pill ${c.status}">${c.status === "active" ? `hole ${c.currentHole}` : c.status}</span></span></li>`).join("")}</ul>`
        : `<p class="muted">No courses yet. A course is 18 days in a row.</p>`}
      <details ${g.courses.length ? "" : "open"}><summary>New course</summary>
        <form id="f-course">
          <div class="row">
            <div><label for="cn">Name</label><input id="cn" name="name" required maxlength="40" value="The ${esc(g.name)} Open"></div>
            <div><label for="cs">First day (hole 1)</label><input id="cs" name="startDate" type="date" required value="${localDate()}"></div>
          </div>
          <div class="row" style="margin-top:8px">
            <div><label for="wp">Par on weekdays</label><input id="wp" name="weekdayPar" type="number" required value="0"></div>
            <div><label for="ep">Par on weekends</label><input id="ep" name="weekendPar" type="number" required value="0"></div>
            <div style="flex:0 0 auto"><button type="submit">Make course</button></div>
          </div>
          <p class="muted" style="font-size:13px">Par is how many unlocks above your handicap count as "even". Par 0 means: stay at your baseline.</p>
          <p class="msg"></p>
        </form>
      </details>
    </section>

    <section class="card">
      <h2>Players</h2>
      <ul class="list">${g.members.map((m) => `<li><span>${esc(m.name)}${m.id === g.ownerId ? ' <span class="pill">owner</span>' : ""}</span>
        <span class="muted">handicap ${m.handicap} · ${m.platform}</span></li>`).join("")}</ul>
    </section>

    <section class="card">
      <h2>Your handicap: ${hcp.current}</h2>
      ${hcp.suggestion ? `<p>Suggested: <strong>${hcp.suggestion.value}</strong> <span class="muted">— ${esc(hcp.suggestion.explanation)}</span></p>
        ${hcp.suggestion.value !== hcp.current ? `<button id="accept">Use ${hcp.suggestion.value}</button>` : `<p class="ok">Your handicap matches the suggestion.</p>`}`
        : `<p class="muted">Score ${hcp.suggestionNeedsDays} more day${hcp.suggestionNeedsDays === 1 ? "" : "s"} to get a suggested handicap.</p>`}
      <details><summary>Set a different number</summary>
        <form id="f-hcp" class="row">
          <div><label for="hv">Handicap</label><input id="hv" name="handicap" type="number" min="0" required value="${hcp.current}"></div>
          <div style="flex:0 0 auto"><button type="submit" class="ghost">Save</button></div>
          <p class="msg"></p>
        </form>
      </details>
      <p class="muted" style="font-size:13px">Handicap changes start tomorrow.</p>
      <p class="msg" id="hmsg"></p>
    </section>

    <button class="ghost small" id="leave">Leave group</button>`;

  form(document.getElementById("f-course"), async (v) => {
    await api("POST", `/api/groups/${id}/courses`, {
      name: v.name, startDate: v.startDate, weekdayPar: Number(v.weekdayPar), weekendPar: Number(v.weekendPar),
    });
    location.hash = "#/";
  });
  form(document.getElementById("f-hcp"), async ({ handicap }) => {
    await api("PUT", `/api/groups/${id}/handicap`, { handicap: Number(handicap), source: "manual" });
    await viewGroup(id);
  });
  document.getElementById("accept")?.addEventListener("click", async () => {
    await api("PUT", `/api/groups/${id}/handicap`, { handicap: hcp.suggestion.value, source: "suggestion" });
    await viewGroup(id);
  });
  document.getElementById("leave").addEventListener("click", async () => {
    if (!confirm(`Leave ${g.name}? Your scores stay, but you leave the leaderboard.`)) return;
    await api("DELETE", `/api/groups/${id}/membership`);
    location.hash = "#/";
  });
}

// Tournament positions with ties: 1, T2, T2, 4. Players with no holes get no position.
function positions(board) {
  const out = [];
  board.forEach((p, i) => {
    if (!p.thru) return out.push("");
    const first = board.findIndex((q) => q.thru && q.totalToPar === p.totalToPar);
    const tied = board.filter((q) => q.thru && q.totalToPar === p.totalToPar).length > 1;
    out.push((tied ? "T" : "") + (first + 1));
  });
  return out;
}

// One scorecard cell: the literal count, circled when under target, boxed when over.
function scoreCell(h, today, mine) {
  const cls = ["sc"];
  if (h.date === today) cls.push("now");
  // A dash: a different player has no score for a finished hole.
  if (h.gross === null) return `<td class="${cls.join(" ")}">${!mine && h.date < today ? '<span class="muted">–</span>' : ""}</td>`;
  const mark = h.toPar < 0 ? (h.toPar <= -10 ? "eagle" : "birdie") : h.toPar > 0 ? (h.toPar >= 10 ? "double" : "bogey") : "par";
  const tip = `${fmtDate(h.date)}: ${h.gross} ${h.metric ?? ""}, handicap ${h.handicap}, par ${h.par} → ${h.toPar === 0 ? "E" : h.toPar > 0 ? "+" + h.toPar : h.toPar}`;
  return `<td class="${cls.join(" ")}" title="${esc(tip)}"><span class="mk ${mark}">${h.gross}</span></td>`;
}

function nine(holes, from, to) {
  return holes.filter((h) => h.hole >= from && h.hole <= to);
}

function sumGross(hs) {
  const played = hs.filter((h) => h.gross !== null);
  return played.length ? played.reduce((a, h) => a + h.gross, 0) : "";
}

function scorecardHalf(label, from, to, holes, board, today, mine) {
  const hs = nine(holes, from, to);
  const dow = (d) => new Date(d + "T12:00:00").toLocaleDateString(undefined, { weekday: "narrow" });
  return `
    <table class="scorecard">
      <thead>
        <tr class="r-hole"><th>Hole</th>${hs.map((h) => `<th class="${h.date === today ? "now" : ""}">${h.hole}</th>`).join("")}
          <th class="tot">${label}</th></tr>
        <tr class="r-day"><th>Day</th>${hs.map((h) => `<th title="${fmtDate(h.date)}">${dow(h.date)}</th>`).join("")}
          <th class="tot"></th></tr>
        <tr class="r-par"><th>Par</th>${hs.map((h) => `<th>${mine
          ? `<button class="parbtn" data-hole="${h.hole}" data-par="${h.par}" title="Change par for hole ${h.hole}">${h.par}</button>`
          : h.par}</th>`).join("")}
          <th class="tot">${hs.reduce((a, h) => a + h.par, 0)}</th></tr>
      </thead>
      <tbody>${board.map((p) => {
        const mineHs = nine(p.holes, from, to);
        return `<tr class="${p.playerId === me.id ? "me" : ""}">
          <td class="pname"><span>${esc(p.name)}</span><small>hcp ${p.handicap}</small></td>
          ${mineHs.map((h) => scoreCell(h, today, p.playerId === me.id)).join("")}
          <td class="tot">${sumGross(mineHs)}</td>
        </tr>`;
      }).join("")}</tbody>
    </table>`;
}

// Out, In, Tot and the total to par for each player.
function totalsHtml(board) {
  return `
    <table class="scorecard totals">
      <thead><tr class="r-hole"><th></th><th class="tot">Out</th><th class="tot">In</th><th class="tot">Tot</th><th class="tot">±</th></tr></thead>
      <tbody>${board.map((p) => `<tr class="${p.playerId === me.id ? "me" : ""}">
          <td class="pname"><span>${esc(p.name)}</span><small>hcp ${p.handicap}</small></td>
          <td>${sumGross(nine(p.holes, 1, 9))}</td><td>${sumGross(nine(p.holes, 10, 18))}</td>
          <td class="tot">${sumGross(p.holes)}</td><td class="tot">${p.thru ? fmtToPar(p.totalToPar) : ""}</td>
        </tr>`).join("")}</tbody>
    </table>`;
}

// The paper scorecard: two nines and the totals. It fits the width of a phone.
function paperHtml({ course, holes, leaderboard }, today) {
  const mine = course.createdBy === me.id;
  return `
    <section class="paper" aria-label="Scorecard">
      <div class="paper-head">
        <div>
          <div class="paper-title">${esc(course.name)}</div>
          <div class="paper-sub">${fmtDate(course.startDate)} – ${fmtDate(course.endDate)}</div>
        </div>
        <div class="paper-badge">⛳ Scorecard</div>
      </div>
      <div class="half">${scorecardHalf("Out", 1, 9, holes, leaderboard, today, mine)}</div>
      <div class="half">${scorecardHalf("In", 10, 18, holes, leaderboard, today, mine)}</div>
      <div class="half">${totalsHtml(leaderboard)}</div>
      <div class="legend">
        <span><span class="mk birdie">12</span> under</span>
        <span><span class="mk bogey">40</span> over</span>
        <span><span class="mk eagle">8</span> 10+</span>
      </div>
      <p class="fine">Numbers are literal unlocks or pickups. Target = handicap + par.
        ${mine ? "You made this course: tap a par to change it." : ""}</p>
    </section>`;
}

function wirePar(courseId, rerender) {
  $app.querySelectorAll("button.parbtn").forEach((b) =>
    b.addEventListener("click", async () => {
      const v = prompt(`Par for hole ${b.dataset.hole}`, b.dataset.par);
      if (v === null || v.trim() === "" || Number.isNaN(Number(v))) return;
      await api("PATCH", `/api/courses/${courseId}/holes/${b.dataset.hole}`, { par: Math.round(Number(v)) });
      await rerender();
    }),
  );
}

// Three plaques on the green board: the hole, today (or the target), and the round to par.
function stripHtml(course, p, today) {
  const h = p?.holes.find((x) => x.date === today);
  const plaque = (label, value, sub) => `<div><span class="lbl">${label}</span><div class="plq"><b>${value}</b><small>${sub}</small></div></div>`;
  const hole = course.status === "active" ? plaque("Hole", course.currentHole, "of 18")
    : course.status === "upcoming" ? plaque("Hole", "–", `starts ${fmtDate(course.startDate)}`) : plaque("Hole", "F", "final");
  const mid = !h ? plaque("Handicap", p ? p.handicap : "–", "a day")
    : h.gross !== null ? plaque("Today", `<span class="${h.toPar < 0 ? "under" : h.toPar === 0 ? "even" : "over"}">${h.gross}</span>`, `target ${h.handicap + h.par}`)
    : plaque("Target", h.handicap + h.par, "today");
  const round = plaque("Round", p?.thru ? fmtToPar(p.totalToPar) : "–", course.status === "finished" ? "final" : `thru ${p?.thru ?? 0}`);
  return `<section class="strip" aria-label="Scoreboard">${hole}${mid}${round}</section>`;
}

// The green tournament board. Used on the course page and the group page.
function boardHtml({ course, leaderboard }, today) {
  const pos = positions(leaderboard);
  const thruLabel = (p) => {
    if (!p.thru) return "–";
    if (course.status === "finished" || p.holes[17].gross !== null) return "F";
    return p.thru;
  };
  return `
    <section class="board" aria-label="Leaderboard">
      <div class="board-head">
        <div class="board-title">Leaderboard</div>
        <div class="board-sub">${esc(course.name)} · ${course.status === "active" ? `Hole ${course.currentHole} of 18` : course.status === "upcoming" ? `Starts ${fmtDate(course.startDate)}` : "Final"}</div>
      </div>
      <table class="board-table">
        <thead><tr><th>Pos</th><th class="l">Player</th><th>To par</th><th>Today</th><th>Thru</th></tr></thead>
        <tbody>${leaderboard.map((p, i) => {
          const t = p.holes.find((h) => h.date === today);
          return `<tr class="${p.playerId === me.id ? "me" : ""}">
            <td class="pos">${pos[i]}</td>
            <td class="l plaque">${esc(p.name)}</td>
            <td class="big">${p.thru ? fmtToPar(p.totalToPar) : '<span class="muted">–</span>'}</td>
            <td>${t?.toPar != null ? fmtToPar(t.toPar) : "–"}</td>
            <td>${thruLabel(p)}</td>
          </tr>`;
        }).join("")}</tbody>
      </table>
    </section>
`;
}

async function viewCourse(id) {
  const data = await api("GET", `/api/courses/${id}/leaderboard`);
  const today = localDate();
  $app.innerHTML = `
    <p><a href="#/">← Home</a></p>
    ${stripHtml(data.course, data.leaderboard.find((p) => p.playerId === me.id), today)}
    ${paperHtml(data, today)}
    ${data.leaderboard.length > 1 ? boardHtml(data, today) : ""}`;
  wirePar(id, () => viewCourse(id));
}

async function viewSettings() {
  const { groups } = await api("GET", "/api/groups");
  $app.innerHTML = `
    <p><a href="#/">← Home</a></p>
    <h1>Settings</h1>
    <form class="card" id="f-name">
      <h2>Name</h2>
      <div class="row"><div><input name="name" value="${esc(me.name)}" maxlength="40" required aria-label="Name"></div>
      <div style="flex:0 0 auto"><button type="submit">Save</button></div></div><p class="msg"></p>
    </form>
    <section class="card">
      <h2>Groups</h2>
      ${groups.length ? `<ul class="list">${groups.map((g) => `<li><a href="#/group/${g.id}">${esc(g.name)}</a>
        <span class="muted">${g.members.length} player${g.members.length === 1 ? "" : "s"}</span></li>`).join("")}</ul>
        <p class="muted" style="font-size:13px">Open a group for the invite code, your handicap, the courses and the players.</p>`
        : `<p class="muted">You are not in a group yet.</p>`}
    </section>
    ${groupForms("The number of unlocks or pickups on a normal day.")}
    <section class="card">
      <h2>Player key</h2>
      <p class="muted">This key is your login. Paste it on another device to play as the same person. Keep it private.</p>
      <p><input readonly value="${esc(store.get())}" aria-label="Player key" onclick="this.select()"></p>
      <button class="ghost" id="signout">Sign out of this browser</button>
    </section>`;
  wireGroupForms();
  form(document.getElementById("f-name"), async ({ name }) => {
    const r = await api("PATCH", "/api/me", { name });
    me = r.player;
    $who.textContent = me.name;
  });
  document.getElementById("signout").addEventListener("click", () => {
    if (!confirm("Sign out? Copy your player key first, or you lose this player.")) return;
    store.set(null);
    me = null;
    location.hash = "#/";
    boot();
  });
}

// ---- router --------------------------------------------------------------

async function route() {
  if (!me) return viewWelcome();
  const [, view, id] = location.hash.replace(/^#/, "").split("/");
  try {
    if (view === "group" && id) await viewGroup(id);
    else if (view === "course" && id) await viewCourse(id);
    else if (view === "settings") await viewSettings();
    else await viewHome();
  } catch (err) {
    $app.innerHTML = `<p class="err">${esc(err.message)}</p><p><a href="#/">Go home</a></p>`;
  }
}

async function boot() {
  me = null;
  if (store.get()) {
    try {
      me = (await api("GET", "/api/me")).player;
    } catch (err) {
      if (err.status === 401) store.set(null);
    }
  }
  $who.textContent = me ? me.name : "";
  await route();
}

window.addEventListener("hashchange", route);
boot();
