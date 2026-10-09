"use client";

import { Suspense, useMemo, useRef } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import { Float, Html, Line } from "@react-three/drei";
import * as THREE from "three";
import type { LucideIcon } from "lucide-react";

/**
 * Per-role sign-in page centerpiece — a compact cousin of the Digital
 * Twin's orbiting-modules scene (see DigitalTwinScene.tsx): a glowing
 * icon core with this role's feature signals orbiting it, connected by
 * pulsing signal lines. Mounted only on capable, motion-enabled desktop
 * viewports via RoleSignatureCanvas; never imported directly by a page.
 */

export type RoleSignal = { icon: LucideIcon; label: string };

const SIGNAL_RADIUS = 2.05;

function IconCore({ Icon, label }: { Icon: LucideIcon; label: string }) {
  const coreGeometry = useMemo(() => new THREE.SphereGeometry(0.5, 32, 32), []);
  const coreMaterial = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: "#9D5CFF",
        emissive: "#7C3AED",
        emissiveIntensity: 0.8,
        roughness: 0.35,
        metalness: 0.2,
      }),
    [],
  );
  const ringGeometry = useMemo(() => new THREE.RingGeometry(0.7, 0.76, 64), []);
  const ringMaterial = useMemo(
    () =>
      new THREE.MeshBasicMaterial({
        color: "#C4A6FF",
        transparent: true,
        opacity: 0.3,
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
        <mesh geometry={ringGeometry} material={ringMaterial} />
      </group>
      <Html center distanceFactor={7.5} occlude={false} style={{ pointerEvents: "none" }}>
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 4 }}>
          <Icon size={22} color="#F8FAFC" style={{ filter: "drop-shadow(0 0 4px rgba(124,58,237,0.6))" }} />
          <span
            style={{
              fontSize: 10,
              fontWeight: 600,
              letterSpacing: 0.3,
              color: "#F8FAFC",
              textShadow: "0 0 5px rgba(124,58,237,0.55), 0 1px 2px rgba(0,0,0,0.8)",
              fontFamily: "var(--font-inter), system-ui, sans-serif",
            }}
          >
            {label}
          </span>
        </div>
      </Html>
    </group>
  );
}

function SignalNode({
  pos,
  label,
  geometry,
}: {
  pos: [number, number, number];
  label: string;
  geometry: THREE.SphereGeometry;
}) {
  const material = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: "#9D5CFF",
        emissive: "#7C3AED",
        emissiveIntensity: 0.55,
        roughness: 0.45,
      }),
    [],
  );
  return (
    <Float speed={1.1} rotationIntensity={0.2} floatIntensity={0.5} floatingRange={[-0.06, 0.06]}>
      <group position={pos}>
        <mesh geometry={geometry} material={material} />
        <Html center distanceFactor={7.5} occlude={false} style={{ pointerEvents: "none" }}>
          <div
            style={{
              fontSize: 9.5,
              fontWeight: 500,
              color: "#E2D9FF",
              whiteSpace: "nowrap",
              textShadow: "0 0 4px rgba(124,58,237,0.5), 0 1px 2px rgba(0,0,0,0.8)",
              transform: "translateY(16px)",
              fontFamily: "var(--font-inter), system-ui, sans-serif",
            }}
          >
            {label}
          </div>
        </Html>
      </group>
    </Float>
  );
}

function FeedPulse({
  target,
  delay,
  geometry,
}: {
  target: [number, number, number];
  delay: number;
  geometry: THREE.SphereGeometry;
}) {
  const ref = useRef<THREE.Mesh>(null);
  const duration = 3;
  const start = useRef(new THREE.Vector3(...target));
  const end = useRef(new THREE.Vector3(0, 0, 0));
  // Own material per pulse — sharing one across instances would make every
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
    mat.opacity = Math.sin(Math.min(1, t) * Math.PI) * 0.55;
  });

  return <mesh ref={ref} geometry={geometry} material={material} />;
}

function RotatingSignals({ Icon, label, signals }: { Icon: LucideIcon; label: string; signals: RoleSignal[] }) {
  const groupRef = useRef<THREE.Group>(null);
  const nodeGeometry = useMemo(() => new THREE.SphereGeometry(0.135, 20, 20), []);
  const pulseGeometry = useMemo(() => new THREE.SphereGeometry(0.04, 8, 8), []);

  const positions = useMemo<[number, number, number][]>(() => {
    // Offset by half a step so no node lands directly above/below the
    // core, where its label would collide with the core's own icon+name.
    const halfStep = Math.PI / signals.length;
    return signals.map((_, i) => {
      const angle = (i / signals.length) * Math.PI * 2 - Math.PI / 2 + halfStep;
      const z = Math.sin(angle * 1.4) * 0.5;
      return [Math.cos(angle) * SIGNAL_RADIUS, Math.sin(angle) * SIGNAL_RADIUS * 0.62, z];
    });
  }, [signals]);

  useFrame((_, delta) => {
    if (groupRef.current) groupRef.current.rotation.y += delta * 0.08;
  });

  return (
    <>
      {/* Kept outside the rotating group — see DigitalTwinScene.tsx for why
          a camera-billboarded ring can't live inside a rotating parent. */}
      <IconCore Icon={Icon} label={label} />
      <group ref={groupRef}>
        {signals.map((s, i) => (
          <Line
            key={`line-${s.label}`}
            points={[[0, 0, 0], positions[i]]}
            color="#7C3AED"
            transparent
            opacity={0.22}
            lineWidth={1}
          />
        ))}
        {signals.map((s, i) => (
          <FeedPulse key={`pulse-${s.label}`} target={positions[i]} delay={i * 0.6} geometry={pulseGeometry} />
        ))}
        {signals.map((s, i) => (
          <SignalNode key={s.label} pos={positions[i]} label={s.label} geometry={nodeGeometry} />
        ))}
      </group>
    </>
  );
}

export default function RoleSignatureScene({
  Icon,
  label,
  signals,
}: {
  Icon: LucideIcon;
  label: string;
  signals: RoleSignal[];
}) {
  return (
    <Canvas
      dpr={[1, 1.5]}
      gl={{ antialias: true, alpha: true, powerPreference: "low-power" }}
      camera={{ position: [0, 0.3, 5.6], fov: 42 }}
      style={{ position: "absolute", inset: 0 }}
    >
      <ambientLight intensity={0.6} />
      <pointLight position={[0, 1, 3]} intensity={18} color="#9D5CFF" />
      <Suspense fallback={null}>
        <RotatingSignals Icon={Icon} label={label} signals={signals} />
      </Suspense>
    </Canvas>
  );
}
