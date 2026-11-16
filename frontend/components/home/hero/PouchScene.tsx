"use client";

// The 3D half of the landing hero: one stand-up pouch (front + back pack art) that sways,
// can be dragged round or tapped to flip, and spins a full turn to change flavor (the art
// swaps while it is edge-on). The active flavor's ingredient cut-outs orbit it on two
// tilted rings, passing behind and in front. Loaded lazily by HeroFlavorCycler — never
// rendered on the server, under reduced motion, or on low-power devices.

import { Canvas, useFrame, useLoader, useThree, type ThreeEvent } from "@react-three/fiber";
import { Suspense, useEffect, useMemo, useRef, useState } from "react";
import * as THREE from "three";
import { HERO_FLAVORS, HERO_SPRITES, PACK_ASPECT } from "./flavors";

const POUCH_H = 3.2;
const POUCH_W = POUCH_H * PACK_ASPECT;
const POUCH_DEPTH = 0.27;
const SPIN_SECONDS = 1.5;
const TAU = Math.PI * 2;

interface Controls {
  rotY: number;
  rotX: number;
  /** Where rotY settles when nobody is dragging: a multiple of PI (front or back). */
  rest: number;
  dragging: boolean;
  /** Flavor whose art is on the mesh right now (lags `active` until mid-spin). */
  shown: number;
  /** Spin progress 0..1, or -1 when idle. */
  spin: number;
  orbit: number;
  orbitSpeed: number;
}

export interface PouchSceneProps {
  active: number;
  selected: string | null;
  onSelect: (ingredient: string | null) => void;
  onReady: () => void;
  onInteract: () => void;
}

const smooth = (t: number) => {
  const c = Math.min(1, Math.max(0, t));
  return c * c * (3 - 2 * c);
};
const easeInOut = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
const damp = (from: number, to: number, lambda: number, dt: number) => from + (to - from) * (1 - Math.exp(-lambda * dt));

/** One face of the pouch: a flat sheet pushed out into a pillow — sealed flat along the top
 *  and the sides, fullest about a third of the way up, like a filled stand-up pouch. */
function makeFaceGeometry() {
  const g = new THREE.PlaneGeometry(POUCH_W, POUCH_H, 28, 44);
  const pos = g.attributes.position;
  for (let i = 0; i < pos.count; i++) {
    const u = pos.getX(i) / (POUCH_W / 2);
    const v = pos.getY(i) / POUCH_H + 0.5;
    const side = Math.pow(Math.max(0, 1 - Math.pow(Math.abs(u), 2.4)), 0.75);
    const belly = v < 0.22 ? 0.3 + 0.7 * smooth(v / 0.22) : 1 - smooth((v - 0.22) / 0.66);
    pos.setZ(i, POUCH_DEPTH * side * Math.max(0, belly) + 0.003);
  }
  g.computeVertexNormals();
  return g;
}

function usePackTextures() {
  const urls = useMemo(() => HERO_FLAVORS.flatMap((f) => [f.front, f.back]), []);
  const textures = useLoader(THREE.TextureLoader, urls);
  return useMemo(() => {
    textures.forEach((t) => {
      t.colorSpace = THREE.SRGBColorSpace;
      t.anisotropy = 8;
    });
    return HERO_FLAVORS.map((_, i) => ({ front: textures[i * 2], back: textures[i * 2 + 1] }));
  }, [textures]);
}

