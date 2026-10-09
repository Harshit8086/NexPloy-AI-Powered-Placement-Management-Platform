import {
  TrendingUp,
  BarChart3,
  Lightbulb,
  ShieldCheck,
  Sparkles,
  type LucideIcon,
} from "lucide-react";

/** Proficiency band derived purely from the 0-100 level — drives a skill row's label, icon
 *  and color. Shared by My Skills and the profile so both render skills identically. */
export interface SkillBand {
  label: string;
  /** Text/foreground color class (also used for the percent). */
  text: string;
  /** Subtle chip background for the band badge. */
  chip: string;
  /** Progress-bar fill color. */
  bar: string;
  Icon: LucideIcon;
}

export function proficiencyBand(p: number): SkillBand {
  if (p >= 90) return { label: "Master", text: "text-success", chip: "bg-success/15", bar: "bg-success", Icon: ShieldCheck };
  if (p >= 75) return { label: "Expert", text: "text-success", chip: "bg-success/15", bar: "bg-success", Icon: Lightbulb };
  if (p >= 60) return { label: "Advanced", text: "text-cyan-400", chip: "bg-cyan-500/15", bar: "bg-cyan-500", Icon: BarChart3 };
  if (p >= 40) return { label: "Developing", text: "text-warning", chip: "bg-warning/15", bar: "bg-warning", Icon: TrendingUp };
  return { label: "Beginner", text: "text-muted-foreground", chip: "bg-muted", bar: "bg-muted-foreground", Icon: Sparkles };
}
