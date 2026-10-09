"use client";

import { motion } from "framer-motion";
import { staggerContainer, staggerItem, fadeUp } from "@/lib/motion-variants";
import { useReducedMotion } from "@/hooks/useReducedMotion";

/**
 * Shared scroll/mount reveal wrapper for chart and section content —
 * the "data comes online" moment. Use instead of one-off whileInView
 * blocks per page.
 *
 * `stagger` reveals direct children one after another (wrap each child in
 * <DataRevealItem> or rely on staggerItem-compatible children); without
 * it the whole block fades/settles in as one unit.
 *
 * DataRevealItem is exported separately (not as DataReveal.Item) because a
 * property attached to a function component isn't a real module export —
 * Next's RSC client-reference manifest can't resolve `<DataReveal.Item>`
 * when this is imported from a Server Component page, and the build fails.
 */
export function DataReveal({
  children,
  stagger = false,
  once = true,
  className,
}: {
  children: React.ReactNode;
  stagger?: boolean;
  once?: boolean;
  className?: string;
}) {
  const reduced = useReducedMotion();

  if (reduced) {
    return <div className={className}>{children}</div>;
  }

  return (
    <motion.div
      className={className}
      initial="hidden"
      whileInView="visible"
      viewport={{ once, margin: "-40px" }}
      variants={stagger ? staggerContainer : fadeUp}
    >
      {children}
    </motion.div>
  );
}

export function DataRevealItem({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  const reduced = useReducedMotion();
  if (reduced) return <div className={className}>{children}</div>;
  return (
    <motion.div className={className} variants={staggerItem}>
      {children}
    </motion.div>
  );
}
