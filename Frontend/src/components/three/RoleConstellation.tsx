"use client";

import { Suspense, useMemo, useRef } from "react";
import { Canvas, useFrame, useThree } from "@react-three/fiber";
import type { ThreeEvent } from "@react-three/fiber";
import { Float, Html, Line, Sparkles } from "@react-three/drei";
import * as THREE from "three";
import { useRouter } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import type { Role } from "@/types";

/**
 * Login hub's 3D role picker — five role nodes orbiting a central NEXPLOY
 * core; click one to continue to its sign-in page. Its own self-contained
 * scene (not a reuse of the landing page's network or the Digital Twin's
 * constellation — each of those stays scoped to its own page, see their
 * file comments).
 */

export type ConstellationRole = { role: Role; label: string; icon: LucideIcon };

const NODE_RADIUS = 3.9;

function CameraRig() {
  const { camera, pointer } = useThree();
  useFrame(() => {
    camera.position.x += (pointer.x * 0.4 - camera.position.x) * 0.02;
    camera.position.y += (pointer.y * 0.22 - camera.position.y) * 0.02;
    camera.lookAt(0, 0, 0);
  });
  return null;
}

function CoreMark() {
  const geometry = useMemo(() => new THREE.SphereGeometry(0.42, 32, 32), []);
  const material = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: "#9D5CFF",
        emissive: "#7C3AED",
        emissiveIntensity: 0.85,
        roughness: 0.35,
        metalness: 0.2,
      }),
    [],
  );
  return (
    <mesh geometry={geometry} material={material}>
      <Html center distanceFactor={9} occlude={false} style={{ pointerEvents: "none" }}>
        <span
          style={{
            fontSize: 16,
            fontWeight: 700,
            color: "#F8FAFC",
            textShadow: "0 0 6px rgba(124,58,237,0.6), 0 1px 2px rgba(0,0,0,0.8)",
            fontFamily: "var(--font-inter), system-ui, sans-serif",
          }}
        >
          N
        </span>
      </Html>
    </mesh>
  );
}

function RoleNode({
  pos,
  entry,
  geometry,
}: {
  pos: [number, number, number];
  entry: ConstellationRole;
  geometry: THREE.SphereGeometry;
}) {
  const router = useRouter();
  const groupRef = useRef<THREE.Group>(null);
  const hovered = useRef(false);
  const Icon = entry.icon;
  const material = useMemo(
    () =>
      new THREE.MeshStandardMaterial({
        color: "#9D5CFF",
        emissive: "#7C3AED",
        emissiveIntensity: 0.65,
        roughness: 0.4,
        metalness: 0.1,
      }),
    [],
  );

  useFrame(() => {
    const targetGlow = hovered.current ? 1.15 : 0.65;
    material.emissiveIntensity += (targetGlow - material.emissiveIntensity) * 0.15;
    const targetScale = hovered.current ? 1.2 : 1;
    const s = groupRef.current?.scale;
    if (s) {
      s.x += (targetScale - s.x) * 0.2;
      s.y += (targetScale - s.y) * 0.2;
      s.z += (targetScale - s.z) * 0.2;
    }
  });

  const handleOver = (e: ThreeEvent<PointerEvent>) => {
    e.stopPropagation();
    hovered.current = true;
    document.body.style.cursor = "pointer";
  };
  const handleOut = (e: ThreeEvent<PointerEvent>) => {
    e.stopPropagation();
    hovered.current = false;
    document.body.style.cursor = "auto";
  };

  return (
    <Float speed={1.2} rotationIntensity={0.1} floatIntensity={0.5} floatingRange={[-0.1, 0.1]}>
      <group
        ref={groupRef}
        position={pos}
        onPointerOver={handleOver}
        onPointerOut={handleOut}
        onClick={(e) => {
          e.stopPropagation();
          router.push(`/login/${entry.role}`);
        }}
      >
        <mesh geometry={geometry} material={material} />
        <Html center distanceFactor={9} occlude={false} style={{ pointerEvents: "none" }}>
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: 4,
              transform: "translateY(28px)",
            }}
          >
            <Icon size={16} color="#F8FAFC" style={{ filter: "drop-shadow(0 0 3px rgba(124,58,237,0.55))" }} />
            <span
              style={{
                fontSize: 10.5,
                fontWeight: 600,
                color: "#F8FAFC",
                whiteSpace: "nowrap",
                textShadow: "0 0 5px rgba(124,58,237,0.5), 0 1px 2px rgba(0,0,0,0.8)",
                fontFamily: "var(--font-inter), system-ui, sans-serif",
              }}
            >
              {entry.label}
            </span>
          </div>
        </Html>
      </group>
    </Float>
  );
}

function ConstellationScene({ roles }: { roles: ConstellationRole[] }) {
  const nodeGeometry = useMemo(() => new THREE.SphereGeometry(0.22, 24, 24), []);
  const positions = useMemo<[number, number, number][]>(() => {
    const spread = Math.PI * 0.85;
    return roles.map((_, i) => {
      const t = roles.length === 1 ? 0 : i / (roles.length - 1) - 0.5;
      const angle = t * spread;
      // Alternate each node up/down so adjacent labels never share a
      // horizontal line — the labels are wider than the gap between
      // neighboring nodes would otherwise allow. Wide enough that the
      // center node (x ~ 0) also clears the core mark's own label.
      const stagger = i % 2 === 0 ? 0.85 : -0.85;
      return [
        Math.sin(angle) * NODE_RADIUS,
        Math.cos(angle) * 0.35 - 0.1 + stagger,
        Math.cos(angle) * 0.8 - 0.8,
      ];
    });
  }, [roles]);

  return (
    <>
      <ambientLight intensity={0.6} />
      <pointLight position={[0, 2, 4]} intensity={22} color="#9D5CFF" />
      <pointLight position={[-3, -1, 2]} intensity={9} color="#9D5CFF" />
      <CameraRig />
      <CoreMark />
      {roles.map((r, i) => (
        <Line
          key={`line-${r.role}`}
          points={[[0, 0, 0], positions[i]]}
          color="#7C3AED"
          transparent
          opacity={0.2}
          lineWidth={1}
        />
      ))}
      {roles.map((r, i) => (
        <RoleNode key={r.role} pos={positions[i]} entry={r} geometry={nodeGeometry} />
      ))}
      <Sparkles count={22} scale={[8, 3, 3]} size={1.2} speed={0.1} opacity={0.12} color="#9D5CFF" />
    </>
  );
}

export default function RoleConstellation({ roles }: { roles: ConstellationRole[] }) {
  return (
    <Canvas
      dpr={[1, 1.5]}
      gl={{ antialias: true, alpha: true, powerPreference: "low-power" }}
      camera={{ position: [0, 0, 8.4], fov: 40 }}
      style={{ position: "absolute", inset: 0 }}
    >
      <Suspense fallback={null}>
        <ConstellationScene roles={roles} />
      </Suspense>
    </Canvas>
  );
}
