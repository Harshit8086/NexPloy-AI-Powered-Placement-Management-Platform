"use client";

import { Suspense, useMemo, useRef } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import { Float, Html, Line } from "@react-three/drei";
import * as THREE from "three";

/**
 * Digital Twin page's dedicated visualization — the second (and only
 * other) full R3F scene in the app, per the "strongest 3D only on
 * important intelligence screens" rule. A central twin core with six
 * orbiting modules (Academics, Skills, Projects, Assessments, Interview,
 * Placement Activity) feeding signal into it via thin connection lines
 * and traveling pulses, plus a readiness-driven ring around the core.
 *
 * Modules are placed at fixed local positions inside one slowly rotating
 * group, rather than animating each module's own orbit position — this
 * keeps the connection lines attached without per-frame geometry
 * rebuilds, and reads as gentle "slow camera/parallax movement".
 */

export type TwinModule = { label: string; value: number };

const MODULE_RADIUS = 2.15;

function TwinCore({ readiness }: { readiness: number }) {
  const coreGeometry = useMemo(() => new THREE.SphereGeometry(0.55, 32, 32), []);
  const coreMaterial = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: "#9D5CFF",
        emissive: "#7C3AED",
        emissiveIntensity: 1.1,
        roughness: 0.3,
        metalness: 0.25,
      }),
    [],
  );
  const ringGeometry = useMemo(
    () => new THREE.RingGeometry(0.78, 0.85, 64, 1, 0, (readiness / 100) * Math.PI * 2),
    [readiness],
  );
  const ringMaterial = useMemo(
    () =>
      new THREE.MeshBasicMaterial({
        color: "#C4A6FF",
        transparent: true,
        opacity: 0.85,
        side: THREE.DoubleSide,
        depthWrite: false,
      }),
    [],
  );
  const ringTrackGeometry = useMemo(() => new THREE.RingGeometry(0.78, 0.85, 64), []);
  const ringTrackMaterial = useMemo(
    () =>
      new THREE.MeshBasicMaterial({
        color: "#2A2A38",
        transparent: true,
        opacity: 0.5,
        side: THREE.DoubleSide,
        depthWrite: false,
      }),
    [],
  );
  const ringRef = useRef<THREE.Group>(null);

  useFrame(({ camera }) => {
    ringRef.current?.quaternion.copy(camera.quaternion);
  });

  return (
    <group>
      <mesh geometry={coreGeometry} material={coreMaterial} />
      <group ref={ringRef}>
        <mesh geometry={ringTrackGeometry} material={ringTrackMaterial} />
        <mesh geometry={ringGeometry} material={ringMaterial} rotation={[0, 0, Math.PI / 2]} />
      </group>
    </group>
  );
}

function ModuleNode({ pos, label, value, geometry }: {
  pos: [number, number, number];
  label: string;
  value: number;
  geometry: THREE.IcosahedronGeometry;
}) {
  const material = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: "#9D5CFF",
        emissive: "#7C3AED",
        emissiveIntensity: 0.6 + (value / 100) * 0.9,
        roughness: 0.4,
      }),
    [value],
  );

  return (
    <Float speed={1} rotationIntensity={0.3} floatIntensity={0.4} floatingRange={[-0.06, 0.06]}>
      <group position={pos}>
        <mesh geometry={geometry} material={material} />
        <Html center distanceFactor={8.5} occlude={false} style={{ pointerEvents: "none" }}>
          <div
            style={{
              textAlign: "center",
              transform: "translateY(20px)",
              fontFamily: "var(--font-inter), system-ui, sans-serif",
            }}
          >
            <div
              style={{
                fontSize: 10.5,
                fontWeight: 500,
                color: "#F8FAFC",
                whiteSpace: "nowrap",
                textShadow: "0 0 10px rgba(124,58,237,0.9), 0 1px 2px rgba(0,0,0,0.8)",
              }}
            >
              {label}
            </div>
            <div style={{ fontSize: 9.5, color: "#C4A6FF", fontWeight: 600 }}>{Math.round(value)}</div>
          </div>
        </Html>
      </group>
    </Float>
  );
}

function FeedPulse({ target, delay, geometry }: {
  target: [number, number, number];
  delay: number;
  geometry: THREE.SphereGeometry;
}) {
  const ref = useRef<THREE.Mesh>(null);
  const duration = 3.2;
  const start = useRef(new THREE.Vector3(...target));
  const end = useRef(new THREE.Vector3(0, 0, 0));
  const material = useMemo(
    () => new THREE.MeshBasicMaterial({ color: "#C4A6FF", transparent: true, opacity: 0 }),
    [],
  );

  useFrame(({ clock }) => {
    if (!ref.current) return;
    const t = ((clock.elapsedTime + delay) % duration) / duration;
    ref.current.position.lerpVectors(start.current, end.current, t);
    const mat = ref.current.material as THREE.MeshBasicMaterial;
    mat.opacity = Math.sin(Math.min(1, t) * Math.PI) * 0.85;
  });

  return <mesh ref={ref} geometry={geometry} material={material} />;
}

function RotatingConstellation({ readiness, modules }: { readiness: number; modules: TwinModule[] }) {
  const groupRef = useRef<THREE.Group>(null);
  const moduleGeometry = useMemo(() => new THREE.IcosahedronGeometry(0.17, 0), []);
  const pulseGeometry = useMemo(() => new THREE.SphereGeometry(0.045, 8, 8), []);

  const positions = useMemo<[number, number, number][]>(() => {
    return modules.map((_, i) => {
      const angle = (i / modules.length) * Math.PI * 2;
      const z = Math.sin(angle * 1.5) * 0.6;
      return [Math.cos(angle) * MODULE_RADIUS, Math.sin(angle) * MODULE_RADIUS * 0.55, z];
    });
  }, [modules]);

  useFrame((_, delta) => {
    if (groupRef.current) groupRef.current.rotation.y += delta * 0.06;
  });

  return (
    <>
      {/* Kept outside the rotating group: its ring billboards to the
          camera every frame, which only produces a correct orientation
          when the group's own rotation isn't also being layered in. */}
      <TwinCore readiness={readiness} />
      <group ref={groupRef}>
        {modules.map((m, i) => (
          <Line key={`line-${m.label}`} points={[[0, 0, 0], positions[i]]} color="#7C3AED" transparent opacity={0.25} lineWidth={1} />
        ))}
        {modules.map((m, i) => (
          <FeedPulse key={`pulse-${m.label}`} target={positions[i]} delay={i * 0.5} geometry={pulseGeometry} />
        ))}
        {modules.map((m, i) => (
          <ModuleNode key={m.label} pos={positions[i]} label={m.label} value={m.value} geometry={moduleGeometry} />
        ))}
      </group>
    </>
  );
}

export default function DigitalTwinScene({ readiness, modules }: { readiness: number; modules: TwinModule[] }) {
  return (
    <Canvas
      dpr={[1, 1.5]}
      gl={{ antialias: true, alpha: true, powerPreference: "low-power" }}
      camera={{ position: [0, 0.6, 6.4], fov: 45 }}
      style={{ position: "absolute", inset: 0 }}
    >
      <ambientLight intensity={0.55} />
      <pointLight position={[0, 1, 3]} intensity={40} color="#9D5CFF" />
      <Suspense fallback={null}>
        <RotatingConstellation readiness={readiness} modules={modules} />
      </Suspense>
    </Canvas>
  );
}
