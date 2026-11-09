/* Menú común de Calendario y Notas.
   - Al pulsar el logo (img.logo) sale un menú en el centro con el fondo oscurecido:
     Calendario (logo.svg) · Notas (logo-notas.svg: libreta de espiral con casillas) · Tema (sol / luna) · Exportar / Importar (copia de seguridad, solo en la app, en su propia fila)
     · al final, en pequeño, la versión de la app (Recordatorios.version()).   (Más adelante: donaciones, si la app sale en F-Droid.)
   - Tema: claro u oscuro elegido a mano (localStorage «tema»); sin elegir, el del sistema.
     Pone <html data-tema="claro|oscuro">; las páginas pintan el oscuro con :root[data-tema="oscuro"].
   - En la app Android avisa a la app (Recordatorios.tema) para que la barra de estado vaya a juego.
   Se carga en <head> SIN defer, para que no se vea un parpadeo de color al abrir. */
(function () {
  "use strict";
  const html = document.documentElement;
  const sistema = window.matchMedia("(prefers-color-scheme: dark)");
  const leer = () => { try { return localStorage.getItem("tema"); } catch (e) { return null; } };
  const oscuro = () => { const t = leer(); return t ? t === "oscuro" : sistema.matches; };

  // tema con iconos minimalistas del mismo estilo que los logos (sol = pasar a claro, luna = pasar a oscuro)
  const G = "#78858F", C = "#81D4FA";
  const SOL = `<svg viewBox="0 0 48 48" width="40" height="40" aria-hidden="true"><g stroke="${C}" stroke-width="3.2" stroke-linecap="round">`
    + [0, 45, 90, 135, 180, 225, 270, 315].map(a => { const r = a * Math.PI / 180, c = Math.cos(r), n = Math.sin(r);
        return `<line x1="${(24 + 14.5 * c).toFixed(1)}" y1="${(24 + 14.5 * n).toFixed(1)}" x2="${(24 + 19.5 * c).toFixed(1)}" y2="${(24 + 19.5 * n).toFixed(1)}"/>`; }).join("")
    + `</g><circle cx="24" cy="24" r="8.5" fill="#FFFFFF" stroke="${G}" stroke-width="3.2"/></svg>`;
  const LUNA = `<svg viewBox="3 3 42 42" width="40" height="40" aria-hidden="true">`
    + `<path d="M30.5,8.5 A16,16 0 1 0 39.5,33 A13,13 0 0 1 30.5,8.5 Z" fill="#FFFFFF" stroke="${G}" stroke-width="3.2" stroke-linejoin="round"/>`
    + `<g stroke="${C}" stroke-width="2.6" stroke-linecap="round"><line x1="37" y1="12" x2="37" y2="20"/><line x1="33" y1="16" x2="41" y2="16"/></g>`
    + `<circle cx="42" cy="25" r="1.8" fill="${C}"/></svg>`;

  // iconos pequeños con el mismo estilo (vistas del calendario, candado, lupa). Uso: ICO.cal() → <svg class="ico">
  const ico = cuerpo => `<svg class="ico" viewBox="0 0 48 48" aria-hidden="true">${cuerpo}</svg>`;
  const hoja = (y = 12) => `<rect x="9" y="${y}" width="30" height="${40 - y}" rx="5" fill="#FFFFFF" stroke="${G}" stroke-width="3.4"/>`;
  const anillas = `<path d="M17,13 V8 a2.6,2.6 0 1 1 5.2,0 V10 M27,13 V8 a2.6,2.6 0 1 1 5.2,0 V10" fill="none" stroke="${G}" stroke-width="3" stroke-linecap="round"/>`;
  const l = (x1, y, x2, c = C, w = 3) => `<line x1="${x1}" y1="${y}" x2="${x2}" y2="${y}" stroke="${c}" stroke-width="${w}" stroke-linecap="round"/>`;
  const AC = "#29B6F6";
  window.ICO = {
    // 9-nov (D): tarta para la pestaña «Cumpleaños» del formulario
    cumple: () => ico(`<rect x="10" y="25" width="28" height="15" rx="4" fill="#FFFFFF" stroke="${G}" stroke-width="3.4"/>`
      + `<path d="M11.5,31.5 q4.2,3.6 8.4,0 t8.4,0 t8.4,0" fill="none" stroke="${C}" stroke-width="2.6" stroke-linecap="round"/>`
      + `<line x1="24" y1="25" x2="24" y2="17" stroke="${G}" stroke-width="3.4" stroke-linecap="round"/>`
      + `<path d="M24,6.5 c3.2,3.6 3.2,7 0,8 c-3.2,-1 -3.2,-4.4 0,-8 Z" fill="${AC}"/>`),
    cal: () => ico(hoja(14) + l(15, 21, 33) + `<g stroke="${C}" stroke-width="2.6" stroke-linecap="round"><line x1="19.5" y1="26" x2="19.5" y2="38"/><line x1="28.5" y1="26" x2="28.5" y2="38"/></g>`
      + l(14.5, 29, 33.5, C, 2.6) + l(14.5, 35, 33.5, C, 2.6) + `<rect x="21.5" y="30.5" width="5" height="4" rx="1" fill="${AC}"/>` + anillas),
    semana: () => ico(hoja(14) + l(15, 21, 33) + `<rect x="13.5" y="26" width="21" height="6" rx="2" fill="${AC}"/>` + l(15, 37, 33, C, 2.6) + anillas),
    mes: () => ico(hoja(14) + l(15, 21, 33) + [26.5, 31.5, 36.5].map(y => `<rect x="14" y="${y - 1.6}" width="3.2" height="3.2" rx=".8" fill="${AC}"/>` + l(21, y, 33, C, 2.6)).join("") + anillas),
    lupa: () => ico(`<line x1="29" y1="29" x2="39" y2="39" stroke="${G}" stroke-width="5" stroke-linecap="round"/><circle cx="21" cy="21" r="11" fill="#FFFFFF" stroke="${G}" stroke-width="3.4"/>`
      + `<path d="M15.5,19 a6,6 0 0 1 5,-4.5" fill="none" stroke="${C}" stroke-width="2.8" stroke-linecap="round"/>`),
    candado: (abierto) => ico(`<path d="${abierto ? "M16,22 V15 a8,8 0 0 1 15.5,-2.5" : "M16,22 V15 a8,8 0 0 1 16,0 V22"}" fill="none" stroke="${G}" stroke-width="3.4" stroke-linecap="round"/>`
      + `<rect x="10" y="21" width="28" height="20" rx="5" fill="#FFFFFF" stroke="${G}" stroke-width="3.4"/>`
      + `<circle cx="24" cy="29.5" r="2.6" fill="${abierto ? C : AC}"/><line x1="24" y1="31" x2="24" y2="35" stroke="${abierto ? C : AC}" stroke-width="2.6" stroke-linecap="round"/>`),
    // editor de notas
    volver: () => ico(`<path d="M27,12 L15,24 L27,36" fill="none" stroke="${G}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>`
      + l(17, 24, 36, C, 3.4)),
    editar: () => ico(`<g transform="rotate(45 24 24)"><rect x="19" y="6" width="10" height="27" rx="2" fill="${AC}" stroke="${G}" stroke-width="3"/>`
      + `<path d="M19,33 L24,43 L29,33 Z" fill="#FFFFFF" stroke="${G}" stroke-width="3" stroke-linejoin="round"/>`
      + `<line x1="19" y1="12" x2="29" y2="12" stroke="${G}" stroke-width="3"/></g>`),
    ver: () => ico(`<path d="M5,24 Q24,6 43,24 Q24,42 5,24 Z" fill="#FFFFFF" stroke="${G}" stroke-width="3.4" stroke-linejoin="round"/>`
      + `<circle cx="24" cy="24" r="6.5" fill="${C}"/><circle cx="24" cy="24" r="2.8" fill="${AC}"/>`),
    casilla: () => ico(`<rect x="9" y="9" width="30" height="30" rx="7" fill="#FFFFFF" stroke="${G}" stroke-width="3.4"/>`
      + `<path d="M16,24.5 l5.5,5.5 l11,-12" fill="none" stroke="${AC}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>`),
    fijar: () => ico(`<line x1="24" y1="30" x2="24" y2="43" stroke="${G}" stroke-width="3.4" stroke-linecap="round"/>`
      + `<path d="M17,7 H31 L29,17 L35,26 H13 L19,17 Z" fill="${C}" stroke="${G}" stroke-width="3.2" stroke-linejoin="round"/>`),
    borrar: () => ico(`<path d="M12,14 L14.5,40 a3,3 0 0 0 3,2.7 H30.5 a3,3 0 0 0 3,-2.7 L36,14" fill="#FFFFFF" stroke="${G}" stroke-width="3.4" stroke-linejoin="round"/>`
      + l(8, 13, 40, G, 3.4) + `<path d="M19,13 V8 H29 V13" fill="none" stroke="${G}" stroke-width="3.2" stroke-linejoin="round"/>`
      + `<g stroke="${C}" stroke-width="2.8" stroke-linecap="round"><line x1="20" y1="20" x2="20.8" y2="35"/><line x1="28" y1="20" x2="27.2" y2="35"/></g>`),
    // IMPORTANTE (texto en MAYÚSCULAS), en lugar de ❗
    imp: () => `<svg class="ico ico-imp" viewBox="0 0 48 48" aria-hidden="true"><circle cx="24" cy="24" r="19" fill="#E01B24"/>`
      + `<line x1="24" y1="13" x2="24" y2="27" stroke="#FFFFFF" stroke-width="5.5" stroke-linecap="round"/><circle cx="24" cy="35" r="3.3" fill="#FFFFFF"/></svg>`,
  };
  // la lupa dentro de los buscadores (fondo del campo)
  const LUPA_URL = `url("data:image/svg+xml,${encodeURIComponent(window.ICO.lupa().replace('class="ico"', 'xmlns="http://www.w3.org/2000/svg"'))}")`;

  function aplicar() {
    const o = oscuro();
    html.dataset.tema = o ? "oscuro" : "claro";
    const fondo = o ? "#1e1e22" : "#f6f5f4";
    let m = document.querySelector('meta[name="theme-color"]');
    if (!m) { m = document.createElement("meta"); m.name = "theme-color"; document.head.append(m); }
    m.content = fondo;
    try { window.Recordatorios && window.Recordatorios.tema(o, fondo); } catch (e) {}
    const b = document.getElementById("mtema");
    if (b) { b.querySelector("span").textContent = o ? "Tema claro" : "Tema oscuro"; b.querySelector("i").innerHTML = o ? SOL : LUNA; }
  }
  aplicar();
  sistema.addEventListener("change", aplicar);
  window.addEventListener("storage", ev => { if (ev.key === "tema") aplicar(); });   // cambiado en la otra página
  window.temaMenu = {aplicar};

  const CSS = `
#mfondo { position: fixed; inset: 0; z-index: 50; display: flex; align-items: center; justify-content: center;
  padding: 16px; opacity: 0; transition: opacity .18s ease;
  background: radial-gradient(circle at 50% 50%, rgba(0,0,0,.35) 0%, rgba(0,0,0,.62) 55%, rgba(0,0,0,.82) 100%);
  -webkit-backdrop-filter: blur(3px); backdrop-filter: blur(3px); }
#mfondo[hidden] { display: none; }
#mfondo.si { opacity: 1; }
#mcaja { background: var(--tarjeta); color: var(--texto); border: 2px solid var(--borde); border-radius: 22px;
  padding: 18px 14px 14px; width: min(100%, 360px); box-shadow: 0 18px 50px rgba(0,0,0,.45);
  transform: scale(.92); transition: transform .18s ease; }
#mfondo.si #mcaja { transform: scale(1); }
#mcaja .mlogo { display: block; width: 46px; height: 52px; margin: 0 auto 12px; }
#mcaja .mbotones { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
#mcaja .mbotones button { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
  min-height: 96px; padding: 10px 4px; border-radius: 16px; background: var(--fondo); border: 2px solid transparent;
  color: var(--texto); font: inherit; font-size: .88rem; cursor: pointer; }
#mcaja .mbotones button b { font-size: 2rem; line-height: 1; font-weight: 400; }
#mcaja .mbotones button img { width: 40px; height: 44px; }
#mcaja .mbotones button i { display: flex; align-items: center; justify-content: center; width: 40px; height: 44px; font-style: normal; }
#mcaja .mbotones button.aqui { border-color: var(--acento); color: var(--acento); font-weight: 700; }
#mcaja .mbotones button:active { transform: scale(.96); }
/* copia de seguridad: fila aparte con dos botones iguales a lo ancho del menú (icono y texto en línea) */
#mcaja .mbotones.mfila { grid-template-columns: 1fr 1fr; margin-top: 10px; }
#mcaja .mbotones.mfila[hidden] { display: none; }
#mcaja .mfila button { flex-direction: row; gap: 8px; min-height: 60px; padding: 8px 10px; }
#mcaja .mfila button i, #mcaja .mfila button svg { width: 30px; height: 30px; }
/* versión de la app: línea pequeña y gris al final, separada por un filete; no se pulsa */
#mcaja .mver { margin: 14px 0 0; padding-top: 10px; border-top: 1px solid var(--linea, var(--borde)); text-align: center;
  font-size: .78rem; color: var(--suave); user-select: none; -webkit-user-select: none; }
#mcaja .mver[hidden] { display: none; }
img.logo { cursor: pointer; }
.ico { width: 1.3em; height: 1.3em; flex: none; vertical-align: -.3em; }
.ico.ico-imp { width: 1.05em; height: 1.05em; vertical-align: -.15em; margin-right: 3px; }
button:has(> .ico) { display: inline-flex; align-items: center; justify-content: center; gap: 6px; }
input#bq, input#buscar { background-image: ${LUPA_URL}; background-repeat: no-repeat; background-position: 11px center;
  background-size: 22px 22px; padding-left: 42px; }
/* confirmar borrar: mismo fondo degradado y misma tarjeta que el menú */
#bfondo { position: fixed; inset: 0; z-index: 60; display: flex; align-items: center; justify-content: center;
  padding: 16px; opacity: 0; transition: opacity .18s ease;
  background: radial-gradient(circle at 50% 50%, rgba(0,0,0,.35) 0%, rgba(0,0,0,.62) 55%, rgba(0,0,0,.82) 100%);
  -webkit-backdrop-filter: blur(3px); backdrop-filter: blur(3px); }
#bfondo[hidden] { display: none; }
#bfondo.si { opacity: 1; }
#bcaja { background: var(--tarjeta); color: var(--texto); border: 2px solid var(--borde); border-radius: 22px;
  padding: 20px 16px 16px; width: min(100%, 340px); box-shadow: 0 18px 50px rgba(0,0,0,.45); text-align: center;
  transform: scale(.92); transition: transform .18s ease; }
#bfondo.si #bcaja { transform: scale(1); }
#bcaja .ico { width: 48px; height: 48px; display: block; margin: 0 auto 8px; vertical-align: 0; }
#bcaja h2 { margin: 0 0 8px; padding-bottom: 4px; font-size: 1.15rem; font-weight: 800; border-bottom: 1px solid var(--borde); }   /* 9-nov (D): línea fina debajo; centrado porque la ventana va centrada */
#bcaja p { margin: 0 0 4px; color: var(--suave); overflow-wrap: anywhere; }
#bcaja p.bque { color: var(--texto); font-weight: 600; }
#bcaja .bbotones { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 16px; }
#bcaja .bbotones button { min-height: 48px; border-radius: 14px; font: inherit; font-weight: 600; cursor: pointer;
  background: var(--fondo); color: var(--texto); border: 2px solid transparent; }
#bcaja .bbotones #bsi { background: #c01c28; color: #ffffff; border-color: #a51d2d; }
#bcaja .bbotones.serie { grid-template-columns: 1fr; }
#bcaja .bbotones.serie #bno { order: 3; }
#bcaja .bbotones #bsi2 { background: #c01c28; color: #ffffff; border-color: #a51d2d; }
#bcaja .bbotones #bsi2[hidden] { display: none; }
#bcaja .bbotones button:active { transform: scale(.96); }
@media (prefers-reduced-motion: reduce) { #mfondo, #mcaja, #bfondo, #bcaja { transition: none; } }`;

  // copia de seguridad (solo en la app): bandeja con flecha hacia fuera (exportar) o hacia dentro (importar)
  const CAJA = fuera => `<svg viewBox="0 0 48 48" width="40" height="40" aria-hidden="true">`
    + `<path d="M8,27 V37 a4,4 0 0 0 4,4 H36 a4,4 0 0 0 4,-4 V27" fill="none" stroke="${G}" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"/>`
    + `<g fill="none" stroke="${C}" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round">`
    + (fuera ? `<line x1="24" y1="32" x2="24" y2="8"/><polyline points="15,16 24,7 33,16"/>`
             : `<line x1="24" y1="7" x2="24" y2="31"/><polyline points="15,23 24,32 33,23"/>`) + `</g></svg>`;

  const hayCal = true;
  const pagina = location.pathname.startsWith("/notas") ? "notas" : "cal";

  const R = pagina === "notas" ? "/notas" : "/cal";   // los iconos (logo.svg, logo-notas.svg) se sirven en las dos rutas
  function montar() {
    const st = document.createElement("style"); st.textContent = CSS; document.head.append(st);
    const f = document.createElement("div"); f.id = "mfondo"; f.hidden = true;
    f.innerHTML = `<div id="mcaja" role="dialog" aria-label="Menú">
      <img class="mlogo" src="${R}/${pagina === "notas" ? "logo-notas" : "logo"}.svg" alt="">
      <div class="mbotones">
        <button id="mcal" data-ir="/cal"><img src="${R}/logo.svg" alt=""><span>Calendario</span></button>
        <button id="mnotas" data-ir="/notas"><img src="${R}/logo-notas.svg" alt=""><span>Notas</span></button>
        <button id="mtema"><i></i><span></span></button>
        <!-- aquí irá «Donar» -->
      </div>
      <div class="mbotones mfila" id="mcopia" hidden>
        <button id="mexp"><i>${CAJA(true)}</i><span>Exportar</span></button>
        <button id="mimp"><i>${CAJA(false)}</i><span>Importar</span></button>
      </div>
      <p class="mver" hidden></p></div>`;
    document.body.append(f);
    if (!hayCal) f.querySelector("#mcal").remove();
    f.querySelector(pagina === "notas" ? "#mnotas" : "#mcal")?.classList.add("aqui");
    aplicar();

    let irA = null;
    const abierto = () => !f.hidden;
    function abrir() {
      if (abierto()) return;
      f.hidden = false; requestAnimationFrame(() => f.classList.add("si"));
      history.pushState(Object.assign({}, history.state, {menu: 1}), "");   // el gesto de atrás lo cierra
    }
    function cerrar() {
      if (!abierto()) return;
      f.classList.remove("si"); f.hidden = true;
      if (history.state && history.state.menu) history.back(); else if (irA) { const u = irA; irA = null; location.href = u; }
    }
    window.addEventListener("popstate", () => {
      if (abierto()) { f.classList.remove("si"); f.hidden = true; }
      if (irA) { const u = irA; irA = null; location.href = u; }
    });
    if (history.state && history.state.menu) history.replaceState(Object.assign({}, history.state, {menu: 0}), "");

    f.addEventListener("click", ev => { if (ev.target === f) cerrar(); });
    document.addEventListener("keydown", ev => { if (ev.key === "Escape" && abierto()) { ev.stopImmediatePropagation(); cerrar(); } }, true);
    for (const b of f.querySelectorAll("[data-ir]")) b.onclick = () => {
      if (b.dataset.ir.startsWith("/" + pagina)) return cerrar();   // ya estás aquí
      irA = b.dataset.ir; cerrar();
    };
    const app = window.Recordatorios;
    if (app && app.exportar && app.importar) {
      const e = f.querySelector("#mexp"), i = f.querySelector("#mimp");
      f.querySelector("#mcopia").hidden = false;
      e.onclick = () => { cerrar(); app.exportar(); };
      i.onclick = () => { cerrar(); app.importar(); };
    }
    // versión de la app (la del build: cambia sola al compilar). Fuera de la app no se pone.
    try {
      const v = app && app.version && app.version();
      if (v) { const p = f.querySelector(".mver"); p.textContent = "Recordatorios " + v; p.hidden = false; }
    } catch (e) {}
    f.querySelector("#mtema").onclick = () => {
      try { localStorage.setItem("tema", oscuro() ? "claro" : "oscuro"); } catch (e) {}
      aplicar();
    };
    for (const l of document.querySelectorAll("img.logo")) {
      l.setAttribute("role", "button"); l.setAttribute("aria-label", "Menú"); l.tabIndex = 0;
      l.addEventListener("click", ev => { ev.stopPropagation(); abrir(); });
      l.addEventListener("keydown", ev => { if (ev.key === "Enter" || ev.key === " ") { ev.preventDefault(); abrir(); } });
    }
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", montar); else montar();

  /* CONFIRMAR BORRAR (calendario y notas): ventana en el centro, como el menú.
     Uso: if (await confirmarBorrar("Fisio", "Esta cita")) { …borrar… }
     Cancelar, tocar fuera, Escape o el gesto de atrás → false; «Eliminar» (en rojo) → true.
     El gesto de atrás solo cierra esta ventana: el aviso no les llega a las páginas
     (este oyente se registra antes que los suyos y corta el evento). */
  let bResolver = null, bResultado = false, bTragar = false;
  function bAcabar() {
    const f = document.getElementById("bfondo");
    f.classList.remove("si"); f.hidden = true;
    const r = bResolver; bResolver = null;
    if (r) r(bResultado);
  }
  function bCerrar(si) {
    if (!bResolver || bTragar) return;
    bResultado = si;
    // quitar el paso del historial que puso la ventana y responder cuando ya se haya quitado,
    // para que la página no se líe con su propio historial (notas abiertas, formularios)
    if (history.state && history.state.borrar) {
      bTragar = true; history.back();
      setTimeout(() => { if (bTragar) { bTragar = false; bAcabar(); } }, 500);   // por si no llega el aviso
    } else bAcabar();
  }
  window.addEventListener("popstate", ev => {
    if (bTragar) { bTragar = false; ev.stopImmediatePropagation(); bAcabar(); return; }
    if (bResolver) { ev.stopImmediatePropagation(); bResultado = false; bAcabar(); }   // atrás = cancelar
  });
  document.addEventListener("keydown", ev => {
    if (ev.key === "Escape" && bResolver) { ev.stopImmediatePropagation(); ev.preventDefault(); bCerrar(false); }
  }, true);

  const diaLargo = d => { try { return new Date(d + "T12:00:00").toLocaleDateString("es", {weekday: "long", day: "numeric", month: "long"}); } catch (e) { return d; } };
  function bCaja() {
    let f = document.getElementById("bfondo");
    if (!f) {
      f = document.createElement("div"); f.id = "bfondo"; f.hidden = true;
      f.innerHTML = `<div id="bcaja" role="alertdialog" aria-modal="true" aria-labelledby="btit">
        ${window.ICO.borrar()}
        <h2 id="btit"></h2>
        <p class="bque"></p><p class="bque2"></p>
        <div class="bbotones"><button id="bno" type="button">Cancelar</button><button id="bsi" type="button">Eliminar</button><button id="bsi2" type="button" hidden></button></div></div>`;
      document.body.append(f);
      f.addEventListener("click", ev => { if (ev.target === f) bCerrar(false); });
      f.querySelector("#bno").onclick = () => bCerrar(false);
    }
    return f;
  }
  function bAbrir(f) {
    return new Promise(res => {
      bResolver = res; bResultado = false;
      f.hidden = false; requestAnimationFrame(() => f.classList.add("si"));
      history.pushState(Object.assign({}, history.state, {borrar: 1}), "");
      f.querySelector("#bno").focus();
    });
  }
  window.confirmarBorrar = function (nombre, que) {
    if (bResolver) return Promise.resolve(false);
    const f = bCaja();
    f.querySelector("#btit").textContent = "¿Seguro que quieres eliminarlo?";
    f.querySelector(".bque2").textContent = "No se puede deshacer.";
    f.querySelector(".bbotones").classList.remove("serie");
    const si = f.querySelector("#bsi"); si.textContent = "Eliminar"; si.onclick = () => bCerrar(true);
    f.querySelector("#bsi2").hidden = true;
    const n = (nombre || "").trim();
    f.querySelector(".bque").textContent = n ? `${que || "Se eliminará"}: «${n.length > 80 ? n.slice(0, 80) + "…" : n}»` : (que || "");
    f.querySelector(".bque").hidden = !n && !que;
    return bAbrir(f);
  };
  // Algo que se repite: «esta» (solo esa vez) | «siguientes» (esa y las que vienen después) | false (cancelar)
  window.confirmarBorrarVez = function (nombre, dia) {
    if (bResolver) return Promise.resolve(false);
    const f = bCaja();
    f.querySelector("#btit").textContent = "¿Qué quieres borrar?";
    const n = (nombre || "").trim();
    f.querySelector(".bque").textContent = n ? `«${n.length > 80 ? n.slice(0, 80) + "…" : n}»` : "";
    f.querySelector(".bque").hidden = !n;
    f.querySelector(".bque2").textContent = "Se repite. Esta vez es el " + diaLargo(dia) + ".";
    f.querySelector(".bbotones").classList.add("serie");
    const a = f.querySelector("#bsi"); a.textContent = "Solo esta"; a.onclick = () => bCerrar("esta");
    const b = f.querySelector("#bsi2"); b.hidden = false; b.textContent = "Esta y las siguientes"; b.onclick = () => bCerrar("siguientes");
    return bAbrir(f);
  };
})();