function Pouch({ ctlRef, active }: { ctlRef: React.RefObject<Controls>; active: number }) {
  const packs = usePackTextures();
  const geometry = useMemo(() => makeFaceGeometry(), []);
  const group = useRef<THREE.Group>(null);
  const frontMat = useRef<THREE.MeshStandardMaterial>(null);
  const backMat = useRef<THREE.MeshStandardMaterial>(null);
  const { viewport } = useThree();
  // Narrow stages (phones): shrink the pouch so the orbit still fits beside it.
  const fit = Math.min(1, viewport.width / 3.3);

  useEffect(() => () => geometry.dispose(), [geometry]);

  useFrame((state, dt) => {
    const c = ctlRef.current;
    const g = group.current;
    if (!c || !g) return;
    const t = state.clock.elapsedTime;

    if (active !== c.shown && c.spin < 0) {
      c.spin = 0;
      c.rest = Math.round(c.rotY / TAU) * TAU; // always land on the front
    }
    let spinAngle = 0;
    if (c.spin >= 0) {
      c.spin = Math.min(1, c.spin + dt / SPIN_SECONDS);
      spinAngle = easeInOut(c.spin) * TAU;
      if (spinAngle >= Math.PI / 2 && c.shown !== active) {
        // Edge-on: nobody can see either face, so swap the art now.
        if (frontMat.current) frontMat.current.map = packs[active].front;
        if (backMat.current) backMat.current.map = packs[active].back;
        c.shown = active;
      }
      if (c.spin >= 1) {
        c.spin = -1;
        spinAngle = 0;
      }
    }

    if (!c.dragging) {
      c.rotY = damp(c.rotY, c.rest, 4.5, dt);
      c.rotX = damp(c.rotX, 0, 4.5, dt);
    }
    const sway = c.dragging ? 0 : 1;
    g.rotation.y = c.rotY + spinAngle + Math.sin(t * 0.6) * 0.2 * sway;
    g.rotation.x = c.rotX + Math.sin(t * 0.45) * 0.03 * sway;
    g.position.y = Math.sin(t * 0.9) * 0.05;
    g.scale.setScalar(damp(g.scale.x, fit, 4, dt));
  });

  const flip = (e: ThreeEvent<MouseEvent>) => {
    if (e.delta > 6) return; // that was a drag, not a tap
    e.stopPropagation();
    const c = ctlRef.current;
    if (c) c.rest = Math.round(c.rotY / Math.PI) * Math.PI + Math.PI;
  };

  return (
    <group ref={group} scale={0.86}>
      <mesh geometry={geometry} onClick={flip}>
        <meshStandardMaterial ref={frontMat} map={packs[0].front} alphaTest={0.5} roughness={0.58} metalness={0.04} />
      </mesh>
      <mesh geometry={geometry} rotation-y={Math.PI} onClick={flip}>
        <meshStandardMaterial ref={backMat} map={packs[0].back} alphaTest={0.5} roughness={0.58} metalness={0.04} />
      </mesh>
    </group>
  );
}

const RINGS = [
  { y: 0.62, tilt: 0.38, dir: 1 },
  { y: -0.78, tilt: -0.3, dir: 1 },
];

function Ingredients({
  ctlRef,
  selected,
  onSelect,
}: {
  ctlRef: React.RefObject<Controls>;
  selected: string | null;
  onSelect: (ingredient: string | null) => void;
}) {
  const textures = useLoader(
    THREE.TextureLoader,
    useMemo(() => HERO_SPRITES.map((s) => s.src), []),
  );
  const meshes = useRef<(THREE.Mesh | null)[]>([]);
  const { viewport } = useThree();

  // Where each cut-out sits on its ring: spread evenly per flavor, alternating rings.
  const layout = useMemo(() => {
    const perFlavorRing = new Map<string, number>();
    const slots = HERO_SPRITES.map((s, i) => {
      const inFlavor = HERO_SPRITES.slice(0, i).filter((o) => o.flavor === s.flavor).length;
      const ring = inFlavor % 2;
      const key = `${s.flavor}:${ring}`;
      const index = perFlavorRing.get(key) ?? 0;
      perFlavorRing.set(key, index + 1);
      return { ring, index, key };
    });
    return slots.map((slot, i) => ({
      ring: slot.ring,
      phase: (slot.index / (perFlavorRing.get(slot.key) ?? 1)) * TAU + slot.ring * 1.1,
      tilt: ((i * 37) % 60) / 100 - 0.3,
    }));
  }, []);

  useEffect(() => {
    textures.forEach((t) => {
      t.colorSpace = THREE.SRGBColorSpace;
      t.anisotropy = 4;
    });
  }, [textures]);

  const target = useMemo(() => new THREE.Vector3(), []);

  useFrame((state, dt) => {
    const c = ctlRef.current;
    if (!c) return;
    const t = state.clock.elapsedTime;
    c.orbitSpeed = damp(c.orbitSpeed, selected ? 0 : 1, 3, dt);
    c.orbit += dt * 0.3 * c.orbitSpeed;

    const radiusX = Math.min(2.05, Math.max(1.12, viewport.width / 2 - 0.4));
    const radiusZ = radiusX * 0.62;
    const sizeMul = Math.min(1, Math.max(0.66, radiusX / 1.9));

    HERO_SPRITES.forEach((s, i) => {
      const mesh = meshes.current[i];
      if (!mesh) return;
      const slot = layout[i];
      const ring = RINGS[slot.ring];
      const a = c.orbit * ring.dir + slot.phase;
      const isSelected = selected === s.ingredient;
      const visible = s.flavor === c.shown ? 1 : 0;

      if (isSelected && s.primary) {
        target.set(radiusX * 0.5, -0.35, 1.7);
      } else {
        target.set(
          Math.cos(a) * radiusX,
          ring.y + Math.sin(a + 0.8) * ring.tilt + Math.sin(t * 1.1 + slot.phase) * 0.05,
          Math.sin(a) * radiusZ,
        );
      }
      mesh.position.lerp(target, 1 - Math.exp(-5 * dt));

      const image = textures[i].image as { width: number; height: number } | undefined;
      const aspect = image ? image.width / image.height : 1;
      const base = 0.62 * sizeMul * (isSelected ? (s.primary ? 1.55 : 1.2) : selected ? 0.82 : 1) * visible;
      const sx = damp(mesh.scale.x, aspect >= 1 ? base : base * aspect, 6, dt);
      const sy = damp(mesh.scale.y, aspect >= 1 ? base / aspect : base, 6, dt);
      mesh.scale.set(sx, sy, 1);
      mesh.rotation.z = slot.tilt + Math.sin(t * 0.5 + slot.phase) * 0.14;
      const mat = mesh.material as THREE.MeshBasicMaterial;
      mat.opacity = damp(mat.opacity, selected && !isSelected ? 0.4 : 1, 6, dt);
    });
  });

  return (
    <>
      {HERO_SPRITES.map((s, i) => (
        <mesh
          key={`${s.flavor}-${s.src}`}
          ref={(m) => {
            meshes.current[i] = m;
          }}
          scale={0}
          onClick={(e) => {
            if (e.delta > 6) return;
            e.stopPropagation();
            onSelect(selected === s.ingredient ? null : s.ingredient);
          }}
          onPointerOver={() => {
            document.body.style.cursor = "pointer";
          }}
          onPointerOut={() => {
            document.body.style.cursor = "";
          }}
        >
          <planeGeometry args={[1, 1]} />
          <meshBasicMaterial map={textures[i]} transparent depthWrite={false} toneMapped={false} />
        </mesh>
      ))}
    </>
  );
}

