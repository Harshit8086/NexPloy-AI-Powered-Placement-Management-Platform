// Procedural "digital twin" bust, built silhouette-first:
//   1. an authored human bust outline (three-quarter view, facing right),
//      smoothed with a cardinal spline into a dense polygon
//   2. points placed INSIDE that polygon with region-dependent density
//      (face > head > neck > body), plus points along the outline itself
//   3. Delaunay triangulation, clipped to the outline
// Pure geometry (no React / DOM); computed once at module load.

export const TWIN_W = 300;
export const TWIN_H = 380;

type Pt = [number, number];

function rng(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

// Key points, clockwise from the crown. Subject faces right: forehead, brow,
// nose, lips, chin, then throat → neck → sloping shoulder → bust. The left
// half runs back up the far shoulder, neck, nape and back of the skull.
const KEY: Pt[] = [
  [148, 30], // crown
  [170, 36],
  [184, 50], // forehead
  [190, 68],
  [191, 80], // brow
  [193, 88], // nose bridge
  [203, 99], // nose tip
  [196, 104],
  [196, 110], // lips
  [193, 117],
  [191, 126], // chin
  [185, 136], // squarer jaw than a softer/rounder chin would read
  [174, 140], // under-jaw
  [168, 144], // throat — thicker than a slender neck
  [173, 157], // neck front
  [181, 169],
  [196, 179], // neck → shoulder: broader, more square-set shoulders
  [214, 187],
  [232, 194], // shoulder plateau — flatter top, less sloped
  [248, 200],
  [260, 210],
  [266, 226],
  [268, 248],
  [268, 285],
  [268, 325],
  [268, 374], // bust, right — wider frame overall
  [32, 374], // bust, left
  [32, 325],
  [32, 285],
  [32, 248],
  [34, 226],
  [40, 210], // far shoulder — mirrors the right side
  [52, 200],
  [68, 194],
  [86, 187],
  [104, 179],
  [119, 169],
  [127, 157], // neck back
  [132, 144],
  [127, 138], // nape
  [117, 126],
  [108, 110],
  [104, 90],
  [106, 68],
  [115, 48],
  [130, 35],
];

function smooth(pts: Pt[], perSeg = 8, tension = 0.42): Pt[] {
  const n = pts.length;
  const out: Pt[] = [];
  for (let i = 0; i < n; i++) {
    const p0 = pts[(i - 1 + n) % n];
    const p1 = pts[i];
    const p2 = pts[(i + 1) % n];
    const p3 = pts[(i + 2) % n];
    const m1: Pt = [(p2[0] - p0[0]) * tension, (p2[1] - p0[1]) * tension];
    const m2: Pt = [(p3[0] - p1[0]) * tension, (p3[1] - p1[1]) * tension];
    for (let k = 0; k < perSeg; k++) {
      const t = k / perSeg;
      const t2 = t * t;
      const t3 = t2 * t;
      const h00 = 2 * t3 - 3 * t2 + 1;
      const h10 = t3 - 2 * t2 + t;
      const h01 = -2 * t3 + 3 * t2;
      const h11 = t3 - t2;
      out.push([
        h00 * p1[0] + h10 * m1[0] + h01 * p2[0] + h11 * m2[0],
        h00 * p1[1] + h10 * m1[1] + h01 * p2[1] + h11 * m2[1],
      ]);
    }
  }
  return out;
}

const OUTLINE = smooth(KEY);

function inPoly(x: number, y: number, poly: Pt[] = OUTLINE): boolean {
  let c = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i];
    const [xj, yj] = poly[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
  }
  return c;
}

/** Target point spacing: finest on the face, coarser down the body. */
function spacing(x: number, y: number): number {
  if (y < 142) return x > 150 && y > 62 ? 6.2 : 7.8; // face / skull
  if (y < 182) return 9; // neck
  return 12.5 + Math.min(8, (y - 182) * 0.04); // shoulders / bust
}

function delaunay(pts: Pt[]): [number, number, number][] {
  const n = pts.length;
  const M = 4000;
  const all: Pt[] = [...pts, [-M, -M], [M, -M], [0, M]];
  type T = { a: number; b: number; c: number; cx: number; cy: number; r2: number };
  const mk = (a: number, b: number, c: number): T => {
    const [ax, ay] = all[a];
    const [bx, by] = all[b];
    const [cx, cy] = all[c];
    const d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by)) || 1e-9;
    const ux = ((ax * ax + ay * ay) * (by - cy) + (bx * bx + by * by) * (cy - ay) + (cx * cx + cy * cy) * (ay - by)) / d;
    const uy = ((ax * ax + ay * ay) * (cx - bx) + (bx * bx + by * by) * (ax - cx) + (cx * cx + cy * cy) * (bx - ax)) / d;
    return { a, b, c, cx: ux, cy: uy, r2: (ax - ux) ** 2 + (ay - uy) ** 2 };
  };
  let tris: T[] = [mk(n, n + 1, n + 2)];
  for (let i = 0; i < n; i++) {
    const [px, py] = all[i];
    const bad: T[] = [];
    const keep: T[] = [];
    for (const t of tris) ((px - t.cx) ** 2 + (py - t.cy) ** 2 < t.r2 ? bad : keep).push(t);
    const edges = new Map<string, [number, number, number]>();
    for (const t of bad) {
      for (const [u, v] of [[t.a, t.b], [t.b, t.c], [t.c, t.a]] as [number, number][]) {
        const k = u < v ? `${u}-${v}` : `${v}-${u}`;
        const e = edges.get(k);
        edges.set(k, [u, v, (e ? e[2] : 0) + 1]);
      }
    }
    tris = keep;
    edges.forEach(([u, v, c]) => {
      if (c === 1) tris.push(mk(u, v, i));
    });
  }
  return tris.filter((t) => t.a < n && t.b < n && t.c < n).map((t) => [t.a, t.b, t.c]);
}

