/* Las páginas piden /cal/… y /notas/… como si hubiera un servidor; aquí las contesta la propia app
   (puente «Recordatorios» de MainActivity). Todo se queda en el móvil: no hay internet. */
(() => {
  const f0 = window.fetch.bind(window);
  const API = /^\/(cal\/(datos|buscar|hecho|anadir|editar|borrar)|notas\/(datos|guardar|borrar|candado))$/;
  window.fetch = async (u, o = {}) => {
    const url = new URL(typeof u === "string" ? u : u.url, location.href);
    if (!API.test(url.pathname) || !window.Recordatorios) return f0(u, o);
    const r = JSON.parse(Recordatorios.pedir(o.method || "GET", url.pathname + url.search,
                                             o.body == null ? "" : String(o.body)));
    return new Response(r.cuerpo, {status: r.estado, headers: {"Content-Type": r.tipo}});
  };
})();