function Ready({ onReady }: { onReady: () => void }) {
  // Mounts only once every texture in the Suspense boundary has loaded.
  useEffect(() => {
    onReady();
  }, [onReady]);
  return null;
}

export default function PouchScene({ active, selected, onSelect, onReady, onInteract }: PouchSceneProps) {
  const wrap = useRef<HTMLDivElement>(null);
  const ctl = useRef<Controls>({
    rotY: -1.5,
    rotX: 0,
    rest: 0,
    dragging: false,
    shown: active,
    spin: -1,
    orbit: 0,
    orbitSpeed: 1,
  });
  const [onScreen, setOnScreen] = useState(true);

  // Stop rendering while the hero is scrolled out of view.
  useEffect(() => {
    const el = wrap.current;
    if (!el) return;
    const io = new IntersectionObserver(([entry]) => setOnScreen(entry.isIntersecting), { threshold: 0.05 });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  useEffect(
    () => () => {
      document.body.style.cursor = "";
    },
    [],
  );

  // Drag to turn the pouch. Listeners live on window so a drag can leave the canvas; the
  // canvas keeps `touch-action: pan-y`, so a vertical swipe still scrolls the page.
  const onPointerDown = (e: React.PointerEvent) => {
    if (e.pointerType === "mouse" && e.button !== 0) return;
    const c = ctl.current;
    const start = { x: e.clientX, y: e.clientY, rotY: c.rotY, rotX: c.rotX };
    let moved = false;
    const move = (ev: PointerEvent) => {
      const dx = ev.clientX - start.x;
      const dy = ev.clientY - start.y;
      if (!moved && Math.abs(dx) < 5) return;
      if (!moved) {
        moved = true;
        c.dragging = true;
        onInteract();
      }
      c.rotY = start.rotY + dx * 0.011;
      c.rotX = Math.min(0.3, Math.max(-0.3, start.rotX + dy * 0.004));
    };
    const end = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", end);
      window.removeEventListener("pointercancel", end);
      if (moved) {
        c.dragging = false;
        c.rest = Math.round(c.rotY / Math.PI) * Math.PI;
      }
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", end);
    window.addEventListener("pointercancel", end);
  };

  return (
    <div ref={wrap} onPointerDown={onPointerDown} style={{ position: "absolute", inset: 0, cursor: "grab" }}>
      <Canvas
        flat
        dpr={[1, 2]}
        frameloop={onScreen ? "always" : "never"}
        camera={{ fov: 28, position: [0, 0.3, 8.9], near: 0.1, far: 40 }}
        gl={{ antialias: true, alpha: true, powerPreference: "high-performance" }}
        style={{ touchAction: "pan-y" }}
        onPointerMissed={() => onSelect(null)}
        aria-hidden="true"
      >
        <ambientLight intensity={1.55} />
        <directionalLight position={[-2.5, 3.2, 6]} intensity={1.75} />
        <directionalLight position={[3, 1, 4]} intensity={0.35} />
        <Suspense fallback={null}>
          <Pouch ctlRef={ctl} active={active} />
          <Ingredients ctlRef={ctl} selected={selected} onSelect={onSelect} />
          <Ready onReady={onReady} />
        </Suspense>
      </Canvas>
    </div>
  );
}