export type TwinMesh = {
  outline: string;
  edges: string;
  rimEdges: string;
  vertices: string;
  brightVertices: string;
  cloud: { x: number; y: number; r: number; d: number }[];
  halo: { x: number; y: number; d: number }[];
};

function build(): TwinMesh {
  const rand = rng(20270);

  // 1. points along the outline (skip nothing — bottom edge is faded out later)
  const pts: Pt[] = [];
  let acc = 0;
  for (let i = 0; i < OUTLINE.length; i++) {
    const a = OUTLINE[i];
    const b = OUTLINE[(i + 1) % OUTLINE.length];
    acc += Math.hypot(b[0] - a[0], b[1] - a[1]);
    if (acc >= spacing(a[0], a[1]) * 0.95) {
      pts.push(a);
      acc = 0;
    }
  }

  // 2. interior points, blue-noise by dart throwing with variable radius
  for (let i = 0; i < 9000; i++) {
    const x = 40 + rand() * 220;
    const y = 26 + rand() * 350;
    if (!inPoly(x, y)) continue;
    const r = spacing(x, y) * 0.92;
    let ok = true;
    for (const p of pts) {
      if ((p[0] - x) ** 2 + (p[1] - y) ** 2 < r * r) {
        ok = false;
        break;
      }
    }
    if (ok) pts.push([x, y]);
  }

  // 3. triangulate, keep only triangles fully inside the silhouette
  const tris = delaunay(pts).filter(([a, b, c]) => {
    const A = pts[a];
    const B = pts[b];
    const C = pts[c];
    const probes: Pt[] = [
      [(A[0] + B[0] + C[0]) / 3, (A[1] + B[1] + C[1]) / 3],
      [(A[0] + B[0]) / 2, (A[1] + B[1]) / 2],
      [(B[0] + C[0]) / 2, (B[1] + C[1]) / 2],
      [(C[0] + A[0]) / 2, (C[1] + A[1]) / 2],
    ];
    return probes.every(([x, y]) => inPoly(x, y));
  });

  const count = new Map<string, number>();
  const used = new Set<number>();
  for (const [a, b, c] of tris) {
    for (const [u, v] of [[a, b], [b, c], [c, a]] as [number, number][]) {
      const k = u < v ? `${u}-${v}` : `${v}-${u}`;
      count.set(k, (count.get(k) ?? 0) + 1);
    }
    used.add(a);
    used.add(b);
    used.add(c);
  }
  const f = (n: number) => n.toFixed(1);
  let edges = "";
  let rimEdges = "";
  count.forEach((n, k) => {
    const [u, v] = k.split("-").map(Number);
    const seg = `M${f(pts[u][0])} ${f(pts[u][1])}L${f(pts[v][0])} ${f(pts[v][1])}`;
    if (n === 1) rimEdges += seg;
    else edges += seg;
  });

  let vertices = "";
  let brightVertices = "";
  used.forEach((i) => {
    const seg = `M${f(pts[i][0])} ${f(pts[i][1])}h0`;
    // brighter accents are more frequent on the head, rare on the body
    const p = pts[i][1] < 145 ? 0.2 : pts[i][1] < 230 ? 0.1 : 0.04;
    if (rand() < p) brightVertices += seg;
    else vertices += seg;
  });

  const outline = "M" + OUTLINE.map((p) => `${f(p[0])} ${f(p[1])}`).join("L") + "Z";

  // point cloud inside: concentrated on head / shoulders
  const cloud: TwinMesh["cloud"] = [];
  for (let i = 0; i < 400 && cloud.length < 55; i++) {
    const x = 40 + rand() * 220;
    const y = 26 + rand() * 300;
    if (inPoly(x, y) && rand() < 1 - y / 340) cloud.push({ x: +x.toFixed(1), y: +y.toFixed(1), r: +(0.6 + rand() * 0.6).toFixed(2), d: +(rand() * 4).toFixed(2) });
  }
  // halo particles just outside the silhouette, denser near the head
  const halo: TwinMesh["halo"] = [];
  for (let i = 0; i < 2000 && halo.length < 16; i++) {
    const p = OUTLINE[Math.floor(rand() * OUTLINE.length)];
    if (p[1] > 280 || p[1] > 60 + rand() * 220) continue;
    const ang = rand() * Math.PI * 2;
    const dist = 9 + rand() * 16;
    const x = p[0] + Math.cos(ang) * dist;
    const y = p[1] + Math.sin(ang) * dist;
    if (!inPoly(x, y)) halo.push({ x: +x.toFixed(1), y: +y.toFixed(1), d: +(rand() * 5).toFixed(2) });
  }

  return { outline, edges, rimEdges, vertices, brightVertices, cloud, halo };
}

export const twinMesh: TwinMesh = build();
