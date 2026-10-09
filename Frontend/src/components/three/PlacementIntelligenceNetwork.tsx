"use client";

import { Suspense, useMemo, useRef } from "react";
import { Canvas, useFrame, useThree } from "@react-three/fiber";
import { Float, Html, Line, Sparkles } from "@react-three/drei";
import * as THREE from "three";

/**
 * Landing page hero visualization — the one place in the app that gets a
 * full R3F scene at high visual intensity. Represents the placement
 * journey as a connected node network: Student → Skills → Readiness →
 * Opportunity → Recruiter → Offer.
 *
 * Always mounted via next/dynamic with ssr:false and unmounted on route
 * change (it lives inside the landing page component only). Keep this
 * scene self-contained — do not import it from any other page.
 */

const NODES: { label: string; pos: [number, number, number] }[] = [
  { label: "Student", pos: [-4.4, 0.55, 0] },
  { label: "Skills", pos: [-2.7, -0.5, 0.7] },
  { label: "Readiness", pos: [-0.9, 0.55, -0.5] },
  { label: "Opportunity", pos: [0.9, -0.45, 0.6] },
  { label: "Recruiter", pos: [2.7, 0.4, -0.4] },
  { label: "Offer", pos: [4.4, -0.15, 0.25] },
];

function CameraRig() {
  const { camera, pointer } = useThree();
  useFrame(() => {
    camera.position.x += (pointer.x * 0.55 - camera.position.x) * 0.025;
    camera.position.y += (pointer.y * 0.3 - camera.position.y) * 0.025;
    camera.lookAt(0, 0, 0);
  });
  return null;
}

function IntelligenceNode({ pos, label, geometry, material }: {
  pos: [number, number, number];
  label: string;
  geometry: THREE.SphereGeometry;
  material: THREE.MeshStandardMaterial;
}) {
  return (
    <Float speed={1.1} rotationIntensity={0.12} floatIntensity={0.55} floatingRange={[-0.1, 0.1]}>
      <group position={pos}>
        <mesh geometry={geometry} material={material} />
        <Html center distanceFactor={9} occlude={false} style={{ pointerEvents: "none" }}>
          <div
            style={{
              fontSize: 11,
              fontWeight: 500,
              color: "#F8FAFC",
              whiteSpace: "nowrap",
              textAlign: "center",
              textShadow: "0 0 10px rgba(124,58,237,0.9), 0 1px 2px rgba(0,0,0,0.8)",
              fontFamily: "var(--font-inter), system-ui, sans-serif",
              transform: "translateY(20px)",
            }}
          >
            {label}
          </div>
        </Html>
      </group>
    </Float>
  );
}

function ConnectionPulse({
  from,
  to,
  delay,
  geometry,
}: {
  from: [number, number, number];
  to: [number, number, number];
  delay: number;
  geometry: THREE.SphereGeometry;
}) {
  const ref = useRef<THREE.Mesh>(null);
  const duration = 2.4;
  const start = useRef(new THREE.Vector3(...from));
  const end = useRef(new THREE.Vector3(...to));
  // Each pulse animates its own opacity independently, so it needs its own
  // material instance — sharing one across instances would make every
  // pulse flash in lockstep (last-writer-wins on the shared uniform).
  const material = useMemo(
    () => new THREE.MeshBasicMaterial({ color: "#C4A6FF", transparent: true, opacity: 0 }),
    [],
  );

  useFrame(({ clock }) => {
    if (!ref.current) return;
    const t = ((clock.elapsedTime + delay) % duration) / duration;
    ref.current.position.lerpVectors(start.current, end.current, t);
    const mat = ref.current.material as THREE.MeshBasicMaterial;
    mat.opacity = Math.sin(Math.min(1, t) * Math.PI) * 0.9;
  });

  return <mesh ref={ref} geometry={geometry} material={material} />;
}

function NetworkScene() {
  const nodeGeometry = useMemo(() => new THREE.SphereGeometry(0.15, 20, 20), []);
  const nodeMaterial = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: "#9D5CFF",
        emissive: "#7C3AED",
        emissiveIntensity: 1.3,
        roughness: 0.35,
        metalness: 0.15,
      }),
    [],
  );
  const pulseGeometry = useMemo(() => new THREE.SphereGeometry(0.05, 8, 8), []);

  return (
    <>
      <ambientLight intensity={0.55} />
      <pointLight position={[0, 2, 4]} intensity={45} color="#9D5CFF" />
      <pointLight position={[-3, -1, 2]} intensity={20} color="#9D5CFF" />
      <CameraRig />

      {NODES.slice(0, -1).map((n, i) => (
        <Line
          key={`line-${n.label}`}
          points={[n.pos, NODES[i + 1].pos]}
          color="#7C3AED"
          transparent
          opacity={0.28}
          lineWidth={1}
        />
      ))}

      {NODES.slice(0, -1).map((n, i) => (
        <ConnectionPulse
          key={`pulse-${n.label}`}
          from={n.pos}
          to={NODES[i + 1].pos}
          delay={i * 0.45}
          geometry={pulseGeometry}
        />
      ))}

      {NODES.map((n) => (
        <IntelligenceNode key={n.label} pos={n.pos} label={n.label} geometry={nodeGeometry} material={nodeMaterial} />
      ))}

      <Sparkles count={50} scale={[9.5, 3.2, 3]} size={1.8} speed={0.15} opacity={0.3} color="#9D5CFF" />
    </>
  );
}

export default function PlacementIntelligenceNetwork() {
  return (
    <Canvas
      dpr={[1, 1.5]}
      gl={{ antialias: true, alpha: true, powerPreference: "low-power" }}
      camera={{ position: [0, 0, 7], fov: 42 }}
      style={{ position: "absolute", inset: 0 }}
    >
      <Suspense fallback={null}>
        <NetworkScene />
      </Suspense>
    </Canvas>
  );
}
